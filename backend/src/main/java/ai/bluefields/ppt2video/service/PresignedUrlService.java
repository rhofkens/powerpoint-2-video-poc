package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.config.R2ClientFactory;
import ai.bluefields.ppt2video.config.R2Configuration;
import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.PresignedUrl;
import ai.bluefields.ppt2video.entity.UrlType;
import ai.bluefields.ppt2video.repository.PresignedUrlRepository;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Service for managing pre-signed URLs for R2 assets. Handles URL generation, validation, and
 * lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PresignedUrlService {

  private final PresignedUrlRepository presignedUrlRepository;
  private final R2ClientFactory r2ClientFactory;
  private final R2Configuration r2Configuration;

  /**
   * Generate a pre-signed upload URL for an asset.
   *
   * @param asset the asset metadata
   * @param contentType the content type
   * @return generated pre-signed URL
   */
  public PresignedUrl generateUploadUrl(AssetMetadata asset, String contentType) {
    S3Presigner presigner = r2ClientFactory.getPresigner();

    Duration expiration = r2Configuration.getUrls().getDefaultExpiration();

    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(asset.getBucketName())
            .key(asset.getObjectKey())
            .contentType(contentType)
            .build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .putObjectRequest(putRequest)
            .build();

    PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
    URL url = presignedRequest.url();

    LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);

    PresignedUrl presignedUrl =
        PresignedUrl.builder()
            .assetMetadata(asset)
            .urlType(UrlType.UPLOAD)
            .presignedUrl(url.toString())
            .expiresAt(expiresAt)
            .isActive(true)
            .build();

    presignedUrl = presignedUrlRepository.save(presignedUrl);
    log.info("Generated upload URL for asset: {}", asset.getId());
    return presignedUrl;
  }

  /**
   * Generate a pre-signed download URL for an asset.
   *
   * @param asset the asset metadata
   * @return generated pre-signed URL
   */
  public PresignedUrl generateDownloadUrl(AssetMetadata asset) {
    // Check for existing valid URL
    Optional<PresignedUrl> existingUrl =
        presignedUrlRepository.findActiveUrl(asset.getId(), UrlType.DOWNLOAD, LocalDateTime.now());

    if (existingUrl.isPresent()) {
      log.debug("Using existing download URL for asset: {}", asset.getId());
      return existingUrl.get();
    }

    S3Presigner presigner = r2ClientFactory.getPresigner();

    Duration expiration = r2Configuration.getUrls().getDefaultExpiration();

    GetObjectRequest getRequest =
        GetObjectRequest.builder().bucket(asset.getBucketName()).key(asset.getObjectKey()).build();

    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .getObjectRequest(getRequest)
            .build();

    PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
    URL url = presignedRequest.url();

    LocalDateTime expiresAt = LocalDateTime.now().plus(expiration);

    PresignedUrl presignedUrl =
        PresignedUrl.builder()
            .assetMetadata(asset)
            .urlType(UrlType.DOWNLOAD)
            .presignedUrl(url.toString())
            .expiresAt(expiresAt)
            .isActive(true)
            .build();

    presignedUrl = presignedUrlRepository.save(presignedUrl);
    log.info("Generated download URL for asset: {}", asset.getId());
    return presignedUrl;
  }

  /**
   * Re-sign an expired URL.
   *
   * @param assetId the asset ID
   * @param urlType the URL type
   * @return new pre-signed URL
   */
  public PresignedUrl resignUrl(UUID assetId, UrlType urlType, AssetMetadata asset) {
    // Deactivate old URLs
    presignedUrlRepository.deactivateUrlsForAsset(assetId);

    if (urlType == UrlType.UPLOAD) {
      return generateUploadUrl(asset, asset.getContentType());
    } else {
      return generateDownloadUrl(asset);
    }
  }

  /**
   * Get active URL for an asset.
   *
   * @param assetId the asset ID
   * @param urlType the URL type
   * @return optional pre-signed URL
   */
  @Transactional(readOnly = true)
  public Optional<PresignedUrl> getActiveUrl(UUID assetId, UrlType urlType) {
    return presignedUrlRepository.findActiveUrl(assetId, urlType, LocalDateTime.now());
  }

  /**
   * Get all URLs for an asset.
   *
   * @param assetId the asset ID
   * @return list of pre-signed URLs
   */
  @Transactional(readOnly = true)
  public List<PresignedUrl> getUrlsForAsset(UUID assetId) {
    return presignedUrlRepository.findByAssetMetadataId(assetId);
  }

  /**
   * Track URL access.
   *
   * @param urlId the URL ID
   */
  public void trackAccess(UUID urlId) {
    presignedUrlRepository.incrementAccessCount(urlId);
    log.debug("Tracked access for URL: {}", urlId);
  }

  /**
   * Deactivate expired URLs.
   *
   * @return number of deactivated URLs
   */
  public int deactivateExpiredUrls() {
    int count = presignedUrlRepository.deactivateExpiredUrls(LocalDateTime.now());
    if (count > 0) {
      log.info("Deactivated {} expired URLs", count);
    }
    return count;
  }

  /**
   * Delete all URLs for an asset.
   *
   * @param assetId the asset ID
   */
  public void deleteUrlsForAsset(UUID assetId) {
    presignedUrlRepository.deleteByAssetMetadataId(assetId);
    log.info("Deleted all URLs for asset: {}", assetId);
  }

  /**
   * Get total access count for an asset.
   *
   * @param assetId the asset ID
   * @return total access count
   */
  @Transactional(readOnly = true)
  public Long getTotalAccessCount(UUID assetId) {
    return presignedUrlRepository.getTotalAccessCount(assetId);
  }
}
