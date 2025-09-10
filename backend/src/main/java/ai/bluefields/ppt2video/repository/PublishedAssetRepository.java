package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.PublishStatus;
import ai.bluefields.ppt2video.entity.PublishedAsset;
import ai.bluefields.ppt2video.entity.VideoProviderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PublishedAssetRepository extends JpaRepository<PublishedAsset, UUID> {

  List<PublishedAsset> findByPresentationId(UUID presentationId);

  List<PublishedAsset> findByVideoStoryId(UUID videoStoryId);

  List<PublishedAsset> findByAssetMetadataId(UUID assetMetadataId);

  @Query(
      "SELECT pa FROM PublishedAsset pa WHERE pa.presentation.id = :presentationId "
          + "AND pa.deletedAt IS NULL")
  List<PublishedAsset> findByPresentationIdAndDeletedAtIsNull(
      @Param("presentationId") UUID presentationId);

  @Query(
      "SELECT pa FROM PublishedAsset pa WHERE pa.scheduledDeletionAt < :cutoff "
          + "AND pa.deletedAt IS NULL")
  List<PublishedAsset> findByScheduledDeletionAtBeforeAndDeletedAtIsNull(
      @Param("cutoff") LocalDateTime cutoff);

  @Query(
      "SELECT pa FROM PublishedAsset pa WHERE pa.provider = :provider "
          + "AND pa.providerAssetId = :providerAssetId")
  Optional<PublishedAsset> findByProviderAndProviderAssetId(
      @Param("provider") VideoProviderType provider,
      @Param("providerAssetId") String providerAssetId);

  @Query(
      "SELECT pa FROM PublishedAsset pa WHERE pa.publishStatus = :status "
          + "AND pa.provider = :provider")
  List<PublishedAsset> findByPublishStatusAndProvider(
      @Param("status") PublishStatus status, @Param("provider") VideoProviderType provider);

  @Query(
      "SELECT COUNT(pa) FROM PublishedAsset pa WHERE pa.presentation.id = :presentationId "
          + "AND pa.deletedAt IS NULL")
  long countActiveAssetsByPresentation(@Param("presentationId") UUID presentationId);

  @Query(
      "SELECT pa FROM PublishedAsset pa WHERE pa.expiresAt < :now "
          + "AND pa.deletedAt IS NULL AND pa.publishStatus = 'PUBLISHED'")
  List<PublishedAsset> findExpiredAssets(@Param("now") LocalDateTime now);
}
