package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.UploadStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for AssetMetadata entity operations. Provides database access methods for
 * asset metadata management.
 */
@Repository
public interface AssetMetadataRepository extends JpaRepository<AssetMetadata, UUID> {

  /**
   * Find all assets for a specific presentation.
   *
   * @param presentationId the presentation ID
   * @return list of asset metadata
   */
  List<AssetMetadata> findByPresentationId(UUID presentationId);

  /**
   * Find all assets for a specific slide.
   *
   * @param slideId the slide ID
   * @return list of asset metadata
   */
  List<AssetMetadata> findBySlideId(UUID slideId);

  /**
   * Find assets by presentation and type.
   *
   * @param presentationId the presentation ID
   * @param assetType the asset type
   * @return list of asset metadata
   */
  List<AssetMetadata> findByPresentationIdAndAssetType(UUID presentationId, AssetType assetType);

  /**
   * Find assets by slide and type.
   *
   * @param slideId the slide ID
   * @param assetType the asset type
   * @return list of asset metadata
   */
  List<AssetMetadata> findBySlideIdAndAssetType(UUID slideId, AssetType assetType);

  /**
   * Find asset by bucket and object key.
   *
   * @param bucketName the bucket name
   * @param objectKey the object key
   * @return optional asset metadata
   */
  Optional<AssetMetadata> findByBucketNameAndObjectKey(String bucketName, String objectKey);

  /**
   * Find assets by upload status.
   *
   * @param uploadStatus the upload status
   * @return list of asset metadata
   */
  List<AssetMetadata> findByUploadStatus(UploadStatus uploadStatus);

  /**
   * Find assets by presentation and upload status.
   *
   * @param presentationId the presentation ID
   * @param uploadStatus the upload status
   * @return list of asset metadata
   */
  List<AssetMetadata> findByPresentationIdAndUploadStatus(
      UUID presentationId, UploadStatus uploadStatus);

  /**
   * Count assets by presentation.
   *
   * @param presentationId the presentation ID
   * @return asset count
   */
  Long countByPresentationId(UUID presentationId);

  /**
   * Count assets by presentation and type.
   *
   * @param presentationId the presentation ID
   * @param assetType the asset type
   * @return asset count
   */
  Long countByPresentationIdAndAssetType(UUID presentationId, AssetType assetType);

  /**
   * Get total storage size for a presentation.
   *
   * @param presentationId the presentation ID
   * @return total size in bytes
   */
  @Query(
      "SELECT COALESCE(SUM(a.fileSize), 0) FROM AssetMetadata a WHERE a.presentation.id = :presentationId")
  Long getTotalStorageSizeByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Delete all assets for a presentation.
   *
   * @param presentationId the presentation ID
   */
  void deleteByPresentationId(UUID presentationId);

  /**
   * Delete all assets for a slide.
   *
   * @param slideId the slide ID
   */
  void deleteBySlideId(UUID slideId);
}
