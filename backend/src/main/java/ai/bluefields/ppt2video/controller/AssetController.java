package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.service.R2AssetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for asset management operations. Provides endpoints for uploading, retrieving,
 * and managing assets in R2.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class AssetController {

  private final R2AssetService r2AssetService;

  /**
   * Publish an existing asset to R2 by type. Automatically locates the file based on type and IDs.
   *
   * @param assetType the type of asset to publish
   * @param presentationId the presentation ID
   * @param slideId the slide ID (optional, depending on asset type)
   * @return upload response with asset details
   */
  @PostMapping("/publish/{assetType}")
  public ResponseEntity<AssetDto> publishAsset(
      @PathVariable String assetType,
      @RequestParam UUID presentationId,
      @RequestParam(required = false) UUID slideId) {

    log.info(
        "Publishing {} asset for presentation: {}, slide: {}", assetType, presentationId, slideId);

    // Convert string to AssetType enum
    AssetType type;
    try {
      type = AssetType.valueOf(assetType.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }

    AssetDto asset = r2AssetService.publishExistingAsset(presentationId, slideId, type);
    return ResponseEntity.ok(asset);
  }

  /**
   * Initiate asset upload with manual file upload.
   *
   * @param request the upload request
   * @return upload response with pre-signed URL
   */
  @PostMapping("/upload")
  public ResponseEntity<AssetUploadResponse> initiateUpload(
      @Valid @RequestBody AssetUploadRequest request) {
    log.info("Initiating upload for asset type: {}", request.getAssetType());
    AssetUploadResponse response = r2AssetService.initiateUpload(request);
    return ResponseEntity.ok(response);
  }

  /**
   * Confirm upload completion.
   *
   * @param assetId the asset ID
   * @param body request body with file details
   * @return updated asset DTO
   */
  @PostMapping("/{assetId}/confirm-upload")
  public ResponseEntity<AssetDto> confirmUpload(
      @PathVariable UUID assetId, @RequestBody Map<String, Object> body) {
    Long fileSize =
        body.containsKey("fileSize") ? Long.valueOf(body.get("fileSize").toString()) : null;
    String checksum = body.containsKey("checksum") ? body.get("checksum").toString() : null;

    log.info("Confirming upload for asset: {}", assetId);
    AssetDto asset = r2AssetService.confirmUpload(assetId, fileSize, checksum);
    return ResponseEntity.ok(asset);
  }

  /**
   * Get asset by ID.
   *
   * @param assetId the asset ID
   * @return asset DTO with download URL
   */
  @GetMapping("/{assetId}")
  public ResponseEntity<AssetDto> getAsset(@PathVariable UUID assetId) {
    log.info("Getting asset: {}", assetId);
    AssetDto asset = r2AssetService.getAsset(assetId);
    return ResponseEntity.ok(asset);
  }

  /**
   * Delete asset.
   *
   * @param assetId the asset ID
   * @return no content response
   */
  @DeleteMapping("/{assetId}")
  public ResponseEntity<Void> deleteAsset(@PathVariable UUID assetId) {
    log.info("Deleting asset: {}", assetId);
    r2AssetService.deleteAsset(assetId);
    return ResponseEntity.noContent().build();
  }

  /**
   * List assets for a presentation.
   *
   * @param presentationId the presentation ID
   * @return list of asset DTOs
   */
  @GetMapping("/presentations/{presentationId}")
  public ResponseEntity<List<AssetDto>> listPresentationAssets(@PathVariable UUID presentationId) {
    log.info("Listing assets for presentation: {}", presentationId);
    List<AssetDto> assets = r2AssetService.listPresentationAssets(presentationId);
    return ResponseEntity.ok(assets);
  }

  /**
   * List assets for a slide.
   *
   * @param slideId the slide ID
   * @return list of asset DTOs
   */
  @GetMapping("/slides/{slideId}")
  public ResponseEntity<List<AssetDto>> listSlideAssets(@PathVariable UUID slideId) {
    log.info("Listing assets for slide: {}", slideId);
    List<AssetDto> assets = r2AssetService.listSlideAssets(slideId);
    return ResponseEntity.ok(assets);
  }
}
