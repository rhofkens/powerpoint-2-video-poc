package ai.bluefields.ppt2video.service.ai.narrative.optimization;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Factory for selecting the appropriate emotional enhancer based on TTS engine. */
@Slf4j
@Component
public class EmotionalEnhancerFactory {

  private final Map<String, EmotionalEnhancer> enhancers;
  private final String defaultEngine;

  public EmotionalEnhancerFactory(
      List<EmotionalEnhancer> enhancerList,
      @Value("${app.ai.narrative.optimization.tts-engine:elevenlabs}") String defaultEngine) {

    this.enhancers =
        enhancerList.stream()
            .collect(Collectors.toMap(EmotionalEnhancer::getEngineType, Function.identity()));

    this.defaultEngine = defaultEngine;

    log.info(
        "Initialized EmotionalEnhancerFactory with {} enhancers: {}",
        enhancers.size(),
        enhancers.keySet());
  }

  /**
   * Get the emotional enhancer for the specified TTS engine.
   *
   * @param engineType The TTS engine type
   * @return The appropriate enhancer, or null if not found
   */
  public EmotionalEnhancer getEnhancer(String engineType) {
    EmotionalEnhancer enhancer = enhancers.get(engineType.toLowerCase());

    if (enhancer == null) {
      log.warn(
          "No emotional enhancer found for engine '{}', available engines: {}",
          engineType,
          enhancers.keySet());
    }

    return enhancer;
  }

  /**
   * Get the default emotional enhancer.
   *
   * @return The default enhancer based on configuration
   */
  public EmotionalEnhancer getDefaultEnhancer() {
    return getEnhancer(defaultEngine);
  }

  /**
   * Check if an enhancer exists for the specified engine.
   *
   * @param engineType The TTS engine type
   * @return true if an enhancer exists
   */
  public boolean hasEnhancer(String engineType) {
    return enhancers.containsKey(engineType.toLowerCase());
  }

  /**
   * Get all available engine types.
   *
   * @return List of supported engine types
   */
  public List<String> getAvailableEngines() {
    return enhancers.keySet().stream().sorted().collect(Collectors.toList());
  }
}
