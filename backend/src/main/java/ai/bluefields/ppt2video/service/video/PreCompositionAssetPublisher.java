package ai.bluefields.ppt2video.service.video;

import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarVideo;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.repository.IntroVideoRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for preparing all assets before video composition. Ensures R2 URLs are fresh
 * and uploads to Shotstack if needed, completely separated from composition building logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreCompositionAssetPublisher {

  private final AssetMetadataRepository assetMetadataRepository;
  private final AvatarVideoRepository avatarVideoRepository;
  private final IntroVideoRepository introVideoRepository;
  private final R2AssetService r2AssetService;
  private final ShotstackAssetPublisher shotstackAssetPublisher;

  @Value("${shotstack.assets.mode:r2-direct}")
  private String assetMode;

  /**
   * Ensures all assets for a presentation are ready for composition. Step 1: Refreshes all R2 URLs
   * (needed for both modes) Step 2: Uploads to Shotstack if in shotstack-upload mode
   */
  public void ensureAllAssetsPublished(UUID presentationId) {
    log.info("Preparing assets for presentation: {} in {} mode", presentationId, assetMode);

    // Step 1: Discover all assets for this presentation
    AssetDiscoveryResult discovery = discoverAllAssets(presentationId);
    if (discovery.isEmpty()) {
      log.warn("No assets found for presentation: {}", presentationId);
      return;
    }

    log.info(
        "Discovered {} assets: {} intro videos, {} slide images, {} avatar videos",
        discovery.getTotalCount(),
        discovery.introVideos.size(),
        discovery.slideImages.size(),
        discovery.avatarVideos.size());

    // Step 2: ALWAYS refresh R2 URLs first (needed for both modes)
    refreshAllR2Urls(discovery);

    // Step 3: If in Shotstack upload mode, upload to Shotstack
    if ("shotstack-upload".equalsIgnoreCase(assetMode)) {
      uploadAssetsToShotstack(discovery);
    }

    log.info("Asset preparation complete for presentation: {}", presentationId);
  }

  /** Discovers all assets that need to be prepared for a presentation. */
  private AssetDiscoveryResult discoverAllAssets(UUID presentationId) {
    AssetDiscoveryResult result = new AssetDiscoveryResult();

    // Fetch intro video - get latest completed intro video with AssetMetadata
    List<IntroVideo> completedIntros =
        introVideoRepository.findByStatus(
            ai.bluefields.ppt2video.entity.AvatarGenerationStatusType.COMPLETED);
    completedIntros.stream()
        .filter(iv -> iv.getPresentation().getId().equals(presentationId))
        .filter(iv -> iv.getR2Asset() != null)
        .findFirst()
        .ifPresent(result.introVideos::add);

    // Fetch all slide images
    List<AssetMetadata> slideImages =
        assetMetadataRepository.findByPresentationIdAndAssetType(
            presentationId, AssetType.SLIDE_IMAGE);
    result.slideImages.addAll(slideImages);

    // Fetch all completed avatar videos with AssetMetadata
    List<AvatarVideo> allAvatarVideos =
        avatarVideoRepository.findByPresentationIdOrderByCreatedAtDesc(presentationId);
    allAvatarVideos.stream()
        .filter(av -> av.getStatus() == AvatarGenerationStatusType.COMPLETED)
        .filter(av -> av.getR2Asset() != null)
        .forEach(result.avatarVideos::add);

    log.debug(
        "Asset discovery for presentation {}: {} intro videos, {} slide images, {} avatar videos",
        presentationId,
        result.introVideos.size(),
        result.slideImages.size(),
        result.avatarVideos.size());

    return result;
  }

  /**
   * Refreshes all R2 presigned URLs to ensure they're valid. This is required for BOTH modes: - R2
   * Direct: URLs must be valid for Shotstack rendering - Shotstack Upload: URLs must be valid for
   * uploading to Shotstack
   */
  private void refreshAllR2Urls(AssetDiscoveryResult discovery) {
    log.info("Refreshing R2 URLs for all assets");

    int refreshedCount = 0;

    // Refresh intro video URLs - always regenerate for freshness
    for (IntroVideo introVideo : discovery.introVideos) {
      try {
        AssetMetadata assetMetadata = introVideo.getR2Asset();
        if (assetMetadata != null) {
          String newUrl = r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
          refreshedCount++;
          log.debug("Refreshed intro video URL for: {}", introVideo.getId());
        } else {
          log.warn("Intro video {} has no R2 asset metadata", introVideo.getId());
        }
      } catch (Exception e) {
        log.error("Failed to refresh intro video URL for: {}", introVideo.getId(), e);
      }
    }

    // Refresh slide image URLs
    for (AssetMetadata slideImage : discovery.slideImages) {
      // For AssetMetadata, we need to check via R2 service and don't directly access presigned URL
      try {
        // Always regenerate URL for slide images to ensure freshness
        String newUrl = r2AssetService.regeneratePresignedUrl(slideImage.getId());
        // Note: AssetMetadata doesn't store presigned URLs directly, R2AssetService manages them
        refreshedCount++;
        log.debug("Refreshed slide image URL for: {}", slideImage.getId());
      } catch (Exception e) {
        log.error("Failed to refresh slide image URL for: {}", slideImage.getId(), e);
      }
    }

    // Refresh avatar video URLs - always regenerate for freshness
    for (AvatarVideo avatarVideo : discovery.avatarVideos) {
      try {
        AssetMetadata assetMetadata = avatarVideo.getR2Asset();
        if (assetMetadata != null) {
          String newUrl = r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
          refreshedCount++;
          log.debug("Refreshed avatar video URL for: {}", avatarVideo.getId());
        } else {
          log.warn("Avatar video {} has no R2 asset metadata", avatarVideo.getId());
        }
      } catch (Exception e) {
        log.error("Failed to refresh avatar video URL for: {}", avatarVideo.getId(), e);
      }
    }

    log.info("R2 URL refresh complete: {} URLs refreshed", refreshedCount);
  }

  /** Uploads assets to Shotstack using fresh R2 URLs. */
  private void uploadAssetsToShotstack(AssetDiscoveryResult discovery) {
    log.info("Uploading assets to Shotstack for preview capability");

    int uploadedCount = 0;

    // Upload intro videos
    for (IntroVideo introVideo : discovery.introVideos) {
      AssetMetadata assetMetadata = introVideo.getR2Asset();
      if (assetMetadata != null) {
        try {
          // Get fresh R2 URL for upload
          String r2Url = r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
          String shotstackUrl = shotstackAssetPublisher.uploadAsset(r2Url, "video");

          // Store Shotstack URL in AssetMetadata
          assetMetadata.setShotstackUrl(shotstackUrl);
          assetMetadata.setShotstackUploadedAt(java.time.LocalDateTime.now());
          assetMetadataRepository.save(assetMetadata);

          log.debug(
              "Uploaded intro video to Shotstack: {} -> {}", introVideo.getId(), shotstackUrl);
          uploadedCount++;
        } catch (Exception e) {
          log.error("Failed to upload intro video to Shotstack: {}", introVideo.getId(), e);
        }
      }
    }

    // Upload slide images
    for (AssetMetadata slideImage : discovery.slideImages) {
      try {
        // Get fresh URL for upload to Shotstack
        String slideImageUrl = r2AssetService.regeneratePresignedUrl(slideImage.getId());
        String shotstackUrl = shotstackAssetPublisher.uploadAsset(slideImageUrl, "image");

        // Store Shotstack URL in AssetMetadata
        slideImage.setShotstackUrl(shotstackUrl);
        slideImage.setShotstackUploadedAt(java.time.LocalDateTime.now());
        assetMetadataRepository.save(slideImage);

        log.debug("Uploaded slide image to Shotstack: {} -> {}", slideImage.getId(), shotstackUrl);
        uploadedCount++;
      } catch (Exception e) {
        log.error("Failed to upload slide image to Shotstack: {}", slideImage.getId(), e);
      }
    }

    // Upload avatar videos
    for (AvatarVideo avatarVideo : discovery.avatarVideos) {
      AssetMetadata assetMetadata = avatarVideo.getR2Asset();
      if (assetMetadata != null) {
        try {
          // Get fresh R2 URL for upload
          String r2Url = r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
          String shotstackUrl = shotstackAssetPublisher.uploadAsset(r2Url, "video");

          // Store Shotstack URL in AssetMetadata
          assetMetadata.setShotstackUrl(shotstackUrl);
          assetMetadata.setShotstackUploadedAt(java.time.LocalDateTime.now());
          assetMetadataRepository.save(assetMetadata);

          log.debug(
              "Uploaded avatar video to Shotstack: {} -> {}", avatarVideo.getId(), shotstackUrl);
          uploadedCount++;
        } catch (Exception e) {
          log.error("Failed to upload avatar video to Shotstack: {}", avatarVideo.getId(), e);
        }
      }
    }

    log.info("Shotstack upload complete: {} assets uploaded", uploadedCount);
  }

  /** Helper class to hold discovered assets. */
  private static class AssetDiscoveryResult {
    final List<IntroVideo> introVideos = new ArrayList<>();
    final List<AssetMetadata> slideImages = new ArrayList<>();
    final List<AvatarVideo> avatarVideos = new ArrayList<>();

    boolean isEmpty() {
      return introVideos.isEmpty() && slideImages.isEmpty() && avatarVideos.isEmpty();
    }

    int getTotalCount() {
      return introVideos.size() + slideImages.size() + avatarVideos.size();
    }
  }
}
