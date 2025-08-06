package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import ai.bluefields.ppt2video.repository.SlideAnalysisRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main service for analyzing individual slides using multimodal AI. Coordinates between different
 * specialized services for slide analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideAnalysisService {

  private final OpenAIService openAIService;
  private final SlideRepository slideRepository;
  private final SlideAnalysisRepository slideAnalysisRepository;
  private final SlideImageService imageService;
  private final SlideAnalysisPromptService promptService;
  private final SlideAnalysisParsingService parsingService;

  @Value("${app.ai.slide-analysis.enabled:true}")
  private boolean slideAnalysisEnabled;

  /**
   * Analyze an individual slide and store the results.
   *
   * @param slideId The ID of the slide to analyze
   * @return The created SlideAnalysis entity
   */
  @Transactional
  public SlideAnalysis analyzeSlide(UUID slideId) {
    return analyzeSlide(slideId, false);
  }

  /**
   * Analyze an individual slide and store the results.
   *
   * @param slideId The ID of the slide to analyze
   * @param force Whether to force re-analysis even if analysis already exists
   * @return The created SlideAnalysis entity
   */
  @Transactional
  public SlideAnalysis analyzeSlide(UUID slideId, boolean force) {
    validateAnalysisEnabled();

    log.info("Starting slide analysis for slide: {} (force={})", slideId, force);

    Slide slide = fetchSlide(slideId);

    // Check if analysis already exists (unless forced)
    if (shouldSkipAnalysis(slide, force)) {
      log.info("Slide analysis already exists for slide: {}", slideId);
      return slide.getSlideAnalysis();
    }

    // Handle forced re-analysis
    if (force) {
      deleteExistingAnalysis(slide);
    }

    // Update status
    updateSlideStatus(slide, Slide.ProcessingStatus.ANALYZING);

    try {
      return performAnalysis(slide);
    } catch (Exception e) {
      handleAnalysisError(slide, slideId, e);
      throw new RuntimeException("Failed to analyze slide", e);
    }
  }

  /** Validate that slide analysis is enabled. */
  private void validateAnalysisEnabled() {
    if (!slideAnalysisEnabled) {
      throw new IllegalStateException("Slide analysis is disabled");
    }
  }

  /** Fetch slide from repository. */
  private Slide fetchSlide(UUID slideId) {
    return slideRepository
        .findById(slideId)
        .orElseThrow(() -> new IllegalArgumentException("Slide not found: " + slideId));
  }

  /** Check if analysis should be skipped. */
  private boolean shouldSkipAnalysis(Slide slide, boolean force) {
    return slide.getSlideAnalysis() != null && !force;
  }

  /** Delete existing analysis for forced re-analysis. */
  private void deleteExistingAnalysis(Slide slide) {
    if (slide.getSlideAnalysis() != null) {
      log.info("Force re-analysis: deleting existing analysis for slide: {}", slide.getId());
      slideAnalysisRepository.delete(slide.getSlideAnalysis());
      slide.setSlideAnalysis(null);
    }
  }

  /** Update slide processing status. */
  private void updateSlideStatus(Slide slide, Slide.ProcessingStatus status) {
    slide.setProcessingStatus(status);
    slideRepository.save(slide);
  }

  /** Perform the actual slide analysis. */
  private SlideAnalysis performAnalysis(Slide slide) throws IOException {
    // Get slide image for multimodal analysis
    String imageBase64 = imageService.getSlideImageBase64(slide);
    String mimeType = imageService.getImageMimeType();

    // Get presentation context
    int totalSlides = slideRepository.countByPresentationId(slide.getPresentation().getId());

    // Prepare slide data
    Map<String, Object> slideData = promptService.prepareSlideData(slide, totalSlides);
    String slideDataJson = promptService.toJson(slideData);

    // Generate analysis using AI
    String analysisResult = generateSlideAnalysis(slide, slideDataJson, imageBase64, mimeType);

    // Parse and save analysis
    SlideAnalysis analysis = parsingService.parseSlideAnalysis(analysisResult, slide);
    analysis = slideAnalysisRepository.save(analysis);

    log.info("Completed slide analysis for slide: {}", slide.getId());
    return analysis;
  }

  /** Generate slide analysis using multimodal AI. */
  private String generateSlideAnalysis(
      Slide slide, String slideDataJson, String imageBase64, String mimeType) {

    String systemPrompt = promptService.getSystemPrompt();
    String userPrompt = promptService.getUserPrompt(slideDataJson);
    String responseFormat = promptService.getResponseFormatSchema();

    // Get presentation ID for logging context
    UUID presentationId = slide.getPresentation().getId();

    return openAIService.generateJsonVisionCompletion(
        systemPrompt,
        userPrompt,
        imageBase64,
        mimeType,
        responseFormat,
        "slide-analysis",
        presentationId);
  }

  /** Handle analysis error by updating slide status. */
  private void handleAnalysisError(Slide slide, UUID slideId, Exception e) {
    log.error("Failed to analyze slide: {}", slideId, e);
    updateSlideStatus(slide, Slide.ProcessingStatus.FAILED);
  }

  /**
   * Check if a slide has been analyzed.
   *
   * @param slideId The slide ID
   * @return true if the slide has been analyzed
   */
  @Transactional(readOnly = true)
  public boolean isSlideAnalyzed(UUID slideId) {
    return slideRepository
        .findById(slideId)
        .map(slide -> slide.getSlideAnalysis() != null)
        .orElse(false);
  }

  /**
   * Get analysis for a specific slide.
   *
   * @param slideId The slide ID
   * @return The slide analysis or null if not analyzed
   */
  @Transactional(readOnly = true)
  public SlideAnalysis getSlideAnalysis(UUID slideId) {
    return slideRepository.findById(slideId).map(Slide::getSlideAnalysis).orElse(null);
  }
}
