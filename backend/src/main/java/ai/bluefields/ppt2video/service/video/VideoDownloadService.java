package ai.bluefields.ppt2video.service.video;

import ai.bluefields.ppt2video.dto.AssetDto;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.repository.VideoStoryRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for downloading rendered videos from providers and storing them in R2. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoDownloadService {

  private final VideoStoryRepository videoStoryRepository;
  private final R2AssetService r2AssetService;

  @Value("${app.storage.base-path:./storage}")
  private String storagePath;

  @Value("${app.storage.temp-path:./temp}")
  private String tempPath;

  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

  /**
   * Downloads a video from the provider URL and stores it in R2.
   *
   * @param renderJob The completed render job with video URL
   * @return The R2 URL of the stored video
   */
  @Transactional
  public String processCompletedRender(RenderJob renderJob) {
    log.info("Processing completed render job: {}", renderJob.getId());

    if (renderJob.getOutputUrl() == null) {
      throw new IllegalStateException("Render job has no output URL");
    }

    VideoStory videoStory = renderJob.getVideoStory();
    if (videoStory == null) {
      throw new IllegalStateException("Render job has no associated video story");
    }

    // Skip if already downloaded
    if (videoStory.getR2VideoUrl() != null) {
      log.info("Video already downloaded for story: {}", videoStory.getId());
      return videoStory.getR2VideoUrl();
    }

    Path localFile = null;
    try {
      // Download video from Shotstack
      byte[] videoData = downloadVideoFromProvider(renderJob.getOutputUrl(), videoStory.getId());
      log.info("Downloaded {} bytes of video data", videoData.length);

      // Save to local storage with unique video story ID
      // {basePath}/presentations/{presentationId}/video-stories/{videoStoryId}.mp4
      Path storageDir =
          Paths.get(
              storagePath,
              "presentations",
              videoStory.getPresentation().getId().toString(),
              "video-stories");
      Files.createDirectories(storageDir);

      String fileName = String.format("%s.mp4", videoStory.getId());
      localFile = storageDir.resolve(fileName);
      Files.write(localFile, videoData);
      log.info("Saved video to local storage: {}", localFile);

      // Publish to R2 using the existing service
      AssetDto publishedAsset =
          r2AssetService.publishExistingAsset(
              videoStory.getPresentation().getId(),
              null, // No specific slide for full presentation video
              AssetType.PRESENTATION_FULL_VIDEO,
              true); // Force republish

      // Update video story with R2 URL and metadata
      videoStory.setR2VideoUrl(publishedAsset.getDownloadUrl());
      videoStory.setVideoFileSize((long) videoData.length);
      videoStory.setDownloadedAt(LocalDateTime.now());
      videoStory.setStatus(
          VideoStoryStatus.COMPLETED); // Mark as completed after successful download

      // TODO: Extract video duration if needed (requires video processing library)
      // For now, we'll use the expected 8 seconds for intro videos
      videoStory.setVideoDurationSeconds(new BigDecimal("8.0"));

      videoStoryRepository.save(videoStory);

      log.info(
          "Successfully processed video for story: {} -> {}",
          videoStory.getId(),
          publishedAsset.getDownloadUrl());
      return publishedAsset.getDownloadUrl();

    } catch (Exception e) {
      log.error("Failed to process completed render for job: {}", renderJob.getId(), e);
      throw new RuntimeException("Failed to download and store video: " + e.getMessage(), e);
    }
  }

  /**
   * Downloads video from provider URL. Similar to how avatar videos are downloaded in
   * AvatarVideoMonitorService.
   */
  private byte[] downloadVideoFromProvider(String videoUrl, UUID videoStoryId)
      throws IOException, InterruptedException {
    log.info("Downloading video from: {}", videoUrl);

    // Download with retry logic
    int attempts = 0;
    Exception lastException = null;

    while (attempts < MAX_RETRY_ATTEMPTS) {
      attempts++;
      try {
        HttpClient client =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(URI.create(videoUrl))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response =
            client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
          byte[] videoData = response.body();
          log.info("Successfully downloaded video: {} bytes", videoData.length);
          return videoData;
        } else {
          throw new IOException("Failed to download video. Status code: " + response.statusCode());
        }
      } catch (Exception e) {
        lastException = e;
        log.warn("Download attempt {} failed: {}", attempts, e.getMessage());
        if (attempts < MAX_RETRY_ATTEMPTS) {
          Thread.sleep(2000 * attempts); // Exponential backoff
        }
      }
    }

    throw new IOException(
        "Failed to download video after " + MAX_RETRY_ATTEMPTS + " attempts", lastException);
  }
}
