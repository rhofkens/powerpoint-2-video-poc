package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.PresignedUrl;
import ai.bluefields.ppt2video.entity.UrlType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for PresignedUrl entity operations. Manages pre-signed URL lifecycle and
 * access tracking.
 */
@Repository
public interface PresignedUrlRepository extends JpaRepository<PresignedUrl, UUID> {

  /**
   * Find all URLs for a specific asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @return list of presigned URLs
   */
  List<PresignedUrl> findByAssetMetadataId(UUID assetMetadataId);

  /**
   * Find URLs by asset and type.
   *
   * @param assetMetadataId the asset metadata ID
   * @param urlType the URL type
   * @return list of presigned URLs
   */
  List<PresignedUrl> findByAssetMetadataIdAndUrlType(UUID assetMetadataId, UrlType urlType);

  /**
   * Find active URLs for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @param isActive active status
   * @return list of presigned URLs
   */
  List<PresignedUrl> findByAssetMetadataIdAndIsActive(UUID assetMetadataId, Boolean isActive);

  /**
   * Find active, non-expired URL for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @param urlType the URL type
   * @param currentTime current time for expiration check
   * @return optional presigned URL
   */
  @Query(
      "SELECT p FROM PresignedUrl p WHERE p.assetMetadata.id = :assetMetadataId "
          + "AND p.urlType = :urlType AND p.isActive = true "
          + "AND p.expiresAt > :currentTime ORDER BY p.expiresAt DESC")
  Optional<PresignedUrl> findActiveUrl(
      @Param("assetMetadataId") UUID assetMetadataId,
      @Param("urlType") UrlType urlType,
      @Param("currentTime") LocalDateTime currentTime);

  /**
   * Find expired URLs.
   *
   * @param expirationTime the expiration threshold
   * @return list of expired URLs
   */
  @Query("SELECT p FROM PresignedUrl p WHERE p.expiresAt < :expirationTime AND p.isActive = true")
  List<PresignedUrl> findExpiredUrls(@Param("expirationTime") LocalDateTime expirationTime);

  /**
   * Deactivate expired URLs.
   *
   * @param expirationTime the expiration threshold
   * @return number of deactivated URLs
   */
  @Modifying
  @Query("UPDATE PresignedUrl p SET p.isActive = false WHERE p.expiresAt < :expirationTime")
  int deactivateExpiredUrls(@Param("expirationTime") LocalDateTime expirationTime);

  /**
   * Increment access count for a URL.
   *
   * @param urlId the URL ID
   */
  @Modifying
  @Query("UPDATE PresignedUrl p SET p.accessCount = p.accessCount + 1 WHERE p.id = :urlId")
  void incrementAccessCount(@Param("urlId") UUID urlId);

  /**
   * Deactivate all URLs for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @return number of deactivated URLs
   */
  @Modifying
  @Query("UPDATE PresignedUrl p SET p.isActive = false WHERE p.assetMetadata.id = :assetMetadataId")
  int deactivateUrlsForAsset(@Param("assetMetadataId") UUID assetMetadataId);

  /**
   * Delete all URLs for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   */
  void deleteByAssetMetadataId(UUID assetMetadataId);

  /**
   * Count URLs by type for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @param urlType the URL type
   * @return URL count
   */
  Long countByAssetMetadataIdAndUrlType(UUID assetMetadataId, UrlType urlType);

  /**
   * Get total access count for an asset.
   *
   * @param assetMetadataId the asset metadata ID
   * @return total access count
   */
  @Query(
      "SELECT COALESCE(SUM(p.accessCount), 0) FROM PresignedUrl p WHERE p.assetMetadata.id = :assetMetadataId")
  Long getTotalAccessCount(@Param("assetMetadataId") UUID assetMetadataId);
}
