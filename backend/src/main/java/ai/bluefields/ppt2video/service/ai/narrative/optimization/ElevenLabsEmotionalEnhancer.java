package ai.bluefields.ppt2video.service.ai.narrative.optimization;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emotional enhancer for ElevenLabs TTS engine. Uses punctuation and capitalization to convey
 * emotions: - Ellipses (...) for pauses and weight - CAPITALIZATION for emphasis - Exclamation
 * marks (!) for excitement - Question marks (?) for inquisitive tone - Commas for natural rhythm
 * Includes post-processing to fix unnatural ellipsis placement in verb constructions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElevenLabsEmotionalEnhancer implements EmotionalEnhancer {

  private final OpenAIService openAIService;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final ObjectMapper objectMapper;

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

      // Check if already enhanced
      if (narrative.hasEnhancement()) {
        log.info(
            "Narrative for slide {} already has enhancement, returning existing enhanced text",
            narrative.getSlide().getSlideNumber());
        return narrative.getEnhancedNarrativeText();
      }

      UUID presentationId = narrative.getSlide().getPresentation().getId();
      String enhancedText = callAIForEmotionalEnhancement(narrative, presentationId);

      // Post-process to fix ellipsis placement
      if (enhancedText != null && !enhancedText.isEmpty()) {
        enhancedText = postProcessEllipses(enhancedText);
      }

      if (enhancedText != null && !enhancedText.isEmpty()) {
        // Store in new enhanced field instead of overwriting original
        narrative.setEnhancedNarrativeText(enhancedText);
        narrative.setEnhancementTimestamp(LocalDateTime.now());
        // Get the actual model name from OpenAIService configuration
        String modelUsed = openAIService.getModelName() + "-elevenlabs";
        narrative.setEnhancementModelUsed(modelUsed);

        // Store metadata about enhancement
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("enhancer", "elevenlabs");
        metadata.put("originalLength", narrative.getNarrativeText().length());
        metadata.put("enhancedLength", enhancedText.length());
        metadata.put("slideNumber", narrative.getSlide().getSlideNumber());
        metadata.put("presentationId", presentationId.toString());

        try {
          narrative.setEnhancementMetadata(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
          log.warn("Failed to serialize enhancement metadata", e);
        }

        slideNarrativeRepository.save(narrative);
        log.info(
            "Successfully enhanced narrative for slide {} - original length: {}, enhanced length: {}",
            narrative.getSlide().getSlideNumber(),
            narrative.getNarrativeText().length(),
            enhancedText.length());
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
    int alreadyEnhanced = 0;

    for (SlideNarrative narrative : narratives) {
      // Skip if already enhanced
      if (narrative.hasEnhancement()) {
        alreadyEnhanced++;
        log.debug(
            "Skipping slide {} - already has enhancement", narrative.getSlide().getSlideNumber());
        continue;
      }

      String result = enhanceNarrative(narrative);
      // Check if enhancement was successful by comparing with original
      if (!result.equals(narrative.getNarrativeText())) {
        enhanced++;
      }
    }

    log.info(
        "Enhancement complete: {}/{} narratives enhanced, {} already had enhancements",
        enhanced,
        narratives.size(),
        alreadyEnhanced);
    return enhanced;
  }

  /** Call AI to add ElevenLabs-specific emotional markers to the narrative. */
  private String callAIForEmotionalEnhancement(SlideNarrative narrative, UUID presentationId) {
    // Parse the structured emotion data from the narrative
    String emotionIndicators =
        narrative.getEmotionIndicators() != null ? narrative.getEmotionIndicators() : "[]";
    String emphasisWords =
        narrative.getEmphasisWords() != null ? narrative.getEmphasisWords() : "[]";
    String speechMarkers =
        narrative.getSpeechMarkers() != null ? narrative.getSpeechMarkers() : "{}";
    String avatarInstructions =
        narrative.getAvatarInstructions() != null ? narrative.getAvatarInstructions() : "{}";

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
        - Parentheses () - Softer, aside tone
        - Period followed by ellipses (. ...) - Stronger dramatic pause

        IMPORTANT: Do NOT use dashes (— or -) as they cause issues with the TTS engine

        Guidelines:
        1. Use CAPS sparingly, only for KEY emphasis words (1-3 per paragraph)
        2. Add ellipses for dramatic effect or thoughtful pauses
        3. NEVER place ellipses between auxiliary/modal verbs and main verbs
        4. Use exclamation marks when genuine excitement is warranted
        5. Maintain readability, don't overuse any technique
        6. Match the emotional context provided
        7. Keep the original meaning intact
        8. Make it sound natural when spoken aloud
        9. Place ellipses at natural pause points: after introductory phrases, before conclusions, between independent clauses

        Examples:
        Original: "This is very important for our success."
        Enhanced: "This is VERY important... for our success."

        Original: "We achieved our goals and exceeded expectations."
        Enhanced: "We achieved our goals and EXCEEDED expectations!"

        Original: "Nobody expected this outcome but here we are."
        Enhanced: "Nobody expected this outcome... but here we are."

        AVOID these problematic patterns:
        Wrong: "I would... do this" or "We have... completed it"
        Correct: "I... would do this" or "We have completed it..." or "I would do this"
        """;

    String userPrompt =
        String.format(
            """
        Enhance this narrative with ElevenLabs emotional markers based on the provided emotional guidance.

        EMOTION TIMELINE (word ranges and their emotions):
        %s

        EMPHASIS WORDS (words that should be emphasized):
        %s

        SPEECH PAUSES (where pauses should occur):
        %s

        AVATAR EMOTION CONTEXT:
        %s

        Slide type: %s

        Original narrative:
        %s

        INSTRUCTIONS:
        1. Follow the emotion timeline - adjust your enhancement style based on the emotion for each word range
        2. Apply CAPITALIZATION to the emphasis words listed above
        3. Add ellipses (...) at the pause locations indicated in the speech markers
        4. For "enthusiastic" sections: use exclamation marks and energetic tone
        5. For "confident" sections: use strong, declarative punctuation
        6. For "friendly" sections: use warm, conversational tone
        7. For "neutral" sections: use standard punctuation
        8. Preserve the exact wording - only modify punctuation and capitalization

        Return ONLY the enhanced text with appropriate punctuation and capitalization.
        Do not include any explanation or metadata.
        """,
            emotionIndicators,
            emphasisWords,
            speechMarkers,
            avatarInstructions,
            narrative.getSlide().getSlideNumber() == 1 ? "Introduction" : "Content",
            narrative.getNarrativeText());

    return openAIService.generateChatCompletion(
        systemPrompt, userPrompt, "ElevenLabsEmotionalEnhancer", presentationId);
  }

  /**
   * Post-process the enhanced text to fix unnatural ellipsis placement. Specifically addresses
   * ellipses placed between auxiliary/modal verbs and main verbs.
   */
  private String postProcessEllipses(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }

    log.debug("Post-processing ellipses in enhanced text");
    String original = text;

    // Pattern 1: Modal verbs (would, could, should, might, may, must, will, shall, can)
    // Matches: "I would... do" and moves to "I... would do" or "I would do"
    text =
        text.replaceAll(
            "\\b(would|could|should|might|may|must|will|shall|can)\\.\\.\\. (\\w+)", "$1 $2");

    // Pattern 1a: Contractions with modal verbs ('d, 'll, etc.)
    // Matches: "I'd... do", "we'll... see", "they'd... have"
    text =
        text.replaceAll(
            "(\\w+'d|\\w+'ll|won't|can't|couldn't|wouldn't|shouldn't|mightn't|mustn't|shan't)\\.\\.\\. (\\w+)",
            "$1 $2");

    // Pattern 2: Auxiliary verbs with past participles (have/has/had + past participle)
    // Matches: "have... done", "has... been", "had... gone"
    text =
        text.replaceAll(
            "\\b(have|has|had)\\.\\.\\. (\\w+ed|\\w+en|been|done|gone|made|taken|given|seen|known)",
            "$1 $2");

    // Pattern 2a: Contractions with have/has ('ve, 's)
    // Matches: "I've... done", "he's... been"
    text =
        text.replaceAll(
            "(\\w+'ve|\\w+'s)\\.\\.\\. (\\w+ed|\\w+en|been|done|gone|made|taken|given|seen|known)",
            "$1 $2");

    // Pattern 3: "To be" verbs (is/was/are/were/am/be + verb-ing or adjective)
    // Matches: "is... running", "was... thinking"
    text =
        text.replaceAll("\\b(is|was|are|were|am|be|being|been)\\.\\.\\. (\\w+ing|\\w+)", "$1 $2");

    // Pattern 3a: Contractions with "to be" ('m, 're, 's)
    // Matches: "I'm... going", "we're... thinking", "it's... working"
    text = text.replaceAll("(\\w+'m|\\w+'re|\\w+'s)\\.\\.\\. (\\w+ing|\\w+)", "$1 $2");

    // Pattern 4: "To" infinitive constructions
    // Matches: "to... do", "to... be"
    text = text.replaceAll("\\bto\\.\\.\\. (\\w+)", "to $1");

    // Pattern 5: Subject + modal/auxiliary with ellipsis
    // If we have "I/You/We/They/He/She/It" followed by a verb with ellipsis, move ellipsis before
    text =
        text.replaceAll(
            "\\b(I|You|We|They|He|She|It) (would|could|should|might|may|must|will|shall|can|have|has|had|am|is|are|was|were)\\.\\.\\. ",
            "$1... $2 ");

    // Clean up any double spaces that might have been created
    text = text.replaceAll("  +", " ");

    // Log if changes were made
    if (!text.equals(original)) {
      log.debug("Post-processing modified ellipsis placement");
    }

    return text;
  }
}
