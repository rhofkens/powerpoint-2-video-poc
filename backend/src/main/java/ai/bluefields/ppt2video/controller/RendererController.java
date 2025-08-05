package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.service.rendering.RenderingStrategyFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for renderer-related endpoints. Provides information about available slide renderers
 * and their status.
 */
@RestController
@RequestMapping("/api/renderers")
@RequiredArgsConstructor
@Slf4j
public class RendererController {

  private final RenderingStrategyFactory strategyFactory;

  /**
   * Get the status of all available renderers.
   *
   * @return list of renderer information including availability
   */
  @GetMapping("/status")
  public ResponseEntity<List<Map<String, Object>>> getRendererStatus() {
    log.debug("Getting renderer status");
    List<Map<String, Object>> renderers = new ArrayList<>();

    // Check each known renderer
    String[] rendererNames = {"MSGRAPH", "ASPOSE", "POI"};
    for (String name : rendererNames) {
      boolean available = strategyFactory.isRendererAvailable(name);
      renderers.add(
          Map.of(
              "name", name,
              "available", available));
    }

    log.info("Found {} renderers", renderers.size());
    return ResponseEntity.ok(renderers);
  }

  /**
   * Get the currently configured renderer based on request.
   *
   * @return information about the renderer
   */
  @GetMapping("/active")
  public ResponseEntity<Map<String, Object>> getActiveRenderer() {
    log.debug("Getting active renderer status");
    // In the new architecture, there's no single "active" renderer
    // Renderer is chosen per presentation rendering request
    return ResponseEntity.ok(
        Map.of(
            "message",
            "Renderer is selected per presentation in the new architecture",
            "availableRenderers",
            List.of("MSGRAPH", "ASPOSE", "POI")));
  }
}
