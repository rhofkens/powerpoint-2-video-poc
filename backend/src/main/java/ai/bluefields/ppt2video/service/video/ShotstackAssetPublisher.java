package ai.bluefields.ppt2video.service.video;

import ai.bluefields.ppt2video.dto.video.AssetUploadRequest;
import ai.bluefields.ppt2video.dto.video.AssetUploadResult;
import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.service.video.provider.shotstack.ShotstackIngestMonitorService;
import ai.bluefields.ppt2video.service.video.provider.shotstack.ShotstackVideoProvider;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for uploading assets to Shotstack's ingestion endpoint. This enables preview
 * functionality in Shotstack Studio but comes with additional storage costs. The service uses
 * database persistence for tracking uploaded assets to avoid duplicates across restarts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShotstackAssetPublisher {

  private final ShotstackVideoProvider shotstackProvider;
  private final ShotstackIngestMonitorService ingestMonitor;
  private final AssetMetadataRepository assetMetadataRepository;

  @Value("${shotstack.assets.cache-duration-hours:24}")
  private int cacheDurationHours;

  /**
   * Uploads an asset to Shotstack if not already cached.
   *
   * @param sourceUrl The source URL of the asset (R2 presigned URL)
   * @param assetType The type of asset (image, video, audio)
   * @return The Shotstack asset URL
   */
  public String uploadAsset(String sourceUrl, String assetType) {
    return uploadAsset(sourceUrl, assetType, false);
  }

  /**
   * Uploads an asset to Shotstack with optional force re-upload.
   *
   * @param sourceUrl The source URL of the asset (R2 presigned URL)
   * @param assetType The type of asset (image, video, audio)
   * @param forceUpload If true, bypasses cache and forces re-upload
   * @return The Shotstack asset URL
   */
  @Transactional
  public String uploadAsset(String sourceUrl, String assetType, boolean forceUpload) {
    // Extract object key from URL for database lookup
    String objectKey = extractObjectKeyFromUrl(sourceUrl);
    if (objectKey == null) {
      log.warn("Could not extract object key from URL: {}", sourceUrl);
      // Proceed without caching
      return uploadToShotstack(sourceUrl, assetType, null);
    }

    // Check database for existing Shotstack URL unless force upload is requested
    if (!forceUpload) {
      Optional<AssetMetadata> existingAsset = assetMetadataRepository.findByObjectKey(objectKey);
      if (existingAsset.isPresent()) {
        AssetMetadata asset = existingAsset.get();
        if (asset.getShotstackUrl() != null && !isExpired(asset.getShotstackUploadedAt())) {
          log.debug("Using cached Shotstack URL for asset: {}", objectKey);
          return asset.getShotstackUrl();
        }
      }
    } else {
      log.info("Force upload requested, bypassing cache for: {}", objectKey);
    }

    // Find the asset metadata to update
    Optional<AssetMetadata> assetOpt = assetMetadataRepository.findByObjectKey(objectKey);
    AssetMetadata asset = assetOpt.orElse(null);

    // Upload to Shotstack and update database
    return uploadToShotstack(sourceUrl, assetType, asset);
  }

  /**
   * Uploads multiple assets to Shotstack.
   *
   * @param assets Map of asset name to source URL
   * @param assetType The type of assets being uploaded
   * @return Map of asset name to Shotstack URL
   */
  public Map<String, String> uploadAssets(Map<String, String> assets, String assetType) {
    return uploadAssets(assets, assetType, false);
  }

  /**
   * Uploads multiple assets to Shotstack with optional force re-upload.
   *
   * @param assets Map of asset name to source URL
   * @param assetType The type of assets being uploaded
   * @param forceUpload If true, bypasses cache and forces re-upload
   * @return Map of asset name to Shotstack URL
   */
  public Map<String, String> uploadAssets(
      Map<String, String> assets, String assetType, boolean forceUpload) {
    Map<String, String> shotstackUrls = new ConcurrentHashMap<>();

    // Upload each asset (could be parallelized if needed)
    for (Map.Entry<String, String> entry : assets.entrySet()) {
      String assetName = entry.getKey();
      String sourceUrl = entry.getValue();

      try {
        String shotstackUrl = uploadAsset(sourceUrl, assetType, forceUpload);
        shotstackUrls.put(assetName, shotstackUrl);
      } catch (Exception e) {
        log.error("Failed to upload asset: {}", assetName, e);
        // Continue with other assets
      }
    }

    return shotstackUrls;
  }

  /**
   * Forces re-upload of all assets for a presentation.
   *
   * @param presentationId The presentation ID
   * @return Number of assets refreshed
   */
  @Transactional
  public int refreshAllAssets(UUID presentationId) {
    log.info("Refreshing all Shotstack assets for presentation: {}", presentationId);

    // Clear all Shotstack URLs for this presentation's assets
    int updated = assetMetadataRepository.clearShotstackUrls(presentationId);

    log.info("Cleared {} Shotstack URLs for presentation: {}", updated, presentationId);
    return updated;
  }

  /** Clears all Shotstack URLs from the database (forces re-upload on next use). */
  @Transactional
  public void clearAllShotstackUrls() {
    int cleared = assetMetadataRepository.clearAllShotstackUrls();
    log.info("Cleared {} Shotstack URLs from database", cleared);
  }

  /**
   * Gets the count of cached Shotstack URLs in the database.
   *
   * @return Number of cached Shotstack URLs
   */
  public long getCacheSize() {
    return assetMetadataRepository.countByShotstackUrlNotNull();
  }

  /**
   * Checks if an asset's Shotstack upload has expired.
   *
   * @param uploadedAt The upload timestamp
   * @return true if the upload has expired
   */
  private boolean isExpired(LocalDateTime uploadedAt) {
    if (uploadedAt == null) {
      return true;
    }
    return uploadedAt.plusHours(cacheDurationHours).isBefore(LocalDateTime.now());
  }

  /**
   * Performs the actual upload to Shotstack.
   *
   * @param sourceUrl The source URL of the asset
   * @param assetType The type of asset
   * @param asset The AssetMetadata entity to update (can be null)
   * @return The Shotstack asset URL
   */
  private String uploadToShotstack(String sourceUrl, String assetType, AssetMetadata asset) {
    log.info("Uploading asset to Shotstack: {} (type: {})", sourceUrl, assetType);

    try {
      AssetUploadRequest request =
          AssetUploadRequest.builder()
              .sourceUrl(sourceUrl)
              .filename(generateFilename(assetType))
              .contentType(getContentType(assetType))
              .build();

      AssetUploadResult result = shotstackProvider.uploadAsset(request);

      // Check if the upload is queued (needs monitoring)
      if ("queued".equals(result.getStatus())) {
        log.info(
            "Asset upload queued, starting monitoring for source ID: {}",
            result.getProviderAssetId());

        // Start monitoring and wait for completion
        java.util.concurrent.CompletableFuture<String> future =
            ingestMonitor.startMonitoring(
                result.getProviderAssetId(),
                (status, resultOrError) -> {
                  if (status == ShotstackIngestMonitorService.SourceStatus.COMPLETED) {
                    log.info("Asset ingestion completed: {}", resultOrError);
                  } else {
                    log.error("Asset ingestion failed: {}", resultOrError);
                  }
                });

        // Wait for completion (with timeout)
        try {
          String assetUrl = future.get(60, java.util.concurrent.TimeUnit.SECONDS);

          // Update database with Shotstack URL
          if (asset != null) {
            asset.setShotstackUrl(assetUrl);
            asset.setShotstackAssetId(result.getProviderAssetId());
            asset.setShotstackUploadedAt(LocalDateTime.now());
            assetMetadataRepository.save(asset);
          }

          log.info(
              "Successfully ingested asset to Shotstack. ID: {}, URL: {}",
              result.getProviderAssetId(),
              assetUrl);

          return assetUrl;

        } catch (java.util.concurrent.TimeoutException e) {
          log.error("Timeout waiting for asset ingestion", e);
          throw new RuntimeException("Asset ingestion timeout", e);
        } catch (Exception e) {
          log.error("Failed waiting for asset ingestion", e);
          throw new RuntimeException("Asset ingestion failed: " + e.getMessage(), e);
        }

      } else {
        // Immediate upload (shouldn't happen with current implementation)
        String assetUrl = result.getProviderUrl();

        // Update database with Shotstack URL
        if (asset != null) {
          asset.setShotstackUrl(assetUrl);
          asset.setShotstackAssetId(result.getProviderAssetId());
          asset.setShotstackUploadedAt(LocalDateTime.now());
          assetMetadataRepository.save(asset);
        }

        log.info(
            "Successfully uploaded asset to Shotstack. ID: {}, URL: {}",
            result.getProviderAssetId(),
            assetUrl);

        return assetUrl;
      }

    } catch (Exception e) {
      log.error("Failed to upload asset to Shotstack", e);
      throw new RuntimeException("Asset upload failed: " + e.getMessage(), e);
    }
  }

  /**
   * Extracts the object key from an R2 presigned URL.
   *
   * @param url The presigned URL
   * @return The object key, or null if extraction fails
   */
  private String extractObjectKeyFromUrl(String url) {
    if (url == null || url.isEmpty()) {
      return null;
    }

    try {
      // Parse the URL
      URI uri = URI.create(url);
      String path = uri.getPath();

      // Remove leading slash and bucket name if present
      // URL format: https://domain.com/bucket-name/path/to/object.ext?presigned-params
      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      // The path should be: bucket-name/actual/object/key
      // We need to extract everything after the bucket name
      int firstSlash = path.indexOf('/');
      if (firstSlash > 0) {
        return path.substring(firstSlash + 1);
      }

      return path;
    } catch (Exception e) {
      log.warn("Failed to extract object key from URL: {}", url, e);
      return null;
    }
  }

  /**
   * Generates a unique filename for the asset.
   *
   * @param assetType The type of asset
   * @return A unique filename
   */
  private String generateFilename(String assetType) {
    String extension =
        switch (assetType.toLowerCase()) {
          case "image" -> ".jpg";
          case "video" -> ".mp4";
          case "audio" -> ".mp3";
          default -> "";
        };
    return "asset_" + UUID.randomUUID().toString() + extension;
  }

  /**
   * Gets the content type for the asset type.
   *
   * @param assetType The type of asset
   * @return The MIME content type
   */
  private String getContentType(String assetType) {
    return switch (assetType.toLowerCase()) {
      case "image" -> "image/jpeg";
      case "video" -> "video/mp4";
      case "audio" -> "audio/mpeg";
      default -> "application/octet-stream";
    };
  }

  /**
   * Gets a cached Shotstack URL for an asset by its UUID. Used by the composition service to
   * retrieve URLs that were uploaded during asset preparation.
   *
   * @param assetId The asset ID (IntroVideo ID, AvatarVideo ID, or AssetMetadata ID)
   * @return The Shotstack URL if cached, otherwise null
   */
  public String getCachedUrl(UUID assetId) {
    // Look in database for existing Shotstack URL
    Optional<AssetMetadata> assetOpt = assetMetadataRepository.findById(assetId);
    if (assetOpt.isPresent() && assetOpt.get().getShotstackUrl() != null) {
      // Check if the cached asset is still valid (not expired)
      LocalDateTime uploadedAt = assetOpt.get().getShotstackUploadedAt();
      if (uploadedAt != null
          && uploadedAt.plusHours(cacheDurationHours).isAfter(LocalDateTime.now())) {
        return assetOpt.get().getShotstackUrl();
      }
    }

    return null; // Not cached or expired
  }
}
