package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.exception.ResourceNotFoundException;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.AssetMetadataService;
import ai.bluefields.ppt2video.service.PresignedUrlService;
import ai.bluefields.ppt2video.service.R2AssetService;
import ai.bluefields.ppt2video.service.avatar.providers.HeyGenConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing avatar video generation. Orchestrates the process of creating avatar videos
 * using various providers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarVideoService {

  private final AvatarVideoRepository avatarVideoRepository;
  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final R2AssetService r2AssetService;
  private final AvatarProviderFactory avatarProviderFactory;
  private final AvatarVideoMonitorService avatarVideoMonitorService;
  private final HeyGenConfiguration heyGenConfiguration;
  private final ObjectMapper objectMapper;
  private final AssetMetadataService assetMetadataService;
  private final PresignedUrlService presignedUrlService;

  /**
   * Generate an avatar video for a slide.
   *
   * @param request the avatar video request
   * @return the avatar video response
   */
  @Transactional
  public AvatarVideoResponse generateAvatarVideo(AvatarVideoRequest request) {
    log.info(
        "Generating avatar video for slide: {} in presentation: {}",
        request.getSlideId(),
        request.getPresentationId());

    // Validate presentation and slide exist
    Presentation presentation =
        presentationRepository
            .findById(request.getPresentationId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Presentation not found: " + request.getPresentationId()));

    Slide slide =
        slideRepository
            .findById(request.getSlideId())
            .orElseThrow(
                () -> new ResourceNotFoundException("Slide not found: " + request.getSlideId()));

    // Check if slide belongs to presentation
    if (!slide.getPresentation().getId().equals(presentation.getId())) {
      throw new ProcessingException("Slide does not belong to the specified presentation");
    }

    // Get audio URL
    String audioUrl = getAudioUrl(request, slide);
    if (audioUrl == null || audioUrl.isEmpty()) {
      throw new ProcessingException(
          "No audio available for slide. Please generate or upload audio first.");
    }

    // Determine avatar ID based on narrative style
    String avatarId = determineAvatarIdFromNarrativeStyle(slide, request.getAvatarId());
    log.info("Using avatar ID: {} for slide: {}", avatarId, slide.getId());

    // Select provider
    AvatarProviderType providerType =
        request.getProviderType() != null ? request.getProviderType() : AvatarProviderType.HEYGEN;

    AvatarProvider provider = avatarProviderFactory.getProvider(providerType);

    // Create entity to track the job
    AvatarVideo avatarVideo =
        AvatarVideo.builder()
            .presentation(presentation)
            .slide(slide)
            .providerType(providerType)
            .status(AvatarGenerationStatusType.PENDING)
            .avatarId(avatarId)
            .backgroundColor(request.getBackgroundColor())
            .audioUrl(audioUrl)
            .createdBy("system")
            .build();

    avatarVideo = avatarVideoRepository.save(avatarVideo);

    try {
      // Update request with the determined avatar ID
      request.setAvatarId(avatarId);

      // Call provider to create video
      AvatarVideoResponse providerResponse = provider.createAvatarVideo(request, audioUrl);

      // Update entity with provider response
      avatarVideo.setProviderVideoId(providerResponse.getProviderVideoId());
      avatarVideo.setStatus(providerResponse.getStatus());
      avatarVideo.setStartedAt(LocalDateTime.now());

      // Store request payload for debugging
      if (providerResponse.getMetadata() != null) {
        avatarVideo.setRequestPayload(providerResponse.getMetadata());
      }

      avatarVideo = avatarVideoRepository.save(avatarVideo);

      // Get the final saved ID before starting async monitoring
      final UUID savedVideoId = avatarVideo.getId();
      final String savedProviderVideoId = avatarVideo.getProviderVideoId();

      // Build response before starting async (to ensure transaction commits)
      AvatarVideoResponse response = convertToResponse(avatarVideo);

      // Start background monitoring (will run after transaction commits)
      avatarVideoMonitorService.monitorVideoStatus(savedVideoId, savedProviderVideoId);

      return response;

    } catch (Exception e) {
      log.error("Failed to generate avatar video", e);

      // Update entity with error
      avatarVideo.setStatus(AvatarGenerationStatusType.FAILED);
      avatarVideo.setErrorMessage(e.getMessage());
      avatarVideoRepository.save(avatarVideo);

      throw new ProcessingException("Failed to generate avatar video: " + e.getMessage());
    }
  }

  /**
   * Get all avatar videos for a slide.
   *
   * @param slideId the slide ID
   * @return list of avatar video responses
   */
  @Transactional(readOnly = true)
  public List<AvatarVideoResponse> getSlideAvatarVideos(UUID slideId) {
    // Get all videos for the slide
    List<AvatarVideo> videos = avatarVideoRepository.findBySlideId(slideId);

    // Sort by creation date descending to ensure most recent is first
    videos.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

    // Only return completed videos that have valid URLs
    // Filter out videos without proper URLs to prevent frontend confusion
    return videos.stream()
        .filter(v -> v.getStatus() == AvatarGenerationStatusType.COMPLETED)
        .filter(v -> v.getR2Asset() != null || v.getVideoUrl() != null)
        .limit(1) // Return only the most recent video to avoid confusion
        .map(this::convertToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Publish a completed avatar video to R2 storage.
   *
   * @param id the avatar video ID
   * @return the published asset information
   */
  @Transactional
  public AssetDto publishAvatarVideo(UUID id) {
    log.info("Publishing avatar video to R2: {}", id);

    AvatarVideo avatarVideo =
        avatarVideoRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Avatar video not found: " + id));

    // Check if video is completed
    if (avatarVideo.getStatus() != AvatarGenerationStatusType.COMPLETED) {
      throw new ProcessingException(
          "Cannot publish avatar video that is not completed. Current status: "
              + avatarVideo.getStatus());
    }

    // Check if video URL exists
    if (avatarVideo.getVideoUrl() == null || avatarVideo.getVideoUrl().isEmpty()) {
      throw new ProcessingException("Avatar video has no video URL to publish");
    }

    // Check if already published
    if (avatarVideo.getR2Asset() != null) {
      log.info("Avatar video {} is already published", id);
      throw new ProcessingException("Avatar video is already published");
    }

    try {
      // Download video from HeyGen
      log.info("Downloading avatar video from HeyGen URL: {}", avatarVideo.getVideoUrl());

      java.net.http.HttpClient client =
          java.net.http.HttpClient.newBuilder()
              .connectTimeout(java.time.Duration.ofSeconds(30))
              .build();

      java.net.http.HttpRequest request =
          java.net.http.HttpRequest.newBuilder()
              .uri(java.net.URI.create(avatarVideo.getVideoUrl()))
              .timeout(java.time.Duration.ofMinutes(5))
              .GET()
              .build();

      java.net.http.HttpResponse<byte[]> response =
          client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

      if (response.statusCode() != 200) {
        throw new ProcessingException(
            "Failed to download video from HeyGen, HTTP status: " + response.statusCode());
      }

      byte[] videoData = response.body();
      log.info("Downloaded {} bytes of video data", videoData.length);

      // Save to temporary file
      String fileName =
          String.format(
              "avatar_video_%s_%s.mp4", avatarVideo.getSlide().getId(), System.currentTimeMillis());
      java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("avatar_", ".mp4");
      java.nio.file.Files.write(tempFile, videoData);

      try {
        // Publish to R2 using existing infrastructure
        log.info("Publishing avatar video to R2 for slide: {}", avatarVideo.getSlide().getId());

        // Save the file to the expected location for publishExistingAsset
        java.nio.file.Path storageDir =
            java.nio.file.Paths.get(
                "./storage/presentations",
                avatarVideo.getPresentation().getId().toString(),
                "slides",
                avatarVideo.getSlide().getId().toString(),
                "avatar_videos");
        java.nio.file.Files.createDirectories(storageDir);

        java.nio.file.Path targetFile = storageDir.resolve(fileName);
        java.nio.file.Files.move(
            tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Use the existing publish method
        AssetDto publishedAsset =
            r2AssetService.publishExistingAsset(
                avatarVideo.getPresentation().getId(),
                avatarVideo.getSlide().getId(),
                AssetType.SLIDE_AVATAR_VIDEO);

        // Get the AssetMetadata entity instead of DTO
        AssetMetadata assetMetadata =
            assetMetadataService
                .getAsset(publishedAsset.getId())
                .orElseThrow(() -> new ProcessingException("Failed to retrieve published asset"));

        // Update avatar video with R2 asset reference
        avatarVideo.setR2Asset(assetMetadata);
        avatarVideo.setPublishedAt(LocalDateTime.now());
        avatarVideoRepository.save(avatarVideo);

        log.info("Successfully published avatar video {} to R2", id);
        return publishedAsset;

      } finally {
        // Clean up temp file if it still exists
        java.nio.file.Files.deleteIfExists(tempFile);
      }

    } catch (Exception e) {
      log.error("Failed to publish avatar video: {}", id, e);
      throw new ProcessingException("Failed to publish avatar video: " + e.getMessage());
    }
  }

  /**
   * Cancel an avatar video generation job.
   *
   * @param id the avatar video ID
   * @return true if cancellation was successful
   */
  public boolean cancelAvatarVideo(UUID id) {
    AvatarVideo avatarVideo =
        avatarVideoRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Avatar video not found: " + id));

    if (avatarVideo.getStatus() == AvatarGenerationStatusType.COMPLETED
        || avatarVideo.getStatus() == AvatarGenerationStatusType.FAILED
        || avatarVideo.getStatus() == AvatarGenerationStatusType.CANCELLED) {
      log.info("Avatar video {} is already in terminal state: {}", id, avatarVideo.getStatus());
      return false;
    }

    try {
      AvatarProvider provider = avatarProviderFactory.getProvider(avatarVideo.getProviderType());
      boolean cancelled = provider.cancelVideo(avatarVideo.getProviderVideoId());

      if (cancelled) {
        avatarVideo.setStatus(AvatarGenerationStatusType.CANCELLED);
        avatarVideo.setCompletedAt(LocalDateTime.now());
        avatarVideoRepository.save(avatarVideo);
        log.info("Successfully cancelled avatar video: {}", id);
      }

      return cancelled;
    } catch (Exception e) {
      log.error("Failed to cancel avatar video: {}", id, e);
      return false;
    }
  }

  /**
   * Get status of an avatar video by ID.
   *
   * @param id the avatar video ID
   * @return the video status
   */
  public AvatarVideoStatusDto getVideoStatus(UUID id) {
    AvatarVideo avatarVideo =
        avatarVideoRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Avatar video not found: " + id));

    // If still processing, update from provider
    if (avatarVideo.getStatus() == AvatarGenerationStatusType.PENDING
        || avatarVideo.getStatus() == AvatarGenerationStatusType.PROCESSING) {
      updateVideoStatus(avatarVideo);
    }

    // Refresh published URL if needed
    String refreshedUrl = refreshPublishedUrlIfNeeded(avatarVideo);

    return AvatarVideoStatusDto.builder()
        .id(avatarVideo.getId())
        .status(avatarVideo.getStatus())
        .errorMessage(avatarVideo.getErrorMessage())
        .progressPercentage(avatarVideo.getProgressPercentage())
        .videoUrl(avatarVideo.getVideoUrl())
        .publishedUrl(refreshedUrl) // Use refreshed R2 URL
        .durationSeconds(avatarVideo.getDurationSeconds())
        .startedAt(avatarVideo.getStartedAt())
        .completedAt(avatarVideo.getCompletedAt())
        .build();
  }

  /**
   * Update video status from provider.
   *
   * @param avatarVideo the avatar video entity
   */
  private void updateVideoStatus(AvatarVideo avatarVideo) {
    try {
      AvatarProvider provider = avatarProviderFactory.getProvider(avatarVideo.getProviderType());
      AvatarVideoStatusDto status = provider.getVideoStatus(avatarVideo.getProviderVideoId());

      avatarVideo.setStatus(status.getStatus());
      avatarVideo.setProgressPercentage(status.getProgressPercentage());
      avatarVideo.setVideoUrl(status.getVideoUrl());
      avatarVideo.setDurationSeconds(status.getDurationSeconds());

      if (status.getErrorMessage() != null) {
        avatarVideo.setErrorMessage(status.getErrorMessage());
      }

      if (status.getCompletedAt() != null) {
        avatarVideo.setCompletedAt(status.getCompletedAt());
      }

      // If completed, try to get the video URL
      if (status.getStatus() == AvatarGenerationStatusType.COMPLETED
          && avatarVideo.getVideoUrl() == null) {
        String videoUrl = provider.getVideoDownloadUrl(avatarVideo.getProviderVideoId());
        if (videoUrl != null) {
          avatarVideo.setVideoUrl(videoUrl);
        }
      }

      avatarVideoRepository.save(avatarVideo);
    } catch (Exception e) {
      log.error("Failed to update video status for: {}", avatarVideo.getId(), e);
    }
  }

  /**
   * Get audio URL for the slide.
   *
   * @param request the avatar video request
   * @param slide the slide entity
   * @return the audio URL
   */
  private String getAudioUrl(AvatarVideoRequest request, Slide slide) {
    // If custom audio URL is provided, use it
    if (!Boolean.TRUE.equals(request.getUsePublishedAudio())
        && request.getCustomAudioUrl() != null) {
      return request.getCustomAudioUrl();
    }

    // Try to get published audio from R2
    try {
      // Always force republish to ensure we're using the latest audio file
      // This ensures that when audio is regenerated with a different voice,
      // the new audio is uploaded to R2 and used for avatar generation
      log.info(
          "Publishing audio to R2 with force republish to ensure latest version for slide: {}",
          slide.getId());
      AssetDto publishedAudio =
          r2AssetService.publishExistingAsset(
              slide.getPresentation().getId(), slide.getId(), AssetType.SLIDE_AUDIO, true);

      if (publishedAudio != null && publishedAudio.getDownloadUrl() != null) {
        log.info("Successfully published audio to R2 for slide: {}", slide.getId());
        return publishedAudio.getDownloadUrl();
      }

    } catch (Exception e) {
      log.error("Failed to get audio from R2 for slide: {}", slide.getId(), e);
    }

    return null;
  }

  /**
   * Determine the avatar ID based on the narrative style. If a custom avatar ID is provided in the
   * request, it takes precedence. Otherwise, selects avatar based on the narrative style from the
   * active narrative.
   *
   * @param slide the slide to get narrative from
   * @param requestedAvatarId custom avatar ID from request (optional)
   * @return the avatar ID to use
   */
  private String determineAvatarIdFromNarrativeStyle(Slide slide, String requestedAvatarId) {
    // If custom avatar ID is provided, use it
    if (requestedAvatarId != null && !requestedAvatarId.isEmpty()) {
      log.debug("Using custom avatar ID from request: {}", requestedAvatarId);
      return requestedAvatarId;
    }

    // Try to get the active narrative for the slide
    try {
      SlideNarrative activeNarrative =
          slideNarrativeRepository.findActiveNarrativeBySlideId(slide.getId()).orElse(null);

      if (activeNarrative != null && activeNarrative.getGenerationMetadata() != null) {
        // Extract style from narrative metadata
        String style = extractStyleFromMetadata(activeNarrative.getGenerationMetadata());

        // Get avatar ID for the detected style
        String avatarId = heyGenConfiguration.getAvatar().getAvatarIdForStyle(style);
        log.info("Selected avatar '{}' for narrative style '{}'", avatarId, style);
        return avatarId;
      }
    } catch (Exception e) {
      log.error(
          "Failed to determine narrative style for slide {}: {}", slide.getId(), e.getMessage());
    }

    // Fall back to default avatar
    String defaultAvatar = heyGenConfiguration.getAvatar().getDefaultId();
    log.debug("Using default avatar: {}", defaultAvatar);
    return defaultAvatar;
  }

  /**
   * Extract narrative style from generation metadata.
   *
   * @param generationMetadata the JSON metadata string
   * @return the style (business, funny, cynical) or "business" as default
   */
  @SuppressWarnings("unchecked")
  private String extractStyleFromMetadata(String generationMetadata) {
    if (generationMetadata == null || generationMetadata.isEmpty()) {
      return "business";
    }

    try {
      java.util.Map<String, Object> metadata =
          objectMapper.readValue(generationMetadata, java.util.Map.class);

      String style = (String) metadata.get("narrativeStyle");
      if (style != null) {
        return style;
      }
    } catch (Exception e) {
      log.debug("Failed to parse generation metadata: {}", e.getMessage());
    }

    return "business";
  }

  /**
   * Check and refresh expired R2 URLs for avatar videos.
   *
   * @param avatarVideo the avatar video entity
   * @return refreshed published URL or existing one if still valid
   */
  private String refreshPublishedUrlIfNeeded(AvatarVideo avatarVideo) {
    // If no R2 asset, return null
    if (avatarVideo.getR2Asset() == null) {
      return null;
    }

    // Generate fresh presigned URL
    return r2AssetService.regeneratePresignedUrl(avatarVideo.getR2Asset().getId());
  }

  /**
   * Convert entity to response DTO.
   *
   * @param avatarVideo the entity
   * @return the response DTO
   */
  private AvatarVideoResponse convertToResponse(AvatarVideo avatarVideo) {
    // Refresh published URL if needed
    String refreshedUrl = refreshPublishedUrlIfNeeded(avatarVideo);

    // IMPORTANT: If we have a publishedUrl (R2), use that as the videoUrl
    // The frontend should use publishedUrl, but as a fallback, we provide
    // the R2 URL in videoUrl field if publishedUrl exists
    String videoUrlToReturn = refreshedUrl != null ? refreshedUrl : avatarVideo.getVideoUrl();

    return AvatarVideoResponse.builder()
        .id(avatarVideo.getId())
        .presentationId(avatarVideo.getPresentation().getId())
        .slideId(avatarVideo.getSlide().getId())
        .providerType(avatarVideo.getProviderType())
        .providerVideoId(avatarVideo.getProviderVideoId())
        .status(avatarVideo.getStatus())
        .avatarId(avatarVideo.getAvatarId())
        .backgroundColor(avatarVideo.getBackgroundColor())
        .audioUrl(avatarVideo.getAudioUrl())
        .videoUrl(videoUrlToReturn) // Use R2 URL if available
        .publishedUrl(refreshedUrl)
        .r2AssetId(avatarVideo.getR2Asset() != null ? avatarVideo.getR2Asset().getId() : null)
        .durationSeconds(avatarVideo.getDurationSeconds())
        .progressPercentage(avatarVideo.getProgressPercentage())
        .errorMessage(avatarVideo.getErrorMessage())
        .startedAt(avatarVideo.getStartedAt())
        .completedAt(avatarVideo.getCompletedAt())
        .createdAt(avatarVideo.getCreatedAt())
        .updatedAt(avatarVideo.getUpdatedAt())
        .metadata(avatarVideo.getRequestPayload())
        .build();
  }
}
