package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import ai.bluefields.ppt2video.repository.DeckAnalysisRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for preparing context data for narrative generation. Handles slide
 * relationships, deck analysis context, and data preparation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeContextService {

  private final SlideRepository slideRepository;
  private final DeckAnalysisRepository deckAnalysisRepository;
  private final ObjectMapper objectMapper;

  /**
   * Prepare complete context data for narrative generation.
   *
   * @param currentSlide The slide to generate narrative for
   * @param presentationId The presentation ID
   * @return Context data as JSON string
   */
  @Transactional(readOnly = true)
  public String prepareContextJson(Slide currentSlide, UUID presentationId) {
    try {
      List<Slide> allSlides =
          slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
      Map<String, Object> contextData = prepareContextData(currentSlide, allSlides, presentationId);
      return objectMapper.writeValueAsString(contextData);
    } catch (Exception e) {
      log.error("Failed to prepare context JSON for slide {}", currentSlide.getId(), e);
      throw new RuntimeException("Failed to prepare context", e);
    }
  }

  /**
   * Get all slides for a presentation ordered by slide number.
   *
   * @param presentationId The presentation ID
   * @return List of slides ordered by slide number
   */
  @Transactional(readOnly = true)
  public List<Slide> getSlidesForPresentation(UUID presentationId) {
    return slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
  }

  /**
   * Prepare context data including surrounding slides.
   *
   * @param currentSlide The current slide
   * @param allSlides All slides in the presentation
   * @param presentationId The presentation ID
   * @return Map containing context data
   */
  private Map<String, Object> prepareContextData(
      Slide currentSlide, List<Slide> allSlides, UUID presentationId) {

    // Find previous and next slides
    SlideContext slideContext = findSurroundingSlides(currentSlide, allSlides);

    Map<String, Object> contextData = new HashMap<>();

    // CURRENT SLIDE - This is the slide to generate narrative for
    contextData.put("currentSlide", prepareSlideData(currentSlide));
    contextData.put("INSTRUCTION", "Generate narrative ONLY for the currentSlide above");

    // Previous slide context (for continuity only)
    if (slideContext.previousSlide != null) {
      contextData.put("previousSlide", preparePreviousSlideContext(slideContext.previousSlide));
    }

    // Next slide context (for transition planning only)
    if (slideContext.nextSlide != null) {
      contextData.put("nextSlide", prepareNextSlideContext(slideContext.nextSlide));
    }

    // Deck analysis context
    addDeckAnalysisContext(contextData, presentationId);

    // Slide position information
    contextData.put("slidePosition", prepareSlidePosition(currentSlide, allSlides));

    return contextData;
  }

  /** Find previous and next slides for context. */
  private SlideContext findSurroundingSlides(Slide currentSlide, List<Slide> allSlides) {
    SlideContext context = new SlideContext();

    for (int i = 0; i < allSlides.size(); i++) {
      if (allSlides.get(i).getId().equals(currentSlide.getId())) {
        if (i > 0) {
          context.previousSlide = allSlides.get(i - 1);
        }
        if (i < allSlides.size() - 1) {
          context.nextSlide = allSlides.get(i + 1);
        }
        break;
      }
    }

    return context;
  }

  /** Prepare individual slide data. */
  private Map<String, Object> prepareSlideData(Slide slide) {
    Map<String, Object> slideData = new HashMap<>();
    slideData.put("slideNumber", slide.getSlideNumber());
    slideData.put("title", slide.getTitle());
    slideData.put("contentText", slide.getContentText());
    slideData.put("speakerNotes", slide.getSpeakerNotes());

    if (slide.getSlideAnalysis() != null) {
      SlideAnalysis analysis = slide.getSlideAnalysis();
      slideData.put("generalMessage", analysis.getGeneralMessage());
      slideData.put("visualConcepts", analysis.getVisualConcepts());
      slideData.put("keyPoints", analysis.getKeyPoints());
      slideData.put("emphasisLevel", analysis.getEmphasisLevel());
    }

    return slideData;
  }

  /** Prepare previous slide context for continuity. */
  private Map<String, Object> preparePreviousSlideContext(Slide previousSlide) {
    Map<String, Object> previousSlideContext = new HashMap<>();
    previousSlideContext.put("title", previousSlide.getTitle());
    previousSlideContext.put("slideNumber", previousSlide.getSlideNumber());
    previousSlideContext.put("NOTE", "This is for context only - do not narrate this slide");

    if (previousSlide.getSlideAnalysis() != null) {
      SlideAnalysis prevAnalysis = previousSlide.getSlideAnalysis();
      previousSlideContext.put("generalMessage", prevAnalysis.getGeneralMessage());
      previousSlideContext.put("visualConcepts", prevAnalysis.getVisualConcepts());
      previousSlideContext.put("keyPoints", prevAnalysis.getKeyPoints());
      previousSlideContext.put("emphasisLevel", prevAnalysis.getEmphasisLevel());
    }

    return previousSlideContext;
  }

  /** Prepare next slide context for transition planning. */
  private Map<String, Object> prepareNextSlideContext(Slide nextSlide) {
    Map<String, Object> nextSlideContext = new HashMap<>();
    nextSlideContext.put("title", nextSlide.getTitle());
    nextSlideContext.put("slideNumber", nextSlide.getSlideNumber());
    nextSlideContext.put("NOTE", "This is for context only - do not narrate this slide");

    if (nextSlide.getSlideAnalysis() != null) {
      SlideAnalysis nextAnalysis = nextSlide.getSlideAnalysis();
      nextSlideContext.put("generalMessage", nextAnalysis.getGeneralMessage());
      nextSlideContext.put("visualConcepts", nextAnalysis.getVisualConcepts());
      nextSlideContext.put("keyPoints", nextAnalysis.getKeyPoints());
      nextSlideContext.put("emphasisLevel", nextAnalysis.getEmphasisLevel());
    }

    return nextSlideContext;
  }

  /** Add deck analysis context if available. */
  private void addDeckAnalysisContext(Map<String, Object> contextData, UUID presentationId) {
    if (presentationId != null) {
      deckAnalysisRepository
          .findByPresentationId(presentationId)
          .ifPresent(
              deckAnalysis -> {
                contextData.put(
                    "deckContext",
                    Map.of(
                        "overallStory", deckAnalysis.getOverallStory(),
                        "tone", deckAnalysis.getTone(),
                        "targetAudience", deckAnalysis.getTargetAudience()));
              });
    }
  }

  /** Prepare slide position information. */
  private Map<String, Object> prepareSlidePosition(Slide currentSlide, List<Slide> allSlides) {
    return Map.of(
        "current",
        currentSlide.getSlideNumber(),
        "total",
        allSlides.size(),
        "isFirst",
        currentSlide.getSlideNumber() == 1,
        "isLast",
        currentSlide.getSlideNumber() == allSlides.size());
  }

  /** Helper class to hold slide context. */
  private static class SlideContext {
    Slide previousSlide;
    Slide nextSlide;
  }
}
