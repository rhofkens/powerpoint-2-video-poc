package ai.bluefields.ppt2video.service.rendering;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Factory for creating rendering strategies based on renderer name. */
@Component
@Slf4j
@RequiredArgsConstructor
public class RenderingStrategyFactory {

  private final Map<String, RenderingStrategy> strategies;

  /**
   * Gets a rendering strategy by name.
   *
   * @param rendererName the renderer name (e.g., "MSGRAPH", "ASPOSE", "POI")
   * @return the rendering strategy
   * @throws IllegalArgumentException if renderer not found
   */
  public RenderingStrategy getStrategy(String rendererName) {
    RenderingStrategy strategy = strategies.get(rendererName.toUpperCase());
    if (strategy == null) {
      log.error("No rendering strategy found for renderer: {}", rendererName);
      throw new IllegalArgumentException("Unknown renderer: " + rendererName);
    }
    return strategy;
  }

  /**
   * Checks if a renderer is available.
   *
   * @param rendererName the renderer name
   * @return true if available, false otherwise
   */
  public boolean isRendererAvailable(String rendererName) {
    return strategies.containsKey(rendererName.toUpperCase());
  }
}
