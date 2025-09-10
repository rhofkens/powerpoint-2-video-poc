package ai.bluefields.ppt2video.service.video;

import ai.bluefields.ppt2video.dto.video.OutputSettings;
import ai.bluefields.ppt2video.dto.video.RenderResponse;
import ai.bluefields.ppt2video.dto.video.RenderStatus;
import ai.bluefields.ppt2video.dto.video.VideoComposition;
import ai.bluefields.ppt2video.dto.video.VideoStoryRequest;
import ai.bluefields.ppt2video.dto.video.VideoStoryResponse;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.entity.RenderJobStatus;
import ai.bluefields.ppt2video.entity.RenderJobType;
import ai.bluefields.ppt2video.entity.VideoProviderType;
import ai.bluefields.ppt2video.entity.VideoStoryStatus;
import ai.bluefields.ppt2video.repository.*;
import ai.bluefields.ppt2video.service.R2AssetService;
import ai.bluefields.ppt2video.service.video.provider.VideoProvider;
import ai.bluefields.ppt2video.service.video.provider.VideoProviderFactory;
import ai.bluefields.ppt2video.service.video.provider.shotstack.ShotstackCompositionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that orchestrates the creation of video stories from presentations. Coordinates between
 * composition service, video provider, and database persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoStoryOrchestrationService {

  private final VideoProviderFactory videoProviderFactory;
  private final ShotstackCompositionService compositionService;
  private final VideoStoryRepository videoStoryRepository;
  private final RenderJobRepository renderJobRepository;
  private final PresentationRepository presentationRepository;
  private final IntroVideoRepository introVideoRepository;
  private final VideoDownloadService videoDownloadService;
  private final R2AssetService r2AssetService;
  private final PresignedUrlValidator urlValidator;
  private final ObjectMapper objectMapper;
  private final PreCompositionAssetPublisher assetPublisher;

  @Value("${shotstack.assets.mode:r2-direct}")
  private String assetMode;

  /**
   * Creates a new video story composition without rendering. This generates the JSON composition
   * and saves it for preview.
   */
  @Transactional
  public VideoStoryResponse createVideoStory(VideoStoryRequest request) {
    return createVideoStory(request, false);
  }

  /**
   * Creates a new video story composition without rendering. This generates the JSON composition
   * and saves it for preview. When force is true, it will regenerate the composition even if one
   * already exists.
   *
   * @param request The video story creation request
   * @param force Whether to force regeneration of the composition
   * @return The created video story with composition data
   */
  @Transactional
  public VideoStoryResponse createVideoStory(VideoStoryRequest request, boolean force) {
    log.info(
        "Creating video story composition for presentation: {} (force: {}, asset mode: {})",
        request.getPresentationId(),
        force,
        assetMode);

    // Load entities
    Presentation presentation =
        presentationRepository
            .findById(request.getPresentationId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Presentation not found: " + request.getPresentationId()));

    IntroVideo introVideo =
        introVideoRepository
            .findById(request.getIntroVideoId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Intro video not found: " + request.getIntroVideoId()));

    // Step 1: Ensure all assets are ready for composition
    // This handles both R2 URL refresh and Shotstack upload (if needed) before composition
    log.info("Preparing assets for video story composition");
    assetPublisher.ensureAllAssetsPublished(request.getPresentationId());

    // Create video story entity
    VideoStory videoStory = createVideoStoryEntity(presentation, request);

    try {
      // Step 2: Build composition using Shotstack format with clean URLs
      JsonNode composition = compositionService.buildFullTimeline(presentation, introVideo);

      // Store composition as script data
      videoStory.setScriptData(composition.toString());
      videoStory.setStatus(VideoStoryStatus.READY);
      videoStory = videoStoryRepository.save(videoStory);

      // Build response with composition data
      return buildResponseWithComposition(videoStory, null);

    } catch (Exception e) {
      log.error("Failed to create video story composition", e);
      videoStory.setStatus(VideoStoryStatus.FAILED);
      videoStory.setDescription("Error: " + e.getMessage());
      videoStoryRepository.save(videoStory);
      throw new RuntimeException("Failed to create video story composition: " + e.getMessage(), e);
    }
  }

  /**
   * Renders an existing video story. Takes a previously created video story and submits it for
   * rendering.
   */
  @Transactional
  public VideoStoryResponse renderVideoStory(UUID videoStoryId) {
    log.info("Rendering video story: {}", videoStoryId);

    VideoStory videoStory =
        videoStoryRepository
            .findById(videoStoryId)
            .orElseThrow(
                () -> new IllegalArgumentException("Video story not found: " + videoStoryId));

    // Check if already rendering or completed
    if (videoStory.getStatus() == VideoStoryStatus.RENDERING
        || videoStory.getStatus() == VideoStoryStatus.COMPLETED) {
      log.warn("Video story {} is already rendering or completed", videoStoryId);
      RenderJob existingJob =
          renderJobRepository.findTopByVideoStoryOrderByCreatedAtDesc(videoStory).orElse(null);
      return buildResponse(videoStory, existingJob);
    }

    try {
      // Parse the stored composition - it already contains timeline and output
      JsonNode composition = objectMapper.readTree(videoStory.getScriptData());

      // Get provider and submit render
      VideoProviderType providerType = VideoProviderType.SHOTSTACK; // Default for now
      VideoProvider provider = videoProviderFactory.getProvider(providerType);

      // Create VideoComposition with both timeline and output from the stored composition
      VideoComposition videoComposition = new VideoComposition();

      // The composition already has both timeline and output, extract them
      if (composition.has("timeline")) {
        videoComposition.setTimeline(composition.get("timeline"));
      } else {
        // If for some reason we only have timeline data, use it directly
        videoComposition.setTimeline(composition);
      }

      // Set output settings if present in composition
      if (composition.has("output")) {
        JsonNode outputNode = composition.get("output");
        OutputSettings outputSettings = new OutputSettings();
        outputSettings.setFormat(
            outputNode.has("format") ? outputNode.get("format").asText() : "mp4");
        outputSettings.setResolution(
            outputNode.has("resolution") ? outputNode.get("resolution").asText() : "hd");
        outputSettings.setFps(outputNode.has("fps") ? outputNode.get("fps").asInt() : 25);
        outputSettings.setQuality(
            outputNode.has("quality") ? outputNode.get("quality").asText() : "medium");
        videoComposition.setOutput(outputSettings);
      } else {
        // Default output settings if not present
        OutputSettings outputSettings = new OutputSettings();
        outputSettings.setFormat("mp4");
        outputSettings.setResolution("hd");
        outputSettings.setFps(25);
        outputSettings.setQuality("medium");
        videoComposition.setOutput(outputSettings);
      }

      RenderResponse renderResponse = provider.submitRender(videoComposition);

      // Create render job
      RenderJob renderJob = createRenderJob(videoStory, renderResponse);
      renderJob = renderJobRepository.save(renderJob);

      // Update video story status
      videoStory.setStatus(VideoStoryStatus.RENDERING);
      videoStory = videoStoryRepository.save(videoStory);

      // Build response
      return buildResponse(videoStory, renderJob);

    } catch (Exception e) {
      log.error("Failed to render video story", e);
      videoStory.setStatus(VideoStoryStatus.FAILED);
      videoStory.setDescription("Render error: " + e.getMessage());
      videoStoryRepository.save(videoStory);
      throw new RuntimeException("Failed to render video story: " + e.getMessage(), e);
    }
  }

  /** Gets a video story with its composition. */
  @Transactional(readOnly = true)
  public VideoStoryResponse getVideoStory(UUID videoStoryId) {
    VideoStory videoStory =
        videoStoryRepository
            .findById(videoStoryId)
            .orElseThrow(
                () -> new IllegalArgumentException("Video story not found: " + videoStoryId));

    // Find the most recent render job if any
    RenderJob renderJob =
        renderJobRepository.findTopByVideoStoryOrderByCreatedAtDesc(videoStory).orElse(null);

    return buildResponseWithComposition(videoStory, renderJob);
  }

  /** Checks the status of a video story render job. */
  @Transactional
  public VideoStoryResponse checkRenderStatus(UUID videoStoryId) {
    VideoStory videoStory =
        videoStoryRepository
            .findById(videoStoryId)
            .orElseThrow(
                () -> new IllegalArgumentException("Video story not found: " + videoStoryId));

    // Find the most recent render job for this video story
    RenderJob renderJob =
        renderJobRepository
            .findTopByVideoStoryOrderByCreatedAtDesc(videoStory)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No render job found for video story: " + videoStoryId));

    // If already completed or failed, return current status
    if (renderJob.getStatus() == RenderJobStatus.COMPLETED
        || renderJob.getStatus() == RenderJobStatus.FAILED) {
      return buildResponse(videoStory, renderJob);
    }

    // Check status with provider
    VideoProvider provider = videoProviderFactory.getProvider(renderJob.getProvider());
    RenderStatus renderStatus = provider.checkRenderStatus(renderJob.getProviderJobId());

    // Update render job
    updateRenderJobFromStatus(renderJob, renderStatus);
    renderJobRepository.save(renderJob);

    // Update video story if needed
    if (renderStatus.getStatus() == RenderJobStatus.COMPLETED) {
      videoStory.setStatus(VideoStoryStatus.COMPLETED);

      // Download and store video in R2
      try {
        String r2Url = videoDownloadService.processCompletedRender(renderJob);
        log.info("Video downloaded and stored in R2: {}", r2Url);
      } catch (Exception e) {
        log.error("Failed to download and store video, but render is complete", e);
        // Continue even if download fails - the video URL is still available in renderJob
      }

      videoStoryRepository.save(videoStory);
    } else if (renderStatus.getStatus() == RenderJobStatus.FAILED) {
      videoStory.setStatus(VideoStoryStatus.FAILED);
      videoStory.setDescription("Error: " + renderStatus.getErrorMessage());
      videoStoryRepository.save(videoStory);
    }

    return buildResponse(videoStory, renderJob);
  }

  private VideoStory createVideoStoryEntity(Presentation presentation, VideoStoryRequest request) {
    VideoStory videoStory = new VideoStory();
    videoStory.setPresentation(presentation);
    videoStory.setName(
        request.getTitle() != null ? request.getTitle() : presentation.getTitle() + " Video");
    videoStory.setDescription(request.getDescription());
    videoStory.setStatus(VideoStoryStatus.DRAFT);
    videoStory.setScriptData("{}");
    videoStory.setCreatedAt(LocalDateTime.now());
    videoStory.setUpdatedAt(LocalDateTime.now());
    return videoStory;
  }

  private RenderJob createRenderJob(VideoStory videoStory, RenderResponse renderResponse) {
    RenderJob renderJob = new RenderJob();
    renderJob.setVideoStory(videoStory);
    renderJob.setPresentation(videoStory.getPresentation());
    renderJob.setProviderJobId(renderResponse.getRenderJobId());
    renderJob.setProvider(VideoProviderType.SHOTSTACK);
    renderJob.setType(RenderJobType.INTRO_ONLY);
    renderJob.setStatus(RenderJobStatus.QUEUED);
    renderJob.setCreatedAt(LocalDateTime.now());
    renderJob.setUpdatedAt(LocalDateTime.now());
    return renderJob;
  }

  private void updateRenderJobFromStatus(RenderJob renderJob, RenderStatus renderStatus) {
    renderJob.setStatus(renderStatus.getStatus());
    renderJob.setProgress(renderStatus.getProgress() != null ? renderStatus.getProgress() : 0);
    renderJob.setOutputUrl(renderStatus.getOutputUrl());
    renderJob.setErrorMessage(renderStatus.getErrorMessage());

    if (renderStatus.getStartedAt() != null) {
      renderJob.setStartedAt(renderStatus.getStartedAt());
    }
    if (renderStatus.getCompletedAt() != null) {
      renderJob.setCompletedAt(renderStatus.getCompletedAt());
    }

    renderJob.setUpdatedAt(LocalDateTime.now());
  }

  private VideoStoryResponse buildResponse(VideoStory videoStory, RenderJob renderJob) {
    VideoStoryResponse response = new VideoStoryResponse();
    response.setId(videoStory.getId());
    response.setTitle(videoStory.getName());
    response.setStatus(videoStory.getStatus().toString());

    if (renderJob != null) {
      response.setRenderJobId(renderJob.getId());
      response.setRenderStatus(renderJob.getStatus().toString());
      response.setProgress(
          renderJob.getProgress() != null ? renderJob.getProgress().doubleValue() : 0.0);

      // Prefer R2 URL if available, otherwise use provider URL
      if (videoStory.getR2VideoUrl() != null) {
        response.setVideoUrl(videoStory.getR2VideoUrl());
        response.setVideoStored(true);
      } else if (renderJob.getOutputUrl() != null) {
        response.setVideoUrl(renderJob.getOutputUrl());
        response.setVideoStored(false);
      }

      response.setErrorMessage(renderJob.getErrorMessage());
    }

    response.setCreatedAt(videoStory.getCreatedAt());
    response.setUpdatedAt(videoStory.getUpdatedAt());
    return response;
  }

  private VideoStoryResponse buildResponseWithComposition(
      VideoStory videoStory, RenderJob renderJob) {
    VideoStoryResponse response = buildResponse(videoStory, renderJob);

    // Include composition data for preview
    try {
      if (videoStory.getScriptData() != null && !videoStory.getScriptData().equals("{}")) {
        response.setCompositionData(videoStory.getScriptData());
      }
    } catch (Exception e) {
      log.error("Failed to include composition data", e);
    }

    return response;
  }

  /** Gets all video stories for a presentation. */
  @Transactional(readOnly = true)
  public List<VideoStoryResponse> getVideoStoriesForPresentation(UUID presentationId) {
    List<VideoStory> stories =
        videoStoryRepository.findByPresentationIdOrderByCreatedAtDesc(presentationId);

    return stories.stream()
        .map(
            story -> {
              // Find the latest render job for this story
              RenderJob renderJob =
                  renderJobRepository.findByVideoStoryIdOrderByCreatedAtDesc(story.getId()).stream()
                      .findFirst()
                      .orElse(null);

              // Use buildResponseWithComposition to include the composition data
              return buildResponseWithComposition(story, renderJob);
            })
        .collect(Collectors.toList());
  }

  /**
   * Validates that all asset URLs are valid and will remain valid during rendering. If URLs are
   * expired or expiring soon, attempts to regenerate them.
   */
  private void validateAssetUrls(Presentation presentation, IntroVideo introVideo) {
    log.debug("Validating asset URLs for presentation: {}", presentation.getId());

    // Validate intro video URL
    if (introVideo != null && introVideo.getPublishedUrl() != null) {
      PresignedUrlValidator.UrlValidationResult result =
          urlValidator.validateUrl(introVideo.getPublishedUrl());

      if (!result.isValid()) {
        log.warn(
            "Intro video URL validation failed: {}. Attempting to regenerate.",
            result.getErrorMessage());

        // In a full implementation, we would regenerate the URL here
        // For now, we'll log a warning and continue
        log.warn("URL regeneration not yet implemented for intro videos");
      }
    }

    // For Phase 3: Validate slide asset URLs
    // Map<String, String> slideUrls = r2AssetService.getSlideAssetUrls(presentation.getId());
    // Map<String, PresignedUrlValidator.UrlValidationResult> validationResults =
    //     urlValidator.validateUrls(slideUrls);
    //
    // for (Map.Entry<String, PresignedUrlValidator.UrlValidationResult> entry :
    //      validationResults.entrySet()) {
    //   if (!entry.getValue().isValid()) {
    //     log.warn("Asset URL validation failed for {}: {}",
    //         entry.getKey(), entry.getValue().getErrorMessage());
    //   }
    // }
  }

  /** Gets the current asset mode configuration. */
  public String getAssetMode() {
    return assetMode;
  }
}
