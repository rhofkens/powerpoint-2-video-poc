package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.PresignedUrlDto;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for pre-signed URL management. Provides endpoints for URL generation and
 * lifecycle management.
 */
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class PresignedUrlController {

  private final R2AssetService r2AssetService;

  /**
   * Generate new pre-signed URL for an asset.
   *
   * @param assetId the asset ID
   * @return new pre-signed URL DTO
   */
  @PostMapping("/{assetId}/presigned-url")
  public ResponseEntity<PresignedUrlDto> generatePresignedUrl(@PathVariable UUID assetId) {
    log.info("Generating new pre-signed URL for asset: {}", assetId);
    PresignedUrlDto url = r2AssetService.resignUrl(assetId);
    return ResponseEntity.ok(url);
  }

  /**
   * Re-sign expired URL for an asset.
   *
   * @param assetId the asset ID
   * @return new pre-signed URL DTO
   */
  @PostMapping("/{assetId}/resign")
  public ResponseEntity<PresignedUrlDto> resignUrl(@PathVariable UUID assetId) {
    log.info("Re-signing URL for asset: {}", assetId);
    PresignedUrlDto url = r2AssetService.resignUrl(assetId);
    return ResponseEntity.ok(url);
  }

  /**
   * Get all URLs for an asset.
   *
   * @param assetId the asset ID
   * @return list of pre-signed URL DTOs
   */
  @GetMapping("/{assetId}/urls")
  public ResponseEntity<List<PresignedUrlDto>> getAssetUrls(@PathVariable UUID assetId) {
    log.info("Getting all URLs for asset: {}", assetId);
    List<PresignedUrlDto> urls = r2AssetService.getAssetUrls(assetId);
    return ResponseEntity.ok(urls);
  }
}
