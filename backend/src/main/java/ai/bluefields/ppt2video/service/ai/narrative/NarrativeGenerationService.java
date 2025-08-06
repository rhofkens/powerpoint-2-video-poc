package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
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

      // Generate narrative using AI
      String narrativeResult =
          generateSlideNarrative(slide, contextDataJson, style, presentationId);

      // Parse and save narrative
      SlideNarrative narrative = parsingService.parseNarrative(narrativeResult, slide, style);
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

      // Generate narrative using AI
      String narrativeResult =
          generateSlideNarrative(slide, contextDataJson, style, presentationId);

      // Parse narrative
      SlideNarrative narrative = parsingService.parseNarrative(narrativeResult, slide, style);

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

    String systemPrompt = promptService.getSystemPromptForStyle(style);
    String userPrompt = promptService.buildUserPrompt(contextDataJson);
    String responseFormat = promptService.getResponseFormatSchema();

    return openAIService.generateJsonCompletion(
        systemPrompt, userPrompt, responseFormat, "narrative-generation", presentationId);
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
}
