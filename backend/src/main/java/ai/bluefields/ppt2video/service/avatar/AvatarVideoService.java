package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.exception.ResourceNotFoundException;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
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
  private final R2AssetService r2AssetService;
  private final AvatarProviderFactory avatarProviderFactory;
  private final AvatarVideoMonitorService avatarVideoMonitorService;

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
            .avatarId(request.getAvatarId())
            .backgroundColor(request.getBackgroundColor())
            .audioUrl(audioUrl)
            .createdBy("system")
            .build();

    avatarVideo = avatarVideoRepository.save(avatarVideo);

    try {
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
    List<AvatarVideo> videos = avatarVideoRepository.findBySlideId(slideId);
    return videos.stream().map(this::convertToResponse).collect(Collectors.toList());
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
    if (avatarVideo.getPublishedUrl() != null) {
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

        // Update avatar video with published URL
        avatarVideo.setPublishedUrl(publishedAsset.getDownloadUrl());
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

    return AvatarVideoStatusDto.builder()
        .id(avatarVideo.getId())
        .status(avatarVideo.getStatus())
        .errorMessage(avatarVideo.getErrorMessage())
        .progressPercentage(avatarVideo.getProgressPercentage())
        .videoUrl(avatarVideo.getVideoUrl())
        .publishedUrl(avatarVideo.getPublishedUrl()) // Add R2 published URL
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
      List<AssetDto> slideAssets = r2AssetService.listSlideAssets(slide.getId());

      // Find audio asset
      AssetDto audioAsset =
          slideAssets.stream()
              .filter(asset -> asset.getAssetType() == AssetType.SLIDE_AUDIO)
              .filter(asset -> asset.getUploadStatus() == UploadStatus.COMPLETED)
              .findFirst()
              .orElse(null);

      if (audioAsset != null && audioAsset.getDownloadUrl() != null) {
        log.info("Using published audio from R2 for slide: {}", slide.getId());
        return audioAsset.getDownloadUrl();
      }

      // If no published audio, try to publish existing audio
      log.info(
          "No published audio found, attempting to publish existing audio for slide: {}",
          slide.getId());
      AssetDto publishedAudio =
          r2AssetService.publishExistingAsset(
              slide.getPresentation().getId(), slide.getId(), AssetType.SLIDE_AUDIO);

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
   * Convert entity to response DTO.
   *
   * @param avatarVideo the entity
   * @return the response DTO
   */
  private AvatarVideoResponse convertToResponse(AvatarVideo avatarVideo) {
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
        .videoUrl(avatarVideo.getVideoUrl())
        .r2AssetId(avatarVideo.getR2AssetId())
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
