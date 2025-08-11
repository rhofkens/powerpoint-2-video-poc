package ai.bluefields.ppt2video.service.ai.narrative.optimization;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import java.util.List;

/**
 * Interface for emotional enhancement implementations. Different TTS engines require different
 * formats for emotional expression.
 */
public interface EmotionalEnhancer {

  /**
   * Get the name of the TTS engine this enhancer supports.
   *
   * @return The TTS engine name (e.g., "elevenlabs", "openai", "azure")
   */
  String getEngineType();

  /**
   * Enhance a narrative with emotional markers appropriate for the TTS engine.
   *
   * @param narrative The narrative to enhance
   * @return The enhanced narrative text
   */
  String enhanceNarrative(SlideNarrative narrative);

  /**
   * Batch enhance multiple narratives.
   *
   * @param narratives List of narratives to enhance
   * @return Number of successfully enhanced narratives
   */
  int enhanceNarratives(List<SlideNarrative> narratives);
}
