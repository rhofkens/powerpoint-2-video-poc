package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service for batch slide analysis. Handles async processing of multiple slides and
 * progress tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSlideAnalysisOrchestrator {

  private final SlideRepository slideRepository;
  private final SlideAnalysisService slideAnalysisService;
  private final AnalysisStatusService analysisStatusService;

  /**
   * Analyze all slides for a presentation asynchronously. Each slide is analyzed in its own
   * transaction to allow real-time progress visibility.
   *
   * @param presentationId The ID of the presentation
   */
  @Async("virtualThreadExecutor")
  public void analyzeAllSlides(UUID presentationId) {
    log.info("=== STARTING BATCH SLIDE ANALYSIS ===");
    log.info("Presentation ID: {}", presentationId);
    log.info("Thread: {}", Thread.currentThread().getName());

    try {
      List<Slide> slides = prepareSlides(presentationId);

      if (slides.isEmpty()) {
        handleEmptyPresentation(presentationId);
        return;
      }

      processSlides(slides, presentationId);

    } catch (Exception e) {
      handleUnexpectedError(presentationId, e);
    }
  }

  /** Prepare slides for batch processing. */
  private List<Slide> prepareSlides(UUID presentationId) {
    // This will use the default transaction from the repository
    List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
    log.info("Found {} slides for presentation {}", slides.size(), presentationId);
    return slides;
  }

  /** Handle case when presentation has no slides. */
  private void handleEmptyPresentation(UUID presentationId) {
    log.warn("No slides found for presentation: {}", presentationId);
    analysisStatusService.completeAnalysis(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        AnalysisState.COMPLETED,
        "No slides to analyze");
  }

  /** Process all slides for analysis. */
  private void processSlides(List<Slide> slides, UUID presentationId) {
    // Start tracking
    log.info("Starting analysis tracking for {} slides", slides.size());
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, slides.size());

    BatchProgress progress = new BatchProgress();

    for (Slide slide : slides) {
      processSingleSlide(slide, presentationId, progress, slides.size());
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide for analysis. */
  private void processSingleSlide(
      Slide slide, UUID presentationId, BatchProgress progress, int totalSlides) {

    try {
      // Skip if already analyzed
      if (slide.getSlideAnalysis() != null) {
        log.debug("Slide {} already analyzed, skipping", slide.getId());
        progress.incrementSkipped();
        updateProgress(presentationId, progress, totalSlides, "Skipped already analyzed slide");
        return;
      }

      log.info(
          "Analyzing slide {} of {} (Slide ID: {}, Number: {})",
          progress.getProcessedCount() + 1,
          totalSlides,
          slide.getId(),
          slide.getSlideNumber());

      // Perform analysis
      slideAnalysisService.analyzeSlide(slide.getId());

      progress.incrementCompleted();
      logSuccess(slide, progress, totalSlides);
      updateProgress(presentationId, progress, totalSlides, "Successfully analyzed slide");

    } catch (Exception e) {
      handleSlideError(slide, presentationId, progress, e);
    }
  }

  /** Log successful slide analysis. */
  private void logSuccess(Slide slide, BatchProgress progress, int totalSlides) {
    log.info(
        "✓ Successfully analyzed slide {} ({}/{}) - Slide Number: {}",
        slide.getId(),
        progress.completed,
        totalSlides,
        slide.getSlideNumber());
  }

  /** Update progress for batch processing. */
  private void updateProgress(
      UUID presentationId, BatchProgress progress, int totalSlides, String action) {

    String progressMessage =
        String.format("Analyzed %d of %d slides", progress.completed, totalSlides);

    log.debug("{}: {}", action, progressMessage);

    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        progress.completed,
        progress.failed,
        progressMessage);
  }

  /** Handle error for single slide processing. */
  private void handleSlideError(
      Slide slide, UUID presentationId, BatchProgress progress, Exception e) {

    progress.incrementFailed();

    log.error(
        "✗ Failed to analyze slide {} (number: {}): {}",
        slide.getId(),
        slide.getSlideNumber(),
        e.getMessage(),
        e);

    // Update slide status to failed
    try {
      slide.setProcessingStatus(Slide.ProcessingStatus.FAILED);
      slideRepository.save(slide);
    } catch (Exception saveError) {
      log.error("Failed to update slide status to FAILED", saveError);
    }

    // Record the error
    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        String.format("Slide %d: %s", slide.getSlideNumber(), e.getMessage()));

    // Update progress
    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        progress.completed,
        progress.failed,
        String.format("Analyzed %d slides, %d failed", progress.completed, progress.failed));
  }

  /** Complete batch processing and update final status. */
  private void completeBatchProcessing(UUID presentationId, BatchProgress progress) {
    AnalysisState finalState = determineFinalState(progress);

    // Calculate actual analyzed count (completed minus skipped)
    int actuallyAnalyzed = progress.completed - progress.skipped;

    String message =
        String.format(
            "Analysis complete: %d analyzed, %d skipped, %d failed",
            actuallyAnalyzed, progress.skipped, progress.failed);

    analysisStatusService.completeAnalysis(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, finalState, message);

    log.info("=== COMPLETED BATCH SLIDE ANALYSIS ===");
    log.info("Presentation: {}", presentationId);
    log.info(
        "Results: {} analyzed, {} skipped, {} failed",
        actuallyAnalyzed,
        progress.skipped,
        progress.failed);
  }

  /** Determine final analysis state based on progress. */
  private AnalysisState determineFinalState(BatchProgress progress) {
    if (progress.failed == 0) {
      return AnalysisState.COMPLETED;
    } else if (progress.completed == 0) {
      return AnalysisState.FAILED;
    } else {
      // Partial success
      return AnalysisState.COMPLETED;
    }
  }

  /** Handle unexpected error in batch processing. */
  private void handleUnexpectedError(UUID presentationId, Exception e) {
    log.error(
        "Unexpected error in batch slide analysis for presentation {}: {}",
        presentationId,
        e.getMessage(),
        e);

    analysisStatusService.completeAnalysis(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        AnalysisState.FAILED,
        "Unexpected error: " + e.getMessage());
  }

  /** Helper class to track batch processing progress. */
  private static class BatchProgress {
    private int completed = 0;
    private int failed = 0;
    private int skipped = 0;

    void incrementCompleted() {
      completed++;
    }

    void incrementFailed() {
      failed++;
    }

    void incrementSkipped() {
      skipped++;
      completed++; // Skipped counts as completed for progress tracking
    }

    int getProcessedCount() {
      return completed + failed;
    }
  }
}
