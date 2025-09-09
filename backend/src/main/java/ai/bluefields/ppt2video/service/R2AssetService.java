package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.config.R2ClientFactory;
import ai.bluefields.ppt2video.config.R2Configuration;
import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.repository.SlideSpeechRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * Core service for managing assets in Cloudflare R2. Handles upload, download, deletion, and
 * listing of assets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class R2AssetService {

  private final AssetMetadataService assetMetadataService;
  private final PresignedUrlService presignedUrlService;
  private final R2ClientFactory r2ClientFactory;
  private final R2Configuration r2Configuration;
  private final SlideRepository slideRepository;
  private final SlideSpeechRepository slideSpeechRepository;

  @Value("${app.storage.base-path:./storage}")
  private String storagePath;

  private static final String VIDEO_ASSETS_BUCKET = "video-assets";
  private static final String VIDEO_GENERATION_BUCKET = "video-generation";

  /**
   * Initiate asset upload with pre-signed URL.
   *
   * @param request the upload request
   * @return upload response with pre-signed URL
   */
  public AssetUploadResponse initiateUpload(AssetUploadRequest request) {
    try {
      // Determine bucket based on asset type
      String bucketName = determineBucket(request.getAssetType());

      // Generate object key
      String objectKey = generateObjectKey(request);

      // Create asset metadata record
      AssetMetadata asset =
          assetMetadataService.createAsset(
              request.getPresentationId(),
              request.getSlideId(),
              request.getAssetType(),
              bucketName,
              objectKey,
              request.getFileName());

      asset.setContentType(request.getContentType());
      asset.setFileSize(request.getFileSize());
      asset.setChecksum(request.getChecksum());
      asset.setMetadata(request.getMetadata());

      // Generate pre-signed upload URL
      PresignedUrl presignedUrl =
          presignedUrlService.generateUploadUrl(asset, request.getContentType());

      // Convert expiration to Instant
      Instant expiresAt = presignedUrl.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant();

      return AssetUploadResponse.builder()
          .assetId(asset.getId())
          .uploadUrl(presignedUrl.getPresignedUrl())
          .expiresAt(expiresAt)
          .bucketName(bucketName)
          .objectKey(objectKey)
          .uploadMethod("PUT")
          .build();

    } catch (Exception e) {
      log.error("Failed to initiate upload", e);
      throw new ProcessingException("Failed to initiate upload: " + e.getMessage());
    }
  }

  /**
   * Publish an existing asset file to R2. Locates the file based on asset type and uploads it.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID (optional)
   * @param assetType the type of asset
   * @return asset DTO with upload details
   */
  public AssetDto publishExistingAsset(UUID presentationId, UUID slideId, AssetType assetType) {
    return publishExistingAsset(presentationId, slideId, assetType, false);
  }

  /**
   * Publish an existing asset file to R2, with option to force republish.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID (optional)
   * @param assetType the type of asset
   * @param forceRepublish if true, will delete existing asset and upload new one
   * @return asset DTO with upload details
   */
  public AssetDto publishExistingAsset(
      UUID presentationId, UUID slideId, AssetType assetType, boolean forceRepublish) {
    try {
      // Locate the file based on asset type
      Path filePath = locateAssetFile(presentationId, slideId, assetType);

      if (filePath == null) {
        throw new ProcessingException(
            "Could not locate asset file for type: " + assetType + ", slideId: " + slideId);
      }

      if (!Files.exists(filePath)) {
        throw new ProcessingException(
            "Asset file not found at path: " + filePath + " for type: " + assetType);
      }

      log.info("Found asset file at: {} (size: {} bytes)", filePath, Files.size(filePath));

      // Get file metadata
      String fileName = filePath.getFileName().toString();
      long fileSize = Files.size(filePath);
      String contentType = determineContentType(assetType, fileName);

      // Determine bucket
      String bucketName = determineBucket(assetType);

      // Generate object key
      String objectKey = generateObjectKeyForExisting(presentationId, slideId, assetType, fileName);

      // Check if asset already exists
      List<AssetMetadata> existingAssets =
          assetMetadataService.getAssetsByPresentationAndType(presentationId, assetType);
      if (!existingAssets.isEmpty() && slideId != null) {
        existingAssets =
            existingAssets.stream()
                .filter(a -> slideId.equals(a.getSlide() != null ? a.getSlide().getId() : null))
                .collect(Collectors.toList());
      }

      if (!existingAssets.isEmpty()) {
        if (forceRepublish) {
          log.info(
              "Force republish enabled, deleting existing asset for {} - {}",
              assetType,
              presentationId);
          // Delete existing assets from R2 and metadata
          for (AssetMetadata existing : existingAssets) {
            try {
              // Delete from R2
              S3Client s3Client = r2ClientFactory.getS3Client();
              DeleteObjectRequest deleteRequest =
                  DeleteObjectRequest.builder()
                      .bucket(existing.getBucketName())
                      .key(existing.getObjectKey())
                      .build();
              s3Client.deleteObject(deleteRequest);
              log.info(
                  "Deleted existing asset from R2: {}/{}",
                  existing.getBucketName(),
                  existing.getObjectKey());
            } catch (Exception e) {
              log.warn("Failed to delete existing asset from R2: {}", e.getMessage());
            }
            // Delete metadata
            assetMetadataService.deleteAsset(existing.getId());
          }
        } else {
          log.info("Asset already exists for {} - {}", assetType, presentationId);
          AssetMetadata existing = existingAssets.get(0);
          PresignedUrl downloadUrl = presignedUrlService.generateDownloadUrl(existing);
          return convertToDto(existing, downloadUrl);
        }
      }

      // Create asset metadata
      AssetMetadata asset =
          assetMetadataService.createAsset(
              presentationId, slideId, assetType, bucketName, objectKey, fileName);

      // Upload file to R2 using pre-signed URL instead of direct upload
      // This approach works better with R2's signature requirements
      log.info("Uploading file using pre-signed URL approach for better R2 compatibility");

      // Generate pre-signed upload URL
      PresignedUrl uploadUrl = presignedUrlService.generateUploadUrl(asset, contentType);

      // Read file content
      byte[] fileContent = Files.readAllBytes(filePath);

      // Upload using the pre-signed URL with HTTP client
      java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
      java.net.http.HttpRequest request =
          java.net.http.HttpRequest.newBuilder()
              .uri(java.net.URI.create(uploadUrl.getPresignedUrl()))
              .header("Content-Type", contentType)
              .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(fileContent))
              .build();

      try {
        java.net.http.HttpResponse<String> response =
            httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
          throw new ProcessingException(
              "Failed to upload file to R2. Status: "
                  + response.statusCode()
                  + ", Body: "
                  + response.body());
        }
        log.info("Successfully uploaded file to R2 using pre-signed URL");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ProcessingException("Upload interrupted: " + e.getMessage());
      }

      log.info("Uploaded {} to R2: {}/{}", assetType, bucketName, objectKey);

      // Update asset metadata
      asset =
          assetMetadataService.updateAssetAfterUpload(asset.getId(), fileSize, contentType, null);

      // Generate download URL
      PresignedUrl downloadUrl = presignedUrlService.generateDownloadUrl(asset);

      return convertToDto(asset, downloadUrl);

    } catch (IOException e) {
      log.error("Failed to read asset file", e);
      throw new ProcessingException("Failed to read asset file: " + e.getMessage());
    } catch (Exception e) {
      log.error("Failed to publish asset", e);
      throw new ProcessingException("Failed to publish asset: " + e.getMessage());
    }
  }

  /**
   * Locate the actual file based on asset type.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID
   * @param assetType the asset type
   * @return path to the file
   */
  private Path locateAssetFile(UUID presentationId, UUID slideId, AssetType assetType) {
    String basePath = storagePath + "/presentations/" + presentationId;

    switch (assetType) {
      case SLIDE_IMAGE:
        if (slideId != null) {
          // Get slide to find the image path
          Slide slide = slideRepository.findById(slideId).orElse(null);
          if (slide != null && slide.getImagePath() != null) {
            // The image path in DB might be relative, check if file exists
            Path imagePath = Paths.get(slide.getImagePath());
            if (Files.exists(imagePath)) {
              return imagePath;
            }
            // If not found, try without the ./ prefix
            String cleanPath =
                slide.getImagePath().startsWith("./")
                    ? slide.getImagePath().substring(2)
                    : slide.getImagePath();
            imagePath = Paths.get(cleanPath);
            if (Files.exists(imagePath)) {
              return imagePath;
            }
            log.warn("Slide image file not found at: {} or {}", slide.getImagePath(), cleanPath);
          }
          // Try standard location
          int slideNumber = slide != null ? slide.getSlideNumber() : 1;
          Path standardPath = Paths.get(basePath, "slides", "slide-" + slideNumber + ".png");
          if (Files.exists(standardPath)) {
            return standardPath;
          }
          log.warn("No slide image found for slide {} at standard path: {}", slideId, standardPath);
        }
        break;

      case SLIDE_AUDIO:
        if (slideId != null) {
          // Get all active speeches for this slide, ordered by most recent first
          List<SlideSpeech> speeches =
              slideSpeechRepository.findAllActiveBySlideIdOrderByCreatedAtDesc(slideId);

          if (!speeches.isEmpty()) {
            // If multiple active speeches exist, use the most recent one
            if (speeches.size() > 1) {
              log.warn(
                  "Found {} active speeches for slide {}, using most recent",
                  speeches.size(),
                  slideId);
            }

            SlideSpeech mostRecentSpeech = speeches.get(0);
            if (mostRecentSpeech.getAudioFilePath() != null) {
              Path audioPath = Paths.get(mostRecentSpeech.getAudioFilePath());
              if (Files.exists(audioPath)) {
                log.info("Using audio file: {}", audioPath);
                return audioPath;
              }
              // Try without ./ prefix if present
              String cleanPath =
                  mostRecentSpeech.getAudioFilePath().startsWith("./")
                      ? mostRecentSpeech.getAudioFilePath().substring(2)
                      : mostRecentSpeech.getAudioFilePath();
              audioPath = Paths.get(cleanPath);
              if (Files.exists(audioPath)) {
                log.info("Using audio file: {}", audioPath);
                return audioPath;
              }
              log.warn(
                  "Audio file not found at: {} or {}",
                  mostRecentSpeech.getAudioFilePath(),
                  cleanPath);
            }
          }
          // Try standard location
          Path standardPath = Paths.get(basePath, "audio", "slide-" + slideId + ".mp3");
          if (Files.exists(standardPath)) {
            return standardPath;
          }
          log.warn("No audio file found for slide {} at standard path: {}", slideId, standardPath);
        }
        break;

      case SLIDE_AVATAR_VIDEO:
        if (slideId != null) {
          // Look for avatar videos in the expected directory
          Path avatarVideoDir = Paths.get(basePath, "slides", slideId.toString(), "avatar_videos");
          if (Files.exists(avatarVideoDir) && Files.isDirectory(avatarVideoDir)) {
            try {
              // Find the most recent avatar video file
              return Files.list(avatarVideoDir)
                  .filter(Files::isRegularFile)
                  .filter(p -> p.getFileName().toString().endsWith(".mp4"))
                  .max(
                      (p1, p2) -> {
                        try {
                          return Files.getLastModifiedTime(p1)
                              .compareTo(Files.getLastModifiedTime(p2));
                        } catch (IOException e) {
                          return 0;
                        }
                      })
                  .orElse(null);
            } catch (IOException e) {
              log.error("Error listing avatar video files", e);
            }
          }
          log.warn("No avatar video directory found for slide {} at: {}", slideId, avatarVideoDir);
        }
        break;

      case PRESENTATION_FULL_VIDEO:
        return Paths.get(basePath, "video", "presentation.mp4");

      case PRESENTATION_INTRO_VIDEO:
        // Look for intro videos in the expected directory
        Path introVideoDir = Paths.get(basePath, "intro_videos");
        if (Files.exists(introVideoDir) && Files.isDirectory(introVideoDir)) {
          try {
            // Find the most recent intro video file
            return Files.list(introVideoDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".mp4"))
                .max(
                    (p1, p2) -> {
                      try {
                        return Files.getLastModifiedTime(p1)
                            .compareTo(Files.getLastModifiedTime(p2));
                      } catch (IOException e) {
                        return 0;
                      }
                    })
                .orElse(null);
          } catch (IOException e) {
            log.error("Error listing intro video files", e);
          }
        }
        log.warn("No intro video found at: {}", introVideoDir);
        break;

      default:
        log.warn("Asset type {} not yet implemented", assetType);
    }

    return null;
  }

  /**
   * Determine content type based on asset type.
   *
   * @param assetType the asset type
   * @param fileName the file name
   * @return content type
   */
  private String determineContentType(AssetType assetType, String fileName) {
    return switch (assetType) {
      case SLIDE_IMAGE -> fileName.endsWith(".jpg") ? "image/jpeg" : "image/png";
      case SLIDE_AUDIO -> "audio/mpeg";
      case SLIDE_AVATAR_VIDEO, PRESENTATION_INTRO_VIDEO, PRESENTATION_FULL_VIDEO -> "video/mp4";
    };
  }

  /**
   * Generate object key for existing asset.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID
   * @param assetType the asset type
   * @param fileName the file name
   * @return object key
   */
  private String generateObjectKeyForExisting(
      UUID presentationId, UUID slideId, AssetType assetType, String fileName) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    String sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9.-]", "_");

    String prefix =
        switch (assetType) {
          case SLIDE_IMAGE -> "slide_image";
          case SLIDE_AUDIO -> "slide_audio";
          case SLIDE_AVATAR_VIDEO -> "slide_avatar_video";
          case PRESENTATION_INTRO_VIDEO -> "presentation_intro_video";
          case PRESENTATION_FULL_VIDEO -> "presentation_full_video";
        };

    if (slideId != null) {
      return String.format(
          "presentations/%s/slides/%s/%s/%s_%s",
          presentationId, slideId, prefix, timestamp, sanitizedFileName);
    } else {
      return String.format(
          "presentations/%s/%s/%s_%s", presentationId, prefix, timestamp, sanitizedFileName);
    }
  }

  /**
   * Confirm upload completion and update metadata.
   *
   * @param assetId the asset ID
   * @param fileSize actual file size
   * @param checksum file checksum
   * @return updated asset DTO
   */
  public AssetDto confirmUpload(UUID assetId, Long fileSize, String checksum) {
    try {
      AssetMetadata asset =
          assetMetadataService.updateAssetAfterUpload(assetId, fileSize, null, checksum);

      // Generate download URL
      PresignedUrl downloadUrl = presignedUrlService.generateDownloadUrl(asset);

      return convertToDto(asset, downloadUrl);
    } catch (Exception e) {
      log.error("Failed to confirm upload for asset: {}", assetId, e);
      assetMetadataService.updateUploadStatus(assetId, UploadStatus.FAILED, e.getMessage());
      throw new ProcessingException("Failed to confirm upload: " + e.getMessage());
    }
  }

  /**
   * Get asset with download URL.
   *
   * @param assetId the asset ID
   * @return asset DTO with download URL
   */
  @Transactional(readOnly = true)
  public AssetDto getAsset(UUID assetId) {
    AssetMetadata asset =
        assetMetadataService
            .getAsset(assetId)
            .orElseThrow(() -> new ProcessingException("Asset not found: " + assetId));

    PresignedUrl downloadUrl = null;
    if (asset.getUploadStatus() == UploadStatus.COMPLETED) {
      downloadUrl = presignedUrlService.generateDownloadUrl(asset);
    }

    return convertToDto(asset, downloadUrl);
  }

  /**
   * List assets for a presentation.
   *
   * @param presentationId the presentation ID
   * @return list of asset DTOs
   */
  @Transactional(readOnly = true)
  public List<AssetDto> listPresentationAssets(UUID presentationId) {
    List<AssetMetadata> assets = assetMetadataService.getAssetsByPresentation(presentationId);

    return assets.stream()
        .map(
            asset -> {
              PresignedUrl downloadUrl = null;
              if (asset.getUploadStatus() == UploadStatus.COMPLETED) {
                downloadUrl = presignedUrlService.generateDownloadUrl(asset);
              }
              return convertToDto(asset, downloadUrl);
            })
        .collect(Collectors.toList());
  }

  /**
   * List assets for a slide.
   *
   * @param slideId the slide ID
   * @return list of asset DTOs
   */
  @Transactional(readOnly = true)
  public List<AssetDto> listSlideAssets(UUID slideId) {
    List<AssetMetadata> assets = assetMetadataService.getAssetsBySlide(slideId);

    return assets.stream()
        .map(
            asset -> {
              PresignedUrl downloadUrl = null;
              if (asset.getUploadStatus() == UploadStatus.COMPLETED) {
                downloadUrl = presignedUrlService.generateDownloadUrl(asset);
              }
              return convertToDto(asset, downloadUrl);
            })
        .collect(Collectors.toList());
  }

  /**
   * Delete asset from R2 and database.
   *
   * @param assetId the asset ID
   */
  public void deleteAsset(UUID assetId) {
    AssetMetadata asset =
        assetMetadataService
            .getAsset(assetId)
            .orElseThrow(() -> new ProcessingException("Asset not found: " + assetId));

    try {
      // Delete from R2
      S3Client s3Client = r2ClientFactory.getS3Client();
      DeleteObjectRequest deleteRequest =
          DeleteObjectRequest.builder()
              .bucket(asset.getBucketName())
              .key(asset.getObjectKey())
              .build();

      s3Client.deleteObject(deleteRequest);
      log.info("Deleted asset from R2: {}/{}", asset.getBucketName(), asset.getObjectKey());

      // Delete URLs
      presignedUrlService.deleteUrlsForAsset(assetId);

      // Delete metadata
      assetMetadataService.deleteAsset(assetId);

    } catch (Exception e) {
      log.error("Failed to delete asset: {}", assetId, e);
      throw new ProcessingException("Failed to delete asset: " + e.getMessage());
    }
  }

  /**
   * Re-sign expired URL for an asset.
   *
   * @param assetId the asset ID
   * @return new pre-signed URL DTO
   */
  public PresignedUrlDto resignUrl(UUID assetId) {
    AssetMetadata asset =
        assetMetadataService
            .getAsset(assetId)
            .orElseThrow(() -> new ProcessingException("Asset not found: " + assetId));

    PresignedUrl newUrl = presignedUrlService.resignUrl(assetId, UrlType.DOWNLOAD, asset);

    return PresignedUrlDto.builder()
        .id(newUrl.getId())
        .assetMetadataId(assetId)
        .urlType(newUrl.getUrlType())
        .presignedUrl(newUrl.getPresignedUrl())
        .expiresAt(newUrl.getExpiresAt())
        .isActive(newUrl.getIsActive())
        .accessCount(newUrl.getAccessCount())
        .createdAt(newUrl.getCreatedAt())
        .createdBy(newUrl.getCreatedBy())
        .build();
  }

  /**
   * Get all URLs for an asset.
   *
   * @param assetId the asset ID
   * @return list of pre-signed URL DTOs
   */
  @Transactional(readOnly = true)
  public List<PresignedUrlDto> getAssetUrls(UUID assetId) {
    List<PresignedUrl> urls = presignedUrlService.getUrlsForAsset(assetId);

    return urls.stream()
        .map(
            url ->
                PresignedUrlDto.builder()
                    .id(url.getId())
                    .assetMetadataId(assetId)
                    .urlType(url.getUrlType())
                    .presignedUrl(url.getPresignedUrl())
                    .expiresAt(url.getExpiresAt())
                    .isActive(url.getIsActive())
                    .accessCount(url.getAccessCount())
                    .createdAt(url.getCreatedAt())
                    .createdBy(url.getCreatedBy())
                    .build())
        .collect(Collectors.toList());
  }

  /**
   * Determine bucket name based on asset type.
   *
   * @param assetType the asset type
   * @return bucket name
   */
  private String determineBucket(AssetType assetType) {
    return switch (assetType) {
      case SLIDE_IMAGE, SLIDE_AUDIO, SLIDE_AVATAR_VIDEO, PRESENTATION_INTRO_VIDEO ->
          VIDEO_ASSETS_BUCKET;
      case PRESENTATION_FULL_VIDEO -> VIDEO_GENERATION_BUCKET;
    };
  }

  /**
   * Generate object key for asset.
   *
   * @param request the upload request
   * @return generated object key
   */
  private String generateObjectKey(AssetUploadRequest request) {
    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    String sanitizedFileName = request.getFileName().replaceAll("[^a-zA-Z0-9.-]", "_");

    String prefix =
        switch (request.getAssetType()) {
          case SLIDE_IMAGE -> "slide_image";
          case SLIDE_AUDIO -> "slide_audio";
          case SLIDE_AVATAR_VIDEO -> "slide_avatar_video";
          case PRESENTATION_INTRO_VIDEO -> "presentation_intro_video";
          case PRESENTATION_FULL_VIDEO -> "presentation_full_video";
        };

    if (request.getSlideId() != null) {
      return String.format(
          "presentations/%s/slides/%s/%s/%s_%s",
          request.getPresentationId(), request.getSlideId(), prefix, timestamp, sanitizedFileName);
    } else {
      return String.format(
          "presentations/%s/%s/%s_%s",
          request.getPresentationId(), prefix, timestamp, sanitizedFileName);
    }
  }

  /**
   * Convert entity to DTO.
   *
   * @param asset the asset entity
   * @param downloadUrl the download URL (optional)
   * @return asset DTO
   */
  private AssetDto convertToDto(AssetMetadata asset, PresignedUrl downloadUrl) {
    AssetDto.AssetDtoBuilder builder =
        AssetDto.builder()
            .id(asset.getId())
            .presentationId(asset.getPresentation().getId())
            .slideId(asset.getSlide() != null ? asset.getSlide().getId() : null)
            .assetType(asset.getAssetType())
            .bucketName(asset.getBucketName())
            .objectKey(asset.getObjectKey())
            .fileName(asset.getFileName())
            .fileSize(asset.getFileSize())
            .contentType(asset.getContentType())
            .checksum(asset.getChecksum())
            .metadata(asset.getMetadata())
            .uploadStatus(asset.getUploadStatus())
            .errorMessage(asset.getErrorMessage())
            .createdAt(asset.getCreatedAt())
            .updatedAt(asset.getUpdatedAt());

    if (downloadUrl != null) {
      builder
          .downloadUrl(downloadUrl.getPresignedUrl())
          .downloadUrlExpiresAt(downloadUrl.getExpiresAt());
    }

    return builder.build();
  }
}
