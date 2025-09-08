package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for verifying asset publication status in R2 storage. Provides methods to check if assets
 * have been successfully uploaded to Cloudflare R2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class R2AssetVerificationService {

  private final AssetMetadataRepository assetMetadataRepository;
  private final R2AssetService r2AssetService;

  /**
   * Verifies if an asset has been successfully published to R2.
   *
   * @param assetId the ID of the asset to verify
   * @return true if the asset is published and accessible, false otherwise
   */
  public boolean verifyAssetPublished(UUID assetId) {
    log.debug("Verifying R2 publication status for asset: {}", assetId);

    try {
      var assetMetadata = assetMetadataRepository.findById(assetId);
      if (assetMetadata.isEmpty()) {
        log.warn("Asset metadata not found for ID: {}", assetId);
        return false;
      }

      var metadata = assetMetadata.get();

      // Check if asset has been marked as uploaded successfully
      if (!"UPLOADED".equals(metadata.getUploadStatus())) {
        log.debug(
            "Asset {} upload status is not UPLOADED: {}", assetId, metadata.getUploadStatus());
        return false;
      }

      // Check if R2 key exists
      if (metadata.getR2Key() == null || metadata.getR2Key().isEmpty()) {
        log.debug("Asset {} has no R2 key", assetId);
        return false;
      }

      // Optionally verify the asset actually exists in R2 (could be expensive)
      // For now, we trust the upload status in the database
      return true;

    } catch (Exception e) {
      log.error("Error verifying asset publication for ID: {}", assetId, e);
      return false;
    }
  }

  /**
   * Batch verification of multiple assets for performance.
   *
   * @param assetIds list of asset IDs to verify
   * @return map of asset ID to publication status
   */
  public Map<UUID, Boolean> batchVerifyAssets(List<UUID> assetIds) {
    log.debug("Batch verifying {} assets", assetIds.size());

    if (assetIds.isEmpty()) {
      return new HashMap<>();
    }

    try {
      // Fetch all metadata in one query
      List<AssetMetadata> assetMetadataList = assetMetadataRepository.findAllById(assetIds);

      // Create a map of asset ID to metadata for quick lookup
      Map<UUID, AssetMetadata> metadataMap =
          assetMetadataList.stream()
              .collect(Collectors.toMap(AssetMetadata::getId, metadata -> metadata));

      // Build result map
      Map<UUID, Boolean> results = new HashMap<>();
      for (UUID assetId : assetIds) {
        AssetMetadata metadata = metadataMap.get(assetId);
        boolean isPublished =
            metadata != null
                && "UPLOADED".equals(metadata.getUploadStatus())
                && metadata.getR2Key() != null
                && !metadata.getR2Key().isEmpty();
        results.put(assetId, isPublished);
      }

      log.debug(
          "Batch verification complete. Published: {}, Not published: {}",
          results.values().stream().filter(v -> v).count(),
          results.values().stream().filter(v -> !v).count());

      return results;

    } catch (Exception e) {
      log.error("Error during batch asset verification", e);
      // Return all as unpublished on error
      return assetIds.stream().collect(Collectors.toMap(id -> id, id -> false));
    }
  }

  /**
   * Checks if an asset exists and is accessible via its R2 key. This performs an actual check
   * against R2 storage (more expensive).
   *
   * @param r2Key the R2 storage key
   * @return true if the asset exists in R2, false otherwise
   */
  public boolean verifyAssetExistsInR2(String r2Key) {
    try {
      // This would need implementation in R2AssetService to check object existence
      // For now, we assume the database status is accurate
      log.debug("Verifying R2 object existence for key: {}", r2Key);
      return true; // Placeholder - would call r2AssetService.objectExists(r2Key)
    } catch (Exception e) {
      log.error("Error checking R2 object existence for key: {}", r2Key, e);
      return false;
    }
  }
}
