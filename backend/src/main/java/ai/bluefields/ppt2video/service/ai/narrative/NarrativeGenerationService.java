package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.dto.ShortenNarrativeResponse;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.entity.SlideType;
import ai.bluefields.ppt2video.model.DurationRange;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.NarrativeLengthCalculator;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main service for generating AI narratives for slides. Coordinates between different specialized
 * services for narrative generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeGenerationService {

  private final OpenAIService openAIService;
  private final SlideRepository slideRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final NarrativePromptService promptService;
  private final NarrativeParsingService parsingService;
  private final NarrativeContextService contextService;
  private final NarrativeLengthCalculator lengthCalculator;
  private final ObjectMapper objectMapper;

  @Value("${app.ai.narrative-generation.enabled:true}")
  private boolean narrativeGenerationEnabled;

  /**
   * Generate narrative for a slide and store the result with default business style.
   *
   * @param slideId The ID of the slide to generate narrative for
   * @return The created SlideNarrative entity
   */
  @Transactional
  public SlideNarrative generateNarrative(UUID slideId) {
    return generateNarrative(slideId, "business");
  }

  /**
   * Generate narrative for a slide with known presentation ID (for async context).
   *
   * @param slideId The ID of the slide to generate narrative for
   * @param style The narrative style
   * @param presentationId The presentation ID (passed to avoid lazy loading)
   * @return The created SlideNarrative entity
   */
  @Transactional
  public SlideNarrative generateNarrativeWithPresentationId(
      UUID slideId, String style, UUID presentationId) {

    validateNarrativeGeneration(slideId);

    log.info(
        "Starting {} style narrative generation for slide: {} (presentation: {})",
        style,
        slideId,
        presentationId);

    Slide slide = fetchAndValidateSlide(slideId);

    try {
      // Prepare context
      String contextDataJson = contextService.prepareContextJson(slide, presentationId);

      // Get target duration for storing with narrative
      SlideType slideType = getSlideType(slide);
      DurationRange targetDuration = lengthCalculator.calculateTargetDuration(slide, slideType);

      // Generate narrative using AI
      String narrativeResult =
          generateSlideNarrative(slide, contextDataJson, style, presentationId);

      // Parse and save narrative with target duration
      SlideNarrative narrative = parsingService.parseNarrative(narrativeResult, slide, style);
      narrative.setTargetDurationSeconds(targetDuration.getMidpoint());
      narrative = slideNarrativeRepository.save(narrative);

      log.info("Generated narrative for slide: {} with ID: {}", slideId, narrative.getId());
      return narrative;

    } catch (Exception e) {
      log.error("Failed to generate narrative for slide: {}", slideId, e);
      throw new RuntimeException("Failed to generate narrative", e);
    }
  }

  /**
   * Generate narrative for a slide and store the result.
   *
   * @param slideId The ID of the slide to generate narrative for
   * @param style The narrative style ("business", "funny", "cynical")
   * @return The created SlideNarrative entity
   */
  @Transactional
  public SlideNarrative generateNarrative(UUID slideId, String style) {
    validateNarrativeGeneration(slideId);

    log.info("Starting {} style narrative generation for slide: {}", style, slideId);

    Slide slide = fetchAndValidateSlide(slideId);

    try {
      // Get presentation ID safely
      UUID presentationId = slide.getPresentation().getId();

      // Prepare context
      String contextDataJson = contextService.prepareContextJson(slide, presentationId);

      // Get target duration for storing with narrative
      SlideType slideType = getSlideType(slide);
      DurationRange targetDuration = lengthCalculator.calculateTargetDuration(slide, slideType);

      // Generate narrative using AI
      String narrativeResult =
          generateSlideNarrative(slide, contextDataJson, style, presentationId);

      // Parse narrative with target duration
      SlideNarrative narrative = parsingService.parseNarrative(narrativeResult, slide, style);
      narrative.setTargetDurationSeconds(targetDuration.getMidpoint());

      // Deactivate previous narratives
      parsingService.deactivateExistingNarrative(slideId);

      // Save new narrative
      narrative = slideNarrativeRepository.save(narrative);

      log.info("Completed narrative generation for slide: {}", slideId);
      return narrative;

    } catch (Exception e) {
      log.error("Failed to generate narrative for slide: {}", slideId, e);
      throw new RuntimeException("Failed to generate narrative", e);
    }
  }

  /** Validate that narrative generation is enabled and slide exists. */
  private void validateNarrativeGeneration(UUID slideId) {
    if (!narrativeGenerationEnabled) {
      throw new IllegalStateException("Narrative generation is disabled");
    }
  }

  /** Fetch and validate slide has been analyzed. */
  private Slide fetchAndValidateSlide(UUID slideId) {
    Slide slide =
        slideRepository
            .findById(slideId)
            .orElseThrow(() -> new IllegalArgumentException("Slide not found: " + slideId));

    if (slide.getSlideAnalysis() == null) {
      throw new IllegalStateException("Slide must be analyzed before generating narrative");
    }

    return slide;
  }

  /** Generate narrative using AI with context. */
  private String generateSlideNarrative(
      Slide slide, String contextDataJson, String style, UUID presentationId) {

    // Get slide type and calculate target duration
    SlideType slideType = getSlideType(slide);
    DurationRange targetDuration = lengthCalculator.calculateTargetDuration(slide, slideType);

    log.info(
        "Generating narrative for slide {} (type: {}) with target duration: {}",
        slide.getSlideNumber(),
        slideType,
        targetDuration.toFormattedString());

    String systemPrompt = promptService.getSystemPromptForStyle(style);
    String userPrompt =
        promptService.buildUserPrompt(
            contextDataJson,
            targetDuration.getMinSeconds(),
            targetDuration.getMaxSeconds(),
            slideType);
    String responseFormat = promptService.getResponseFormatSchema();

    return openAIService.generateJsonCompletion(
        systemPrompt, userPrompt, responseFormat, "narrative-generation", presentationId);
  }

  /** Get slide type from analysis or detect it. */
  private SlideType getSlideType(Slide slide) {
    if (slide.getSlideAnalysis() != null && slide.getSlideAnalysis().getSlideType() != null) {
      return slide.getSlideAnalysis().getSlideType();
    }
    // Fallback to CONTENT if not analyzed
    log.warn("Slide {} has no type in analysis, defaulting to CONTENT", slide.getId());
    return SlideType.CONTENT;
  }

  /**
   * Get all narratives for a presentation ordered by slide number.
   *
   * @param presentationId The ID of the presentation
   * @return List of slide narratives ordered by slide number
   */
  @Transactional(readOnly = true)
  public List<SlideNarrative> getAllNarrativesForPresentation(UUID presentationId) {
    log.debug("Fetching all narratives for presentation: {}", presentationId);

    List<Slide> slides = contextService.getSlidesForPresentation(presentationId);
    List<SlideNarrative> narratives = new ArrayList<>();

    for (Slide slide : slides) {
      slideNarrativeRepository.findBySlideId(slide.getId()).ifPresent(narratives::add);
    }

    log.info("Found {} narratives for presentation {}", narratives.size(), presentationId);
    return narratives;
  }

  /**
   * Shorten an existing narrative by a specified percentage.
   *
   * @param narrativeId The ID of the narrative to shorten
   * @param reductionPercentage The percentage to reduce (25-75)
   * @return Response containing both original and shortened narratives
   */
  @Transactional
  public ShortenNarrativeResponse shortenNarrative(UUID narrativeId, int reductionPercentage) {
    log.info("Shortening narrative {} by {}%", narrativeId, reductionPercentage);

    // Load existing narrative
    SlideNarrative originalNarrative =
        slideNarrativeRepository
            .findById(narrativeId)
            .orElseThrow(() -> new RuntimeException("Narrative not found: " + narrativeId));

    // Load the slide
    Slide slide = originalNarrative.getSlide();

    // Extract the original style from metadata
    String style = extractStyleFromMetadata(originalNarrative.getGenerationMetadata());

    // Generate the shortened narrative using OpenAI
    String shortenedText =
        generateShortenedNarrative(originalNarrative.getNarrativeText(), reductionPercentage);

    log.info(
        "Shortened narrative text ({}% reduction requested): {}",
        reductionPercentage, shortenedText);

    // Deactivate old narrative
    originalNarrative.setIsActive(false);
    slideNarrativeRepository.save(originalNarrative);

    // Apply emotional enhancement
    log.info("Starting emotional enhancement for shortened narrative");
    String emotionallyEnhancedResult = applyEmotionalEnhancement(shortenedText, slide);

    log.info("Emotionally enhanced result: {}", emotionallyEnhancedResult);

    // Create new narrative with shortened text
    SlideNarrative shortenedNarrative = new SlideNarrative();
    shortenedNarrative.setSlide(slide);
    shortenedNarrative.setVersion(originalNarrative.getVersion() + 1);
    shortenedNarrative.setIsActive(true);

    // Parse the enhanced result
    try {
      SlideNarrative enhancedNarrative =
          parsingService.parseNarrative(emotionallyEnhancedResult, slide, style);

      // Use the enhanced narrative text and all enhanced fields
      shortenedNarrative.setNarrativeText(enhancedNarrative.getNarrativeText());
      shortenedNarrative.setEmotionIndicators(enhancedNarrative.getEmotionIndicators());
      shortenedNarrative.setAvatarInstructions(enhancedNarrative.getAvatarInstructions());
      shortenedNarrative.setSpeechMarkers(enhancedNarrative.getSpeechMarkers());
      shortenedNarrative.setEmphasisWords(enhancedNarrative.getEmphasisWords());
      shortenedNarrative.setDurationSeconds(enhancedNarrative.getDurationSeconds());
      shortenedNarrative.setGenerationMetadata(enhancedNarrative.getGenerationMetadata());

      log.info(
          "Successfully applied emotional enhancement with {} emotion indicators",
          enhancedNarrative.getEmotionIndicators() != null
              ? enhancedNarrative.getEmotionIndicators()
              : "no");
    } catch (Exception e) {
      log.error(
          "Failed to parse emotional enhancement for shortened narrative, using plain text", e);
      // Fallback to plain shortened text
      shortenedNarrative.setNarrativeText(shortenedText);

      // Calculate duration manually
      int wordCount = shortenedText.split("\\s+").length;
      int durationSeconds = (int) Math.ceil((wordCount / 150.0) * 60);
      shortenedNarrative.setDurationSeconds(durationSeconds);

      // Create basic generation metadata
      try {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("narrativeStyle", style);
        metadata.put("shortening", true);
        metadata.put("reductionPercentage", reductionPercentage);
        shortenedNarrative.setGenerationMetadata(objectMapper.writeValueAsString(metadata));
      } catch (Exception metaEx) {
        log.error("Failed to create generation metadata", metaEx);
      }
    }

    // Copy transition phrase if it exists
    shortenedNarrative.setTransitionPhrase(originalNarrative.getTransitionPhrase());

    shortenedNarrative.setModelUsed("gpt-4");
    shortenedNarrative.setPromptVersion("1.0-shorten");

    // Save the shortened narrative
    shortenedNarrative = slideNarrativeRepository.save(shortenedNarrative);

    log.info(
        "Successfully shortened narrative {} to {} ({}% reduction)",
        narrativeId, shortenedNarrative.getId(), reductionPercentage);

    return ShortenNarrativeResponse.fromNarratives(
        originalNarrative, shortenedNarrative, reductionPercentage);
  }

  /** Generate shortened narrative using OpenAI. */
  private String generateShortenedNarrative(String originalText, int reductionPercentage) {
    String systemPrompt =
        "You are an expert editor specializing in concise communication. "
            + "Your task is to shorten narratives while preserving their essential meaning and flow.";

    String userPrompt =
        String.format(
            """
            Shorten the following narrative by exactly %d%%.
            Maintain all key points and the narrative flow, but remove:
            - Redundant phrases and words
            - Unnecessary adjectives and adverbs
            - Verbose explanations
            - Repetitive content
            - Filler words and phrases

            Keep the tone and style consistent with the original.
            The shortened version should still be a complete, coherent narrative.

            Original narrative:
            %s

            Provide only the shortened narrative text, no explanations or commentary.
            """,
            reductionPercentage, originalText);

    String shortenedNarrative =
        openAIService.generateChatCompletion(
            systemPrompt, userPrompt, "narrative-shortening", null);

    return shortenedNarrative.trim();
  }

  /** Apply emotional enhancement to shortened narrative. */
  private String applyEmotionalEnhancement(String narrativeText, Slide slide) {
    log.info("Applying emotional enhancement to shortened narrative for slide: {}", slide.getId());

    String systemPrompt =
        """
        You are an expert in emotional communication and speech delivery.
        Enhance the narrative with emotional indicators and speech markers.
        """;

    String userPrompt =
        String.format(
            """
            Add emotional indicators and speech markers to this narrative.

            Narrative text:
            %s

            Provide a JSON response with:
            {
              "narrativeText": "the original text unchanged",
              "emotionIndicators": [
                {"startWord": 1, "endWord": 5, "emotion": "confident"}
              ],
              "avatarInstructions": {
                "primaryEmotion": "professional",
                "gestureIntensity": "medium",
                "facialExpression": "engaged"
              },
              "speechMarkers": {
                "pauses": [{"afterWord": 10, "duration": "short"}],
                "emphasis": ["key", "important", "words"]
              },
              "emphasisWords": ["key", "words", "to", "emphasize"]
            }
            """,
            narrativeText);

    String responseFormat =
        """
        {
          "narrativeText": "string",
          "emotionIndicators": [{"startWord": "number", "endWord": "number", "emotion": "string"}],
          "avatarInstructions": {"primaryEmotion": "string", "gestureIntensity": "string", "facialExpression": "string"},
          "speechMarkers": {"pauses": [{"afterWord": "number", "duration": "string"}], "emphasis": ["string"]},
          "emphasisWords": ["string"]
        }
        """;

    log.debug(
        "Calling OpenAI for emotional enhancement with narrative length: {} characters",
        narrativeText.length());
    String result =
        openAIService.generateJsonCompletion(
            systemPrompt, userPrompt, responseFormat, "emotional-enhancement", slide.getId());
    log.info("Emotional enhancement completed successfully for slide: {}", slide.getId());

    return result;
  }

  /**
   * Extract narrative style from generation metadata. Defaults to "business" if not found or error
   * occurs.
   */
  private String extractStyleFromMetadata(String generationMetadata) {
    if (generationMetadata == null || generationMetadata.isEmpty()) {
      log.warn("No generation metadata found, defaulting to business style");
      return "business";
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = objectMapper.readValue(generationMetadata, Map.class);
      String style = (String) metadata.get("narrativeStyle");
      if (style != null) {
        return style;
      }
    } catch (Exception e) {
      log.error("Failed to parse generation metadata: {}", e.getMessage());
    }

    log.warn("No style found in metadata, defaulting to business style");
    return "business";
  }
}
