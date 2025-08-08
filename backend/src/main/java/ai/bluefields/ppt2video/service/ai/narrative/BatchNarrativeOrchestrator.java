package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service for batch narrative generation. Handles async processing of multiple slides
 * and progress tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchNarrativeOrchestrator {

  private final SlideRepository slideRepository;
  private final NarrativeGenerationService narrativeGenerationService;
  private final NarrativeParsingService narrativeParsingService;
  private final AnalysisStatusService analysisStatusService;

  /**
   * Generate narratives for all slides in a presentation asynchronously. Each narrative is
   * generated in its own transaction to allow real-time progress visibility.
   *
   * @param presentationId The ID of the presentation
   * @param style The narrative style to use
   */
  @Async("virtualThreadExecutor")
  public void generateAllNarratives(UUID presentationId, String style) {
    log.info("=== STARTING BATCH NARRATIVE GENERATION ===");
    log.info("Presentation ID: {}", presentationId);
    log.info("Style: {}", style);
    log.info("Thread: {}", Thread.currentThread().getName());

    try {
      List<Slide> slides = prepareSlides(presentationId);

      if (slides.isEmpty()) {
        handleEmptyPresentation(presentationId);
        return;
      }

      processSlides(slides, presentationId, style);

    } catch (Exception e) {
      handleUnexpectedError(presentationId, e);
    }
  }

  /** Prepare slides for batch processing. */
  private List<Slide> prepareSlides(UUID presentationId) {
    List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
    log.info("Found {} slides for presentation {}", slides.size(), presentationId);
    return slides;
  }

  /** Handle case when presentation has no slides. */
  private void handleEmptyPresentation(UUID presentationId) {
    log.warn("No slides found for presentation: {}", presentationId);
    analysisStatusService.completeAnalysis(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        AnalysisState.COMPLETED,
        "No slides to generate narratives for");
  }

  /** Process all slides for narrative generation. */
  private void processSlides(List<Slide> slides, UUID presentationId, String style) {
    // Start tracking
    log.info("Starting analysis tracking for {} slides", slides.size());
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, slides.size());

    BatchProgress progress = new BatchProgress();

    for (Slide slide : slides) {
      processSingleSlide(slide, presentationId, style, progress, slides.size());
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide for narrative generation. */
  private void processSingleSlide(
      Slide slide, UUID presentationId, String style, BatchProgress progress, int totalSlides) {

    try {
      log.info(
          "Processing slide {} of {} (Slide ID: {}, Number: {})",
          progress.getProcessedCount() + 1,
          totalSlides,
          slide.getId(),
          slide.getSlideNumber());

      // Handle existing narrative
      if (narrativeParsingService.narrativeExists(slide.getId())) {
        handleExistingNarrative(slide.getId(), style);
      }

      // Generate new narrative
      SlideNarrative narrative =
          narrativeGenerationService.generateNarrativeWithPresentationId(
              slide.getId(), style, presentationId);

      progress.incrementCompleted();
      logSuccess(slide, progress, totalSlides, narrative.getId());
      updateProgress(presentationId, progress, totalSlides);

    } catch (Exception e) {
      handleSlideError(slide, presentationId, progress, e);
    }
  }

  /** Handle existing narrative for a slide. */
  private void handleExistingNarrative(UUID slideId, String style) {
    log.info("Narrative already exists for slide {}, regenerating with style: {}", slideId, style);
    narrativeParsingService.deleteExistingNarrative(slideId);
    log.info("Deleted existing narrative for slide {}", slideId);
  }

  /** Log successful narrative generation. */
  private void logSuccess(Slide slide, BatchProgress progress, int totalSlides, UUID narrativeId) {
    log.info(
        "✓ Successfully generated narrative for slide {} ({}/{}) - Narrative ID: {}",
        slide.getSlideNumber(),
        progress.completed,
        totalSlides,
        narrativeId);
  }

  /** Update progress for batch processing. */
  private void updateProgress(UUID presentationId, BatchProgress progress, int totalSlides) {
    String progressMessage =
        String.format("Generated narratives for %d of %d slides", progress.completed, totalSlides);

    log.info("Progress update: {}", progressMessage);
    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        progress.completed,
        progress.failed,
        progressMessage);
  }

  /** Handle error for single slide processing. */
  private void handleSlideError(
      Slide slide, UUID presentationId, BatchProgress progress, Exception e) {
    progress.incrementFailed();

    log.error(
        "✗ Failed to generate narrative for slide {} (number: {}): {}",
        slide.getId(),
        slide.getSlideNumber(),
        e.getMessage(),
        e);

    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        String.format("Slide %d: %s", slide.getSlideNumber(), e.getMessage()));

    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        progress.completed,
        progress.failed,
        String.format("Generated %d narratives, %d failed", progress.completed, progress.failed));
  }

  /** Complete batch processing and update final status. */
  private void completeBatchProcessing(UUID presentationId, BatchProgress progress) {
    AnalysisState finalState = determineFinalState(progress);
    String message =
        String.format(
            "Narrative generation complete: %d succeeded, %d failed",
            progress.completed, progress.failed);

    analysisStatusService.completeAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, finalState, message);

    log.info("=== COMPLETED BATCH NARRATIVE GENERATION ===");
    log.info("Presentation: {}", presentationId);
    log.info("Results: {} succeeded, {} failed", progress.completed, progress.failed);
  }

  /** Determine final analysis state based on progress. */
  private AnalysisState determineFinalState(BatchProgress progress) {
    if (progress.failed == 0) {
      return AnalysisState.COMPLETED;
    } else if (progress.completed == 0) {
      return AnalysisState.FAILED;
    } else {
      return AnalysisState.COMPLETED;
    }
  }

  /** Handle unexpected error in batch processing. */
  private void handleUnexpectedError(UUID presentationId, Exception e) {
    log.error(
        "Unexpected error in batch narrative generation for presentation {}: {}",
        presentationId,
        e.getMessage(),
        e);

    analysisStatusService.completeAnalysis(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        AnalysisState.FAILED,
        "Unexpected error: " + e.getMessage());
  }

  /** Helper class to track batch processing progress. */
  private static class BatchProgress {
    private int completed = 0;
    private int failed = 0;

    void incrementCompleted() {
      completed++;
    }

    void incrementFailed() {
      failed++;
    }

    int getProcessedCount() {
      return completed + failed;
    }
  }
}
