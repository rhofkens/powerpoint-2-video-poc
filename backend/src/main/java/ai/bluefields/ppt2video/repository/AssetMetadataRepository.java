package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.UploadStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
  @Query("SELECT a FROM AssetMetadata a WHERE a.presentation.id = :presentationId")
  List<AssetMetadata> findByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Find all assets for a specific slide.
   *
   * @param slideId the slide ID
   * @return list of asset metadata
   */
  @Query("SELECT a FROM AssetMetadata a WHERE a.slide.id = :slideId")
  List<AssetMetadata> findBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find assets by presentation and type.
   *
   * @param presentationId the presentation ID
   * @param assetType the asset type
   * @return list of asset metadata
   */
  @Query(
      "SELECT a FROM AssetMetadata a WHERE a.presentation.id = :presentationId AND a.assetType = :assetType")
  List<AssetMetadata> findByPresentationIdAndAssetType(
      @Param("presentationId") UUID presentationId, @Param("assetType") AssetType assetType);

  /**
   * Find assets by slide and type.
   *
   * @param slideId the slide ID
   * @param assetType the asset type
   * @return list of asset metadata
   */
  @Query("SELECT a FROM AssetMetadata a WHERE a.slide.id = :slideId AND a.assetType = :assetType")
  List<AssetMetadata> findBySlideIdAndAssetType(
      @Param("slideId") UUID slideId, @Param("assetType") AssetType assetType);

  /**
   * Find asset by bucket and object key.
   *
   * @param bucketName the bucket name
   * @param objectKey the object key
   * @return optional asset metadata
   */
  Optional<AssetMetadata> findByBucketNameAndObjectKey(String bucketName, String objectKey);

  /**
   * Find asset by object key.
   *
   * @param objectKey the object key
   * @return optional asset metadata
   */
  Optional<AssetMetadata> findByObjectKey(String objectKey);

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
  @Query(
      "SELECT a FROM AssetMetadata a WHERE a.presentation.id = :presentationId AND a.uploadStatus = :uploadStatus")
  List<AssetMetadata> findByPresentationIdAndUploadStatus(
      @Param("presentationId") UUID presentationId,
      @Param("uploadStatus") UploadStatus uploadStatus);

  /**
   * Count assets by presentation.
   *
   * @param presentationId the presentation ID
   * @return asset count
   */
  @Query("SELECT COUNT(a) FROM AssetMetadata a WHERE a.presentation.id = :presentationId")
  Long countByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Count assets by presentation and type.
   *
   * @param presentationId the presentation ID
   * @param assetType the asset type
   * @return asset count
   */
  @Query(
      "SELECT COUNT(a) FROM AssetMetadata a WHERE a.presentation.id = :presentationId AND a.assetType = :assetType")
  Long countByPresentationIdAndAssetType(
      @Param("presentationId") UUID presentationId, @Param("assetType") AssetType assetType);

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
  @Modifying
  @Query("DELETE FROM AssetMetadata a WHERE a.presentation.id = :presentationId")
  void deleteByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Delete all assets for a slide.
   *
   * @param slideId the slide ID
   */
  @Modifying
  @Query("DELETE FROM AssetMetadata a WHERE a.slide.id = :slideId")
  void deleteBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find assets for multiple slides. Used for batch operations like preflight checks.
   *
   * @param slideIds the list of slide IDs
   * @return list of asset metadata
   */
  @Query("SELECT a FROM AssetMetadata a WHERE a.slide.id IN :slideIds")
  List<AssetMetadata> findBySlideIdIn(@Param("slideIds") List<UUID> slideIds);

  /**
   * Count assets with Shotstack URL.
   *
   * @return count of assets with Shotstack URL
   */
  @Query("SELECT COUNT(a) FROM AssetMetadata a WHERE a.shotstackUrl IS NOT NULL")
  Long countByShotstackUrlNotNull();

  /**
   * Clear Shotstack URLs for a presentation's assets.
   *
   * @param presentationId the presentation ID
   * @return number of assets updated
   */
  @Modifying
  @Query(
      "UPDATE AssetMetadata a SET a.shotstackUrl = NULL, a.shotstackAssetId = NULL, a.shotstackUploadedAt = NULL WHERE a.presentation.id = :presentationId AND a.shotstackUrl IS NOT NULL")
  int clearShotstackUrls(@Param("presentationId") UUID presentationId);

  /**
   * Clear all Shotstack URLs.
   *
   * @return number of assets updated
   */
  @Modifying
  @Query(
      "UPDATE AssetMetadata a SET a.shotstackUrl = NULL, a.shotstackAssetId = NULL, a.shotstackUploadedAt = NULL WHERE a.shotstackUrl IS NOT NULL")
  int clearAllShotstackUrls();
}
