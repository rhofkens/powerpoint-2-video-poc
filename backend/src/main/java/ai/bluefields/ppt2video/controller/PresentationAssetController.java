package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.AssetDto;
import ai.bluefields.ppt2video.service.R2AssetService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/presentations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PresentationAssetController {

  private final R2AssetService r2AssetService;

  @GetMapping("/{presentationId}/assets")
  public ResponseEntity<List<AssetDto>> listPresentationAssets(@PathVariable UUID presentationId) {
    log.info("Listing assets for presentation: {}", presentationId);
    List<AssetDto> assets = r2AssetService.listPresentationAssets(presentationId);
    return ResponseEntity.ok(assets);
  }
}
