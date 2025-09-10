package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.ApiResponse;
import ai.bluefields.ppt2video.dto.AssetDto;
import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.AvatarVideo;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for R2 upload operations. Provides endpoints for uploading local assets to R2
 * storage, specifically designed for avatar videos that already exist locally.
 */
@RestController
@RequestMapping("/api/r2")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class R2UploadController {

  private final R2AssetService r2AssetService;
  private final AvatarVideoRepository avatarVideoRepository;
  private final AssetMetadataRepository assetMetadataRepository;
  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;

  @Value("${app.storage.base-path:./storage}")
  private String storagePath;

  /**
   * Publish avatar video for a specific slide to R2. This endpoint is designed to be compatible
   * with the shell script that uploads local videos.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID
   * @param assetType the asset type (should be SLIDE_AVATAR_VIDEO)
   * @return API response with asset details
   */
  @PostMapping("/presentations/{presentationId}/slides/{slideId}/assets/{assetType}/publish")
  @Transactional
  public ResponseEntity<ApiResponse<AssetDto>> publishSlideAsset(
      @PathVariable UUID presentationId,
      @PathVariable UUID slideId,
      @PathVariable String assetType) {

    log.info("Publishing {} for presentation: {}, slide: {}", assetType, presentationId, slideId);

    try {
      // Validate asset type
      if (!assetType.equals("SLIDE_AVATAR_VIDEO")) {
        return ResponseEntity.badRequest()
            .body(
                ApiResponse.<AssetDto>builder()
                    .success(false)
                    .message("Only SLIDE_AVATAR_VIDEO is supported by this endpoint")
                    .build());
      }

      // Check if presentation exists
      if (!presentationRepository.existsById(presentationId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<AssetDto>builder()
                    .success(false)
                    .message("Presentation not found: " + presentationId)
                    .build());
      }

      // Check if slide exists
      if (!slideRepository.existsById(slideId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<AssetDto>builder()
                    .success(false)
                    .message("Slide not found: " + slideId)
                    .build());
      }

      // Check if local video file exists
      Path avatarVideoDir =
          Paths.get(
              storagePath,
              "presentations",
              presentationId.toString(),
              "slides",
              slideId.toString(),
              "avatar_videos");

      if (!Files.exists(avatarVideoDir) || !Files.isDirectory(avatarVideoDir)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<AssetDto>builder()
                    .success(false)
                    .message("No avatar video directory found for slide: " + slideId)
                    .build());
      }

      // Check if any video files exist
      boolean hasVideos =
          Files.list(avatarVideoDir).anyMatch(p -> p.getFileName().toString().endsWith(".mp4"));

      if (!hasVideos) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<AssetDto>builder()
                    .success(false)
                    .message("No avatar video files found for slide: " + slideId)
                    .build());
      }

      // Publish the asset (this will automatically pick the most recent video)
      // Use forceRepublish=true to ensure we get a fresh upload and URL
      AssetDto asset =
          r2AssetService.publishExistingAsset(
              presentationId, slideId, AssetType.SLIDE_AVATAR_VIDEO, true);

      // Update avatar video records with the R2 asset ID and published URL
      List<AvatarVideo> avatarVideos =
          avatarVideoRepository.findBySlideIdAndStatusCompleted(slideId);

      for (AvatarVideo avatarVideo : avatarVideos) {
        if (avatarVideo.getR2Asset() == null) {
          // Get AssetMetadata entity
          AssetMetadata assetMetadata =
              assetMetadataRepository
                  .findById(asset.getId())
                  .orElseThrow(() -> new RuntimeException("Failed to find asset metadata"));
          avatarVideo.setR2Asset(assetMetadata);
          avatarVideo.setPublishedAt(LocalDateTime.now());
          avatarVideoRepository.save(avatarVideo);
          log.info(
              "Updated avatar video {} with R2 asset ID: {}", avatarVideo.getId(), asset.getId());
        }
      }

      log.info("Successfully published avatar video for slide: {}", slideId);

      return ResponseEntity.ok(
          ApiResponse.<AssetDto>builder()
              .success(true)
              .message(
                  String.format("Avatar video successfully published to R2 for slide %s", slideId))
              .data(asset)
              .build());

    } catch (Exception e) {
      log.error("Failed to publish avatar video for slide: {}", slideId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<AssetDto>builder()
                  .success(false)
                  .message("Failed to publish avatar video: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Bulk upload avatar videos for all slides in a presentation.
   *
   * @param presentationId the presentation ID
   * @return API response with upload summary
   */
  @PostMapping("/presentations/{presentationId}/avatar-videos/bulk-upload")
  @Transactional
  public ResponseEntity<ApiResponse<Map<String, Object>>> bulkUploadAvatarVideos(
      @PathVariable UUID presentationId) {

    log.info("Starting bulk upload of avatar videos for presentation: {}", presentationId);

    try {
      // Check if presentation exists
      if (!presentationRepository.existsById(presentationId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("Presentation not found: " + presentationId)
                    .build());
      }

      Path presentationDir =
          Paths.get(storagePath, "presentations", presentationId.toString(), "slides");

      if (!Files.exists(presentationDir)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .message("No slides directory found for presentation: " + presentationId)
                    .build());
      }

      List<Map<String, Object>> uploadResults = new ArrayList<>();
      int successCount = 0;
      int failedCount = 0;
      int skippedCount = 0;

      // Find all slide directories with avatar videos
      List<Path> slideDirs =
          Files.list(presentationDir)
              .filter(Files::isDirectory)
              .filter(
                  dir -> {
                    Path avatarDir = dir.resolve("avatar_videos");
                    return Files.exists(avatarDir) && Files.isDirectory(avatarDir);
                  })
              .toList();

      for (Path slideDir : slideDirs) {
        String slideDirName = slideDir.getFileName().toString();
        UUID slideId;

        try {
          slideId = UUID.fromString(slideDirName);
        } catch (IllegalArgumentException e) {
          log.warn("Skipping directory with invalid UUID name: {}", slideDirName);
          continue;
        }

        Path avatarVideoDir = slideDir.resolve("avatar_videos");
        Map<String, Object> result = new HashMap<>();
        result.put("slideId", slideId.toString());

        try {
          // Check if any video files exist
          boolean hasVideos =
              Files.list(avatarVideoDir).anyMatch(p -> p.getFileName().toString().endsWith(".mp4"));

          if (!hasVideos) {
            result.put("status", "skipped");
            result.put("message", "No video files found");
            skippedCount++;
          } else {
            // Check if already published
            List<AvatarVideo> avatarVideos =
                avatarVideoRepository.findBySlideIdAndStatusCompleted(slideId);

            boolean alreadyPublished =
                avatarVideos.stream().anyMatch(av -> av.getR2Asset() != null);

            if (alreadyPublished) {
              // Force republish to get fresh URL
              AssetDto asset =
                  r2AssetService.publishExistingAsset(
                      presentationId, slideId, AssetType.SLIDE_AVATAR_VIDEO, true);

              // Update all avatar video records
              for (AvatarVideo avatarVideo : avatarVideos) {
                // Get AssetMetadata entity
                AssetMetadata assetMetadata =
                    assetMetadataRepository
                        .findById(asset.getId())
                        .orElseThrow(() -> new RuntimeException("Failed to find asset metadata"));
                avatarVideo.setR2Asset(assetMetadata);
                avatarVideo.setPublishedAt(LocalDateTime.now());
                avatarVideoRepository.save(avatarVideo);
              }

              result.put("status", "republished");
              result.put("message", "Video republished with fresh URL");
              result.put("downloadUrl", asset.getDownloadUrl());
              successCount++;
            } else {
              // First time publish
              AssetDto asset =
                  r2AssetService.publishExistingAsset(
                      presentationId, slideId, AssetType.SLIDE_AVATAR_VIDEO, false);

              // Update avatar video records
              for (AvatarVideo avatarVideo : avatarVideos) {
                if (avatarVideo.getR2Asset() == null) {
                  // Get AssetMetadata entity
                  AssetMetadata assetMetadata =
                      assetMetadataRepository
                          .findById(asset.getId())
                          .orElseThrow(() -> new RuntimeException("Failed to find asset metadata"));
                  avatarVideo.setR2Asset(assetMetadata);
                  avatarVideo.setPublishedAt(LocalDateTime.now());
                  avatarVideoRepository.save(avatarVideo);
                }
              }

              result.put("status", "uploaded");
              result.put("message", "Video successfully uploaded");
              result.put("downloadUrl", asset.getDownloadUrl());
              successCount++;
            }
          }
        } catch (Exception e) {
          log.error("Failed to process slide: {}", slideId, e);
          result.put("status", "failed");
          result.put("message", e.getMessage());
          failedCount++;
        }

        uploadResults.add(result);
      }

      Map<String, Object> summary = new HashMap<>();
      summary.put("totalSlides", uploadResults.size());
      summary.put("successCount", successCount);
      summary.put("failedCount", failedCount);
      summary.put("skippedCount", skippedCount);
      summary.put("results", uploadResults);

      log.info(
          "Bulk upload completed. Success: {}, Failed: {}, Skipped: {}",
          successCount,
          failedCount,
          skippedCount);

      return ResponseEntity.ok(
          ApiResponse.<Map<String, Object>>builder()
              .success(true)
              .message(
                  String.format(
                      "Bulk upload completed. Processed %d slides: %d successful, %d failed, %d skipped",
                      uploadResults.size(), successCount, failedCount, skippedCount))
              .data(summary)
              .build());

    } catch (Exception e) {
      log.error("Failed to perform bulk upload for presentation: {}", presentationId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<Map<String, Object>>builder()
                  .success(false)
                  .message("Failed to perform bulk upload: " + e.getMessage())
                  .build());
    }
  }
}
