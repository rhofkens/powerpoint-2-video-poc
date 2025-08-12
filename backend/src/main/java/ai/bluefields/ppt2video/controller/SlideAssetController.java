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
@RequestMapping("/api/slides")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SlideAssetController {

  private final R2AssetService r2AssetService;

  @GetMapping("/{slideId}/assets")
  public ResponseEntity<List<AssetDto>> listSlideAssets(@PathVariable UUID slideId) {
    log.info("Listing assets for slide: {}", slideId);
    List<AssetDto> assets = r2AssetService.listSlideAssets(slideId);
    return ResponseEntity.ok(assets);
  }
}
