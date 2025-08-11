package ai.bluefields.ppt2video.service.ai.narrative.optimization;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emotional enhancer for ElevenLabs TTS engine. Uses punctuation and capitalization to convey
 * emotions: - Ellipses (...) for pauses and weight - CAPITALIZATION for emphasis - Exclamation
 * marks (!) for excitement - Question marks (?) for inquisitive tone
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElevenLabsEmotionalEnhancer implements EmotionalEnhancer {

  private final OpenAIService openAIService;
  private final SlideNarrativeRepository slideNarrativeRepository;

  @Override
  public String getEngineType() {
    return "elevenlabs";
  }

  @Override
  @Transactional
  public String enhanceNarrative(SlideNarrative narrative) {
    try {
      log.debug(
          "Enhancing narrative for slide {} with ElevenLabs emotional markers",
          narrative.getSlide().getSlideNumber());

      UUID presentationId = narrative.getSlide().getPresentation().getId();
      String enhancedText = callAIForEmotionalEnhancement(narrative, presentationId);

      if (enhancedText != null && !enhancedText.isEmpty()) {
        narrative.setNarrativeText(enhancedText);
        slideNarrativeRepository.save(narrative);
        return enhancedText;
      }

      return narrative.getNarrativeText();

    } catch (Exception e) {
      log.error(
          "Failed to enhance narrative for slide {}", narrative.getSlide().getSlideNumber(), e);
      return narrative.getNarrativeText();
    }
  }

  @Override
  @Transactional
  public int enhanceNarratives(List<SlideNarrative> narratives) {
    if (narratives == null || narratives.isEmpty()) {
      return 0;
    }

    log.info("Starting ElevenLabs emotional enhancement for {} narratives", narratives.size());

    int enhanced = 0;
    for (SlideNarrative narrative : narratives) {
      String result = enhanceNarrative(narrative);
      if (!result.equals(narrative.getNarrativeText())) {
        enhanced++;
      }
    }

    log.info(
        "Successfully enhanced {}/{} narratives with ElevenLabs markers",
        enhanced,
        narratives.size());
    return enhanced;
  }

  /** Call AI to add ElevenLabs-specific emotional markers to the narrative. */
  private String callAIForEmotionalEnhancement(SlideNarrative narrative, UUID presentationId) {
    // Get emotion indicators from the narrative metadata
    String emotionContext =
        narrative.getEmotionIndicators() != null ? narrative.getEmotionIndicators() : "neutral";

    String systemPrompt =
        """
        You are an expert at adding emotional expression to text for ElevenLabs TTS engine.
        ElevenLabs uses punctuation and capitalization to convey emotions and emphasis.

        Available techniques:
        - Ellipses (...) - Creates natural pauses and adds weight/thoughtfulness
        - CAPITALIZATION - Adds emphasis and intensity to specific words
        - Exclamation marks (!) - Conveys excitement, urgency, or enthusiasm
        - Question marks (?) - Natural inquisitive tone
        - Commas (,) - Creates natural pauses and rhythm
        - Dashes (—) - Creates dramatic pauses or interruptions
        - Parentheses () - Softer, aside tone

        Guidelines:
        1. Use CAPS sparingly - only for KEY emphasis words (1-3 per paragraph)
        2. Add ellipses for dramatic effect or thoughtful pauses
        3. Use exclamation marks when genuine excitement is warranted
        4. Maintain readability - don't overuse any technique
        5. Match the emotional context provided
        6. Keep the original meaning intact
        7. Make it sound natural when spoken aloud

        Examples:
        Original: "This is very important for our success."
        Enhanced: "This is VERY important... for our success."

        Original: "We achieved our goals and exceeded expectations."
        Enhanced: "We achieved our goals and EXCEEDED expectations!"

        Original: "Nobody expected this outcome but here we are."
        Enhanced: "Nobody expected this outcome... but here we are."
        """;

    String userPrompt =
        String.format(
            """
        Enhance this narrative with ElevenLabs emotional markers.

        Emotional context: %s
        Slide type: %s

        Original narrative:
        %s

        Return ONLY the enhanced text with appropriate punctuation and capitalization.
        Do not include any explanation or metadata.
        """,
            emotionContext,
            narrative.getSlide().getSlideNumber() == 1 ? "Introduction" : "Content",
            narrative.getNarrativeText());

    return openAIService.generateChatCompletion(
        systemPrompt, userPrompt, "ElevenLabsEmotionalEnhancer", presentationId);
  }
}
