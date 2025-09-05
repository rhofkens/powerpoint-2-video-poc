package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.dto.AssetDto;
import ai.bluefields.ppt2video.dto.AvatarVideoStatusDto;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarVideo;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for monitoring avatar video generation status in the background. Polls provider APIs to
 * track progress and update video status until completion. Runs asynchronously in a separate
 * transaction to ensure proper database visibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarVideoMonitorService {

  private final AvatarVideoRepository avatarVideoRepository;
  private final AvatarProviderFactory avatarProviderFactory;
  private final R2AssetService r2AssetService;

  /**
   * Monitor video status asynchronously in a new transaction.
   *
   * @param avatarVideoId the avatar video ID
   * @param providerVideoId the provider's video ID
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void monitorVideoStatus(UUID avatarVideoId, String providerVideoId) {
    log.info("Starting async monitoring for avatar video: {}", avatarVideoId);

    int maxAttempts = 90; // Monitor for up to 15 minutes (90 * 10 seconds)
    int attempt = 0;

    while (attempt < maxAttempts) {
      attempt++;

      try {
        Thread.sleep(10000); // Wait 10 seconds between checks

        AvatarVideo avatarVideo = avatarVideoRepository.findById(avatarVideoId).orElse(null);
        if (avatarVideo == null) {
          log.warn("Avatar video {} not found, stopping monitoring", avatarVideoId);
          return;
        }

        // Check if already in terminal state
        if (avatarVideo.getStatus() == AvatarGenerationStatusType.COMPLETED
            || avatarVideo.getStatus() == AvatarGenerationStatusType.FAILED
            || avatarVideo.getStatus() == AvatarGenerationStatusType.CANCELLED) {
          log.info(
              "Avatar video {} is in terminal state: {}, stopping monitoring",
              avatarVideoId,
              avatarVideo.getStatus());
          return;
        }

        // Update status from provider
        updateVideoStatus(avatarVideo);

        // Check if completed
        if (avatarVideo.getStatus() == AvatarGenerationStatusType.COMPLETED) {
          log.info("Avatar video {} completed successfully", avatarVideoId);

          // Automatically publish to R2
          publishToR2(avatarVideo);

          return;
        }

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Monitoring interrupted for avatar video: {}", avatarVideoId);
        return;
      } catch (Exception e) {
        log.error("Error monitoring avatar video: {}", avatarVideoId, e);
      }
    }

    log.warn("Monitoring timeout for avatar video: {} after {} attempts", avatarVideoId, attempt);

    // Mark as failed after timeout
    try {
      AvatarVideo avatarVideo = avatarVideoRepository.findById(avatarVideoId).orElse(null);
      if (avatarVideo != null && avatarVideo.getStatus() != AvatarGenerationStatusType.COMPLETED) {
        avatarVideo.setStatus(AvatarGenerationStatusType.FAILED);
        avatarVideo.setErrorMessage("Video generation timeout after 10 minutes");
        avatarVideo.setCompletedAt(LocalDateTime.now());
        avatarVideoRepository.save(avatarVideo);
      }
    } catch (Exception e) {
      log.error("Failed to update timeout status for avatar video: {}", avatarVideoId, e);
    }
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
      log.debug(
          "Updated avatar video {} status to {} ({}%)",
          avatarVideo.getId(), status.getStatus(), status.getProgressPercentage());

    } catch (Exception e) {
      log.error(
          "Failed to update status for avatar video: {} (provider video: {})",
          avatarVideo.getId(),
          avatarVideo.getProviderVideoId(),
          e);
    }
  }

  /**
   * Publish avatar video to R2 storage. Downloads the video from HeyGen and uploads to R2.
   *
   * @param avatarVideo the avatar video entity
   */
  private void publishToR2(AvatarVideo avatarVideo) {
    try {
      // Check if already published
      if (avatarVideo.getPublishedUrl() != null) {
        log.info("Avatar video {} already published to R2", avatarVideo.getId());
        return;
      }

      // Check if video URL exists
      if (avatarVideo.getVideoUrl() == null || avatarVideo.getVideoUrl().isEmpty()) {
        log.error("Avatar video {} has no video URL to publish", avatarVideo.getId());
        return;
      }

      log.info("Auto-publishing avatar video {} to R2", avatarVideo.getId());

      // Download video from HeyGen
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
        log.error("Failed to download video from HeyGen, HTTP status: {}", response.statusCode());
        return;
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

        // Use the existing publish method with forceRepublish=true
        // to ensure the new video is uploaded even if an old one exists
        AssetDto publishedAsset =
            r2AssetService.publishExistingAsset(
                avatarVideo.getPresentation().getId(),
                avatarVideo.getSlide().getId(),
                AssetType.SLIDE_AVATAR_VIDEO,
                true); // Force republish to upload the new video

        // Update avatar video with published URL and R2 asset ID
        avatarVideo.setPublishedUrl(publishedAsset.getDownloadUrl());
        avatarVideo.setR2AssetId(publishedAsset.getId());
        avatarVideo.setPublishedAt(LocalDateTime.now());
        avatarVideoRepository.save(avatarVideo);

        // Clear published URLs and HeyGen URLs from any older avatar videos for this slide
        // since their R2 assets have been deleted by forceRepublish
        List<AvatarVideo> olderVideos =
            avatarVideoRepository.findBySlideId(avatarVideo.getSlide().getId());
        for (AvatarVideo oldVideo : olderVideos) {
          if (!oldVideo.getId().equals(avatarVideo.getId())) {
            if (oldVideo.getPublishedUrl() != null || oldVideo.getVideoUrl() != null) {
              log.info("Clearing stale URLs from older avatar video: {}", oldVideo.getId());
              oldVideo.setPublishedUrl(null);
              oldVideo.setVideoUrl(null); // Also clear HeyGen URL to prevent confusion
              // Don't clear R2 asset ID as it's useful for tracking what was deleted
              avatarVideoRepository.save(oldVideo);
            }
          }
        }

        log.info("Successfully auto-published avatar video {} to R2", avatarVideo.getId());

      } finally {
        // Clean up temp file if it still exists
        java.nio.file.Files.deleteIfExists(tempFile);
      }

    } catch (Exception e) {
      log.error(
          "Failed to auto-publish avatar video {} to R2: {}",
          avatarVideo.getId(),
          e.getMessage(),
          e);
      // Don't fail the monitoring, video is still successfully generated
    }
  }
}
