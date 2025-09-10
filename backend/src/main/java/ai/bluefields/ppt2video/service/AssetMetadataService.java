package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing asset metadata in the database. Handles CRUD operations for asset records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AssetMetadataService {

  private final AssetMetadataRepository assetMetadataRepository;
  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;

  /**
   * Create a new asset metadata record.
   *
   * @param presentationId the presentation ID
   * @param slideId the slide ID (optional)
   * @param assetType the asset type
   * @param bucketName the bucket name
   * @param objectKey the object key
   * @param fileName the file name
   * @return created asset metadata
   */
  public AssetMetadata createAsset(
      UUID presentationId,
      UUID slideId,
      AssetType assetType,
      String bucketName,
      String objectKey,
      String fileName) {

    Presentation presentation =
        presentationRepository
            .findById(presentationId)
            .orElseThrow(
                () -> new ProcessingException("Presentation not found with ID: " + presentationId));

    Slide slide = null;
    if (slideId != null) {
      slide =
          slideRepository
              .findById(slideId)
              .orElseThrow(() -> new ProcessingException("Slide not found with ID: " + slideId));
    }

    AssetMetadata asset =
        AssetMetadata.builder()
            .presentation(presentation)
            .slide(slide)
            .assetType(assetType)
            .bucketName(bucketName)
            .objectKey(objectKey)
            .fileName(fileName)
            .uploadStatus(UploadStatus.PENDING)
            .build();

    asset = assetMetadataRepository.save(asset);
    log.info("Created asset metadata: {}", asset.getId());
    return asset;
  }

  /**
   * Update asset upload status.
   *
   * @param assetId the asset ID
   * @param status the new status
   * @param errorMessage error message if failed
   * @return updated asset metadata
   */
  public AssetMetadata updateUploadStatus(UUID assetId, UploadStatus status, String errorMessage) {
    AssetMetadata asset =
        assetMetadataRepository
            .findById(assetId)
            .orElseThrow(() -> new ProcessingException("Asset not found with ID: " + assetId));

    asset.setUploadStatus(status);
    if (errorMessage != null) {
      asset.setErrorMessage(errorMessage);
    }

    asset = assetMetadataRepository.save(asset);
    log.info("Updated asset {} status to: {}", assetId, status);
    return asset;
  }

  /**
   * Update asset metadata after successful upload.
   *
   * @param assetId the asset ID
   * @param fileSize the file size
   * @param contentType the content type
   * @param checksum the file checksum
   * @return updated asset metadata
   */
  public AssetMetadata updateAssetAfterUpload(
      UUID assetId, Long fileSize, String contentType, String checksum) {
    AssetMetadata asset =
        assetMetadataRepository
            .findById(assetId)
            .orElseThrow(() -> new ProcessingException("Asset not found with ID: " + assetId));

    asset.setFileSize(fileSize);
    asset.setContentType(contentType);
    asset.setChecksum(checksum);
    asset.setUploadStatus(UploadStatus.COMPLETED);

    asset = assetMetadataRepository.save(asset);
    log.info("Updated asset {} after successful upload", assetId);
    return asset;
  }

  /**
   * Get asset by ID.
   *
   * @param assetId the asset ID
   * @return optional asset metadata
   */
  @Transactional(readOnly = true)
  public Optional<AssetMetadata> getAsset(UUID assetId) {
    return assetMetadataRepository.findById(assetId);
  }

  /**
   * Find asset by object key.
   *
   * @param objectKey the R2 object key
   * @return optional asset metadata
   */
  @Transactional(readOnly = true)
  public Optional<AssetMetadata> findByObjectKey(String objectKey) {
    return assetMetadataRepository.findByObjectKey(objectKey);
  }

  /**
   * Get assets by presentation.
   *
   * @param presentationId the presentation ID
   * @return list of assets
   */
  @Transactional(readOnly = true)
  public List<AssetMetadata> getAssetsByPresentation(UUID presentationId) {
    return assetMetadataRepository.findByPresentationId(presentationId);
  }

  /**
   * Get assets by slide.
   *
   * @param slideId the slide ID
   * @return list of assets
   */
  @Transactional(readOnly = true)
  public List<AssetMetadata> getAssetsBySlide(UUID slideId) {
    return assetMetadataRepository.findBySlideId(slideId);
  }

  /**
   * Get assets by presentation and type.
   *
   * @param presentationId the presentation ID
   * @param assetType the asset type
   * @return list of assets
   */
  @Transactional(readOnly = true)
  public List<AssetMetadata> getAssetsByPresentationAndType(
      UUID presentationId, AssetType assetType) {
    return assetMetadataRepository.findByPresentationIdAndAssetType(presentationId, assetType);
  }

  /**
   * Delete asset metadata.
   *
   * @param assetId the asset ID
   */
  public void deleteAsset(UUID assetId) {
    assetMetadataRepository.deleteById(assetId);
    log.info("Deleted asset metadata: {}", assetId);
  }

  /**
   * Delete all assets for a presentation.
   *
   * @param presentationId the presentation ID
   */
  public void deleteAssetsByPresentation(UUID presentationId) {
    assetMetadataRepository.deleteByPresentationId(presentationId);
    log.info("Deleted all assets for presentation: {}", presentationId);
  }

  /**
   * Get total storage size for a presentation.
   *
   * @param presentationId the presentation ID
   * @return total size in bytes
   */
  @Transactional(readOnly = true)
  public Long getTotalStorageSize(UUID presentationId) {
    return assetMetadataRepository.getTotalStorageSizeByPresentationId(presentationId);
  }
}
