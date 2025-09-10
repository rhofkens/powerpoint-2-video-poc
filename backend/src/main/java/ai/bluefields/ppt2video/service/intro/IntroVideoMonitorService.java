package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.AssetDto;
import ai.bluefields.ppt2video.dto.veo.VeoVideoStatus;
import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.repository.IntroVideoRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for monitoring intro video generation status. Polls Veo API and handles
 * completion/failure scenarios.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntroVideoMonitorService {

  private final IntroVideoRepository introVideoRepository;
  private final AssetMetadataRepository assetMetadataRepository;
  private final VeoApiService veoApiService;
  private final R2AssetService r2AssetService;
  private final TaskScheduler taskScheduler;

  private final Map<UUID, ScheduledFuture<?>> monitoringTasks = new ConcurrentHashMap<>();

  @Value("${google.gemini.api.key:}")
  private String apiKey;

  @Value("${intro-video.monitor.initial-delay:5000}")
  private long initialDelayMs;

  @Value("${intro-video.monitor.poll-interval:10000}")
  private long pollIntervalMs;

  @Value("${intro-video.monitor.max-duration:600000}")
  private long maxDurationMs;

  /**
   * Start monitoring an intro video generation.
   *
   * @param introVideoId the intro video ID
   * @param veoGenerationId the Veo generation ID
   */
  @Async
  public void startMonitoring(UUID introVideoId, String veoGenerationId) {
    log.info("Starting monitoring for intro video: {}, Veo ID: {}", introVideoId, veoGenerationId);

    // Schedule polling task
    ScheduledFuture<?> future =
        taskScheduler.scheduleWithFixedDelay(
            () -> checkAndUpdateStatus(introVideoId, veoGenerationId),
            Instant.now().plusMillis(initialDelayMs),
            Duration.ofMillis(pollIntervalMs));

    monitoringTasks.put(introVideoId, future);

    // Schedule timeout task
    taskScheduler.schedule(
        () -> handleTimeout(introVideoId),
        new java.util.Date(System.currentTimeMillis() + maxDurationMs));
  }

  /**
   * Stop monitoring an intro video generation.
   *
   * @param introVideoId the intro video ID
   */
  public void stopMonitoring(UUID introVideoId) {
    ScheduledFuture<?> future = monitoringTasks.remove(introVideoId);
    if (future != null) {
      future.cancel(false);
      log.info("Stopped monitoring for intro video: {}", introVideoId);
    }
  }

  /** Check and update the status of a video generation. */
  @Transactional
  private void checkAndUpdateStatus(UUID introVideoId, String veoGenerationId) {
    try {
      // Get intro video entity
      IntroVideo introVideo = introVideoRepository.findById(introVideoId).orElse(null);

      if (introVideo == null) {
        log.warn("Intro video not found, stopping monitoring: {}", introVideoId);
        stopMonitoring(introVideoId);
        return;
      }

      // Check if already in terminal state
      if (isTerminalState(introVideo.getStatus())) {
        log.debug("Intro video already in terminal state: {}", introVideo.getStatus());
        stopMonitoring(introVideoId);
        return;
      }

      // Get status from Veo API
      VeoVideoStatus status = veoApiService.getVideoStatus(veoGenerationId);
      log.debug("Veo status for {}: {}", veoGenerationId, status.getState());

      // Update intro video based on status
      switch (status.getState()) {
        case COMPLETED -> handleCompletion(introVideo, status);
        case FAILED -> handleFailure(introVideo, status);
        case CANCELLED -> handleCancellation(introVideo);
        case PROCESSING -> updateProgress(introVideo, status);
        default -> log.debug("Video still pending: {}", veoGenerationId);
      }

    } catch (Exception e) {
      log.error("Error checking video status for {}", introVideoId, e);
      // Don't stop monitoring on transient errors
    }
  }

  /** Handle successful video completion. */
  private void handleCompletion(IntroVideo introVideo, VeoVideoStatus status) {
    log.info("Intro video generation completed: {}", introVideo.getId());

    introVideo.setStatus(AvatarGenerationStatusType.COMPLETED);
    introVideo.setGoogleVideoUrl(status.getVideoUrl());
    introVideo.setCompletedAt(LocalDateTime.now());
    introVideo.setDurationSeconds(
        status.getDurationSeconds() != null ? status.getDurationSeconds() : 8.0);

    introVideoRepository.save(introVideo);

    // Stop monitoring
    stopMonitoring(introVideo.getId());

    // Publish to R2 asynchronously
    publishToR2(introVideo);
  }

  /** Handle video generation failure. */
  private void handleFailure(IntroVideo introVideo, VeoVideoStatus status) {
    log.error("Intro video generation failed: {}", introVideo.getId());

    introVideo.setStatus(AvatarGenerationStatusType.FAILED);
    introVideo.setErrorMessage(
        status.getErrorMessage() != null ? status.getErrorMessage() : "Video generation failed");
    introVideo.setCompletedAt(LocalDateTime.now());

    introVideoRepository.save(introVideo);
    stopMonitoring(introVideo.getId());
  }

  /** Handle video generation cancellation. */
  private void handleCancellation(IntroVideo introVideo) {
    log.info("Intro video generation cancelled: {}", introVideo.getId());

    introVideo.setStatus(AvatarGenerationStatusType.CANCELLED);
    introVideo.setCompletedAt(LocalDateTime.now());

    introVideoRepository.save(introVideo);
    stopMonitoring(introVideo.getId());
  }

  /** Update progress for ongoing generation. */
  private void updateProgress(IntroVideo introVideo, VeoVideoStatus status) {
    if (status.getProgress() != null) {
      // Could add a progress field to IntroVideo if needed
      log.debug("Intro video generation progress: {}%", status.getProgress());
    }
  }

  /** Handle timeout scenario. */
  private void handleTimeout(UUID introVideoId) {
    log.warn("Intro video generation timeout: {}", introVideoId);

    try {
      IntroVideo introVideo = introVideoRepository.findById(introVideoId).orElse(null);

      if (introVideo != null && !isTerminalState(introVideo.getStatus())) {
        introVideo.setStatus(AvatarGenerationStatusType.FAILED);
        introVideo.setErrorMessage(
            "Generation timeout after " + (maxDurationMs / 1000) + " seconds");
        introVideo.setCompletedAt(LocalDateTime.now());
        introVideoRepository.save(introVideo);
      }
    } catch (Exception e) {
      log.error("Error handling timeout for intro video: {}", introVideoId, e);
    } finally {
      stopMonitoring(introVideoId);
    }
  }

  /** Publish video to R2 storage. */
  @Async
  private void publishToR2(IntroVideo introVideo) {
    try {
      log.info("Publishing intro video to R2: {}", introVideo.getId());

      // Download video from Google
      byte[] videoData = downloadVideo(introVideo.getGoogleVideoUrl());

      // Save to temporary file
      String fileName =
          String.format("intro_video_%s_%s.mp4", introVideo.getId(), System.currentTimeMillis());
      Path tempFile = Files.createTempFile("intro_", ".mp4");
      Files.write(tempFile, videoData);

      try {
        // Save the file to the expected location for publishExistingAsset
        Path storageDir =
            Paths.get(
                "./storage/presentations",
                introVideo.getPresentationId().toString(),
                "intro_videos");
        Files.createDirectories(storageDir);
        Path targetFile = storageDir.resolve(fileName);
        Files.move(tempFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        // Use the existing publish method with forceRepublish=true
        AssetDto asset =
            r2AssetService.publishExistingAsset(
                introVideo.getPresentationId(),
                null, // No slide ID for intro videos
                AssetType.PRESENTATION_INTRO_VIDEO,
                true); // Force republish

        // Update intro video with R2 asset reference
        AssetMetadata assetMetadata =
            assetMetadataRepository
                .findById(asset.getId())
                .orElseThrow(() -> new RuntimeException("Failed to find created asset metadata"));
        introVideo.setR2Asset(assetMetadata);
        introVideoRepository.save(introVideo);

        log.info("Successfully published intro video to R2: {}", asset.getDownloadUrl());
      } finally {
        // Clean up temp file if it still exists
        Files.deleteIfExists(tempFile);
      }

    } catch (Exception e) {
      log.error("Failed to publish intro video to R2", e);
      introVideo.setStatus(AvatarGenerationStatusType.FAILED);
      introVideo.setErrorMessage("Failed to publish video: " + e.getMessage());
      introVideoRepository.save(introVideo);
    }
  }

  /** Download video from URL. */
  private byte[] downloadVideo(String videoUrl) throws Exception {
    // Append API key to URL if not already present
    String urlWithKey = videoUrl;
    if (apiKey != null && !apiKey.isEmpty() && !videoUrl.contains("key=")) {
      urlWithKey = videoUrl + (videoUrl.contains("?") ? "&" : "?") + "key=" + apiKey;
    }

    log.debug(
        "Downloading video from: {}",
        urlWithKey.substring(0, Math.min(urlWithKey.length(), 100)) + "...");

    URL url = new URL(urlWithKey);
    try (InputStream inputStream = url.openStream()) {
      return inputStream.readAllBytes();
    }
  }

  /** Check if status is terminal. */
  private boolean isTerminalState(AvatarGenerationStatusType status) {
    return status == AvatarGenerationStatusType.COMPLETED
        || status == AvatarGenerationStatusType.FAILED
        || status == AvatarGenerationStatusType.CANCELLED;
  }
}
