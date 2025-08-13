package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.service.avatar.AvatarVideoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for avatar video generation operations. Provides endpoints for creating and
 * managing avatar videos.
 */
@RestController
@RequestMapping("/api/avatar-videos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AvatarVideoController {

  private final AvatarVideoService avatarVideoService;

  /**
   * Generate an avatar video for a slide.
   *
   * @param request the avatar video creation request
   * @return the avatar video response
   */
  @PostMapping("/generate")
  public ResponseEntity<ApiResponse<AvatarVideoResponse>> generateAvatarVideo(
      @Valid @RequestBody AvatarVideoRequest request) {
    log.info(
        "Received request to generate avatar video for slide: {} in presentation: {}",
        request.getSlideId(),
        request.getPresentationId());

    try {
      AvatarVideoResponse response = avatarVideoService.generateAvatarVideo(request);
      return ResponseEntity.ok(
          ApiResponse.<AvatarVideoResponse>builder()
              .success(true)
              .data(response)
              .message("Avatar video generation started successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to generate avatar video", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<AvatarVideoResponse>builder()
                  .success(false)
                  .message("Failed to generate avatar video")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("AVATAR_GENERATION_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }

  /**
   * Get the status of an avatar video generation job.
   *
   * @param id the avatar video ID
   * @return the status information
   */
  @GetMapping("/{id}/status")
  public ResponseEntity<ApiResponse<AvatarVideoStatusDto>> getVideoStatus(@PathVariable UUID id) {
    log.info("Getting status for avatar video: {}", id);

    try {
      AvatarVideoStatusDto status = avatarVideoService.getVideoStatus(id);
      return ResponseEntity.ok(
          ApiResponse.<AvatarVideoStatusDto>builder()
              .success(true)
              .data(status)
              .message("Status retrieved successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to get avatar video status", e);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<AvatarVideoStatusDto>builder()
                  .success(false)
                  .message("Failed to get avatar video status")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("AVATAR_STATUS_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }

  /**
   * Get all avatar videos for a slide.
   *
   * @param slideId the slide ID
   * @return list of avatar videos
   */
  @GetMapping("/slide/{slideId}")
  public ResponseEntity<ApiResponse<List<AvatarVideoResponse>>> getSlideAvatarVideos(
      @PathVariable UUID slideId) {
    log.info("Getting avatar videos for slide: {}", slideId);

    try {
      List<AvatarVideoResponse> videos = avatarVideoService.getSlideAvatarVideos(slideId);
      return ResponseEntity.ok(
          ApiResponse.<List<AvatarVideoResponse>>builder()
              .success(true)
              .data(videos)
              .message(String.format("Found %d avatar videos for slide", videos.size()))
              .build());
    } catch (Exception e) {
      log.error("Failed to get slide avatar videos", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<List<AvatarVideoResponse>>builder()
                  .success(false)
                  .message("Failed to get slide avatar videos")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("AVATAR_LIST_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }

  /**
   * Publish a completed avatar video to R2 storage.
   *
   * @param id the avatar video ID
   * @return the published asset information
   */
  @PostMapping("/{id}/publish")
  public ResponseEntity<ApiResponse<AssetDto>> publishAvatarVideo(@PathVariable UUID id) {
    log.info("Publishing avatar video to R2: {}", id);

    try {
      AssetDto publishedAsset = avatarVideoService.publishAvatarVideo(id);
      return ResponseEntity.ok(
          ApiResponse.<AssetDto>builder()
              .success(true)
              .data(publishedAsset)
              .message("Avatar video published successfully to R2")
              .build());
    } catch (Exception e) {
      log.error("Failed to publish avatar video", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<AssetDto>builder()
                  .success(false)
                  .message("Failed to publish avatar video")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("AVATAR_PUBLISH_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }

  /**
   * Cancel an avatar video generation job.
   *
   * @param id the avatar video ID
   * @return cancellation result
   */
  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<Boolean>> cancelAvatarVideo(@PathVariable UUID id) {
    log.info("Cancelling avatar video: {}", id);

    try {
      boolean cancelled = avatarVideoService.cancelAvatarVideo(id);
      return ResponseEntity.ok(
          ApiResponse.<Boolean>builder()
              .success(true)
              .data(cancelled)
              .message(
                  cancelled
                      ? "Avatar video cancelled successfully"
                      : "Could not cancel avatar video")
              .build());
    } catch (Exception e) {
      log.error("Failed to cancel avatar video", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<Boolean>builder()
                  .success(false)
                  .data(false)
                  .message("Failed to cancel avatar video")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("AVATAR_CANCEL_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }
}
