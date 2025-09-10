package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.video.VideoStoryRequest;
import ai.bluefields.ppt2video.dto.video.VideoStoryResponse;
import ai.bluefields.ppt2video.service.video.ShotstackAssetPublisher;
import ai.bluefields.ppt2video.service.video.VideoStoryOrchestrationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for video story operations. Handles creation and status checking of video
 * stories.
 */
@RestController
@RequestMapping("/api/video-stories")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
public class VideoStoryController {

  private final VideoStoryOrchestrationService orchestrationService;
  private final ShotstackAssetPublisher shotstackAssetPublisher;

  @Value("${shotstack.assets.mode:r2-direct}")
  private String assetMode;

  /**
   * Creates a new video story composition without rendering. Generates the JSON composition for
   * preview.
   *
   * @param request The video story creation request
   * @return The created video story with composition data
   */
  @PostMapping
  public ResponseEntity<VideoStoryResponse> createVideoStory(
      @RequestBody VideoStoryRequest request,
      @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
    log.info(
        "Creating video story composition for presentation: {} (force: {})",
        request.getPresentationId(),
        force);

    try {
      VideoStoryResponse response = orchestrationService.createVideoStory(request, force);
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException e) {
      log.error("Invalid request: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      log.error("Failed to create video story composition", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Gets a video story with its composition.
   *
   * @param id The video story ID
   * @return The video story with composition data
   */
  @GetMapping("/{id}")
  public ResponseEntity<VideoStoryResponse> getVideoStory(@PathVariable UUID id) {
    log.info("Getting video story: {}", id);

    try {
      VideoStoryResponse response = orchestrationService.getVideoStory(id);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.error("Video story not found: {}", id);
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("Failed to get video story", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Renders an existing video story. Submits the composition for actual video rendering.
   *
   * @param id The video story ID to render
   * @return The render job status
   */
  @PostMapping("/{id}/render")
  public ResponseEntity<VideoStoryResponse> renderVideoStory(@PathVariable UUID id) {
    log.info("Rendering video story: {}", id);

    try {
      VideoStoryResponse response = orchestrationService.renderVideoStory(id);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.error("Video story not found: {}", id);
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("Failed to render video story", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Checks the render status of a video story.
   *
   * @param id The video story ID
   * @return The current render status
   */
  @GetMapping("/{id}/render/status")
  public ResponseEntity<VideoStoryResponse> checkRenderStatus(@PathVariable UUID id) {
    log.info("Checking render status for video story: {}", id);

    try {
      VideoStoryResponse response = orchestrationService.checkRenderStatus(id);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.error("Video story not found: {}", id);
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("Failed to check render status", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Gets a presigned URL for playing the video. Generates a temporary URL for direct video access.
   *
   * @param id The video story ID
   * @return Presigned URL for video playback
   */
  @GetMapping("/{id}/video-url")
  public ResponseEntity<Map<String, String>> getVideoUrl(@PathVariable UUID id) {
    log.info("Getting video URL for story: {}", id);

    try {
      VideoStoryResponse story = orchestrationService.getVideoStory(id);

      if (story.getVideoUrl() == null) {
        log.warn("No video URL available for story: {}", id);
        return ResponseEntity.notFound().build();
      }

      String videoUrl = story.getVideoUrl();

      // The videoUrl already contains the presigned URL from R2 if it's been downloaded
      // No need to check for "r2://" format since we store the actual presigned URL

      Map<String, String> response = new HashMap<>();
      response.put("videoUrl", videoUrl);
      response.put("stored", String.valueOf(story.isVideoStored()));

      log.info(
          "Returning video URL for story {}: {}",
          id,
          videoUrl != null ? "URL available" : "No URL");

      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException e) {
      log.error("Video story not found: {}", id);
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      log.error("Failed to get video URL", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Gets all video stories for a presentation.
   *
   * @param presentationId The presentation ID
   * @return List of video stories for the presentation
   */
  @GetMapping("/presentations/{presentationId}")
  public ResponseEntity<List<VideoStoryResponse>> getVideoStoriesForPresentation(
      @PathVariable UUID presentationId) {
    log.info("Getting video stories for presentation: {}", presentationId);

    try {
      List<VideoStoryResponse> stories =
          orchestrationService.getVideoStoriesForPresentation(presentationId);
      return ResponseEntity.ok(stories);
    } catch (Exception e) {
      log.error("Failed to get video stories for presentation", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Refreshes Shotstack assets for a presentation (forces re-upload). Only available when
   * shotstack-upload mode is enabled.
   *
   * @param presentationId The presentation ID
   * @return Number of assets cleared for re-upload
   */
  @PostMapping("/presentations/{presentationId}/refresh-shotstack-assets")
  public ResponseEntity<Map<String, Object>> refreshShotstackAssets(
      @PathVariable UUID presentationId) {
    log.info("Refreshing Shotstack assets for presentation: {}", presentationId);

    // Check if shotstack-upload mode is enabled
    if (!"shotstack-upload".equalsIgnoreCase(assetMode)) {
      log.warn("Refresh assets called but asset mode is: {}", assetMode);
      Map<String, Object> response = new HashMap<>();
      response.put("error", "Shotstack asset refresh is only available in shotstack-upload mode");
      response.put("currentMode", assetMode);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    try {
      int refreshedCount = shotstackAssetPublisher.refreshAllAssets(presentationId);

      Map<String, Object> response = new HashMap<>();
      response.put("message", "Shotstack asset URLs cleared successfully");
      response.put("assetsCleared", refreshedCount);
      response.put("presentationId", presentationId.toString());

      log.info("Cleared {} Shotstack URLs for presentation: {}", refreshedCount, presentationId);

      return ResponseEntity.ok(response);
    } catch (Exception e) {
      log.error("Failed to refresh Shotstack assets", e);
      Map<String, Object> response = new HashMap<>();
      response.put("error", "Failed to refresh assets: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
  }

  /** Health check endpoint for the video story API. */
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("Video Story API is healthy");
  }
}
