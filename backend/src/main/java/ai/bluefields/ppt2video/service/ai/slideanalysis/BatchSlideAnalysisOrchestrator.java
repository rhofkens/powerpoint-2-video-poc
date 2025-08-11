package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Orchestrator service for batch slide analysis with parallel processing support. Handles
 * concurrent analysis of multiple slides with configurable concurrency limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSlideAnalysisOrchestrator {

  private final SlideRepository slideRepository;
  private final SlideAnalysisService slideAnalysisService;
  private final AnalysisStatusService analysisStatusService;

  @Autowired private AsyncTaskExecutor virtualThreadExecutor;

  @Value("${app.ai.analysis.parallel.enabled:true}")
  private boolean parallelProcessingEnabled;

  @Value("${app.ai.analysis.parallel.max-concurrent:5}")
  private int maxConcurrentAnalyses;

  @Value("${app.ai.analysis.parallel.timeout-per-slide-seconds:60}")
  private int timeoutPerSlideSeconds;

  @Value("${app.ai.analysis.parallel.batch-size:10}")
  private int batchSize;

  /**
   * Analyze all slides for a presentation asynchronously with parallel processing. Each slide is
   * analyzed in its own transaction to allow real-time progress visibility.
   *
   * @param presentationId The ID of the presentation
   */
  @Async("virtualThreadExecutor")
  public void analyzeAllSlides(UUID presentationId) {
    log.info("=== STARTING BATCH SLIDE ANALYSIS ===");
    log.info("Presentation ID: {}", presentationId);
    log.info(
        "Parallel processing: {}, Max concurrent: {}",
        parallelProcessingEnabled,
        maxConcurrentAnalyses);
    log.info("Thread: {}", Thread.currentThread().getName());

    try {
      List<Slide> slides = prepareSlides(presentationId);

      if (slides.isEmpty()) {
        handleEmptyPresentation(presentationId);
        return;
      }

      if (parallelProcessingEnabled && slides.size() > 1) {
        processSlidesConcurrently(slides, presentationId);
      } else {
        processSlidesSequentially(slides, presentationId);
      }

    } catch (Exception e) {
      handleUnexpectedError(presentationId, e);
    }
  }

  /** Process slides concurrently with controlled parallelism. */
  private void processSlidesConcurrently(List<Slide> slides, UUID presentationId) {
    log.info(
        "Starting PARALLEL processing of {} slides with max concurrency: {}",
        slides.size(),
        maxConcurrentAnalyses);

    // Initialize concurrency control
    Semaphore semaphore = new Semaphore(maxConcurrentAnalyses);

    // Thread-safe progress tracking
    ConcurrentProgress progress = new ConcurrentProgress(slides.size());

    // Start tracking
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, slides.size());

    // Create futures for all slides
    List<CompletableFuture<Void>> futures =
        slides.stream()
            .map(slide -> processSlideAsync(slide, presentationId, semaphore, progress))
            .collect(Collectors.toList());

    // Wait for all to complete or timeout
    CompletableFuture<Void> allFutures =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      // Total timeout is number of slides * timeout per slide / max concurrent
      // This ensures we don't timeout too early for large presentations
      long totalTimeoutSeconds =
          Math.max(
              slides.size() * timeoutPerSlideSeconds / maxConcurrentAnalyses,
              timeoutPerSlideSeconds * 2);

      log.info("Waiting for all slides to complete with timeout: {} seconds", totalTimeoutSeconds);
      allFutures.get(totalTimeoutSeconds, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      log.error("Batch analysis timed out after waiting for all slides");
      handleTimeout(futures, presentationId, progress);
    } catch (Exception e) {
      log.error("Error waiting for batch completion", e);
      handleBatchError(e, presentationId, progress);
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide asynchronously. */
  private CompletableFuture<Void> processSlideAsync(
      Slide slide, UUID presentationId, Semaphore semaphore, ConcurrentProgress progress) {

    return CompletableFuture.runAsync(
        () -> {
          String slideContext =
              String.format("Slide %d (ID: %s)", slide.getSlideNumber(), slide.getId());

          try {
            // Acquire permit (blocks if at max concurrency)
            log.debug("Waiting for permit to analyze {}", slideContext);
            semaphore.acquire();
            progress.startProcessing();

            try {
              log.info("Starting analysis of {}", slideContext);

              // Skip if already analyzed
              if (slide.getSlideAnalysis() != null) {
                log.debug("{} already analyzed, skipping", slideContext);
                progress.incrementSkipped();
                updateProgressAsync(presentationId, progress, "Skipped already analyzed slide");
                return;
              }

              // Perform analysis with timeout
              CompletableFuture<Void> analysisFuture =
                  CompletableFuture.runAsync(
                      () -> slideAnalysisService.analyzeSlide(slide.getId()),
                      virtualThreadExecutor);

              analysisFuture.get(timeoutPerSlideSeconds, TimeUnit.SECONDS);

              progress.incrementCompleted();
              log.info("✓ Successfully analyzed {}", slideContext);
              updateProgressAsync(presentationId, progress, "Successfully analyzed slide");

            } finally {
              semaphore.release();
              log.debug("Released permit for {}", slideContext);
            }

          } catch (TimeoutException e) {
            log.error(
                "✗ Timeout analyzing {} after {} seconds", slideContext, timeoutPerSlideSeconds);
            progress.incrementFailed();
            handleSlideError(
                slide,
                presentationId,
                progress,
                new RuntimeException(
                    "Analysis timeout after " + timeoutPerSlideSeconds + " seconds"));

          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("✗ Interrupted while analyzing {}", slideContext);
            progress.incrementFailed();
            handleSlideError(slide, presentationId, progress, e);

          } catch (Exception e) {
            log.error("✗ Failed to analyze {}: {}", slideContext, e.getMessage());
            progress.incrementFailed();
            handleSlideError(slide, presentationId, progress, e);
          }
        },
        virtualThreadExecutor);
  }

  /** Process slides sequentially (fallback or when parallel is disabled). */
  private void processSlidesSequentially(List<Slide> slides, UUID presentationId) {
    log.info("Starting SEQUENTIAL processing of {} slides", slides.size());

    // Start tracking
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, slides.size());

    BatchProgress progress = new BatchProgress();

    for (Slide slide : slides) {
      processSingleSlide(slide, presentationId, progress, slides.size());
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide for analysis (sequential mode). */
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

  /** Update progress for concurrent processing. */
  private void updateProgressAsync(
      UUID presentationId, ConcurrentProgress progress, String action) {
    ProgressSnapshot snapshot = progress.getSnapshot();

    int totalProcessed = snapshot.completed + snapshot.failed;
    int actuallyAnalyzed = snapshot.completed - snapshot.skipped;

    String message =
        String.format(
            "Progress: %d/%d processed (%d analyzed, %d skipped, %d failed) - %d in progress",
            totalProcessed,
            snapshot.total,
            actuallyAnalyzed,
            snapshot.skipped,
            snapshot.failed,
            snapshot.inProgress);

    log.debug("{}: {}", action, message);

    analysisStatusService.updateProgress(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, totalProcessed, snapshot.failed, message);
  }

  /** Handle timeout for batch processing. */
  private void handleTimeout(
      List<CompletableFuture<Void>> futures, UUID presentationId, ConcurrentProgress progress) {

    log.error("Batch processing timeout - cancelling remaining tasks");

    // Cancel remaining futures
    futures.forEach(
        future -> {
          if (!future.isDone()) {
            future.cancel(true);
          }
        });

    // Update status
    ProgressSnapshot snapshot = progress.getSnapshot();
    String message =
        String.format(
            "Analysis timed out: %d completed, %d failed, %d skipped, %d incomplete",
            snapshot.completed, snapshot.failed, snapshot.skipped, snapshot.inProgress);

    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        "Batch processing timeout - some slides may not have been analyzed");
  }

  /** Handle unexpected batch error. */
  private void handleBatchError(Exception e, UUID presentationId, ConcurrentProgress progress) {
    log.error("Batch processing error", e);

    ProgressSnapshot snapshot = progress.getSnapshot();
    String message =
        String.format(
            "Batch error: %d completed, %d failed before error",
            snapshot.completed, snapshot.failed);

    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_SLIDES_ANALYSIS,
        "Batch processing error: " + e.getMessage());
  }

  /** Prepare slides for batch processing. */
  private List<Slide> prepareSlides(UUID presentationId) {
    // Use eager loading to fetch analysis to avoid lazy loading issues in parallel processing
    List<Slide> slides = slideRepository.findByPresentationIdWithAnalysis(presentationId);
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

  /** Log successful slide analysis. */
  private void logSuccess(Slide slide, BatchProgress progress, int totalSlides) {
    log.info(
        "✓ Successfully analyzed slide {} ({}/{}) - Slide Number: {}",
        slide.getId(),
        progress.completed,
        totalSlides,
        slide.getSlideNumber());
  }

  /** Update progress for batch processing (sequential mode). */
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
  private void handleSlideError(Slide slide, UUID presentationId, Object progress, Exception e) {

    log.error(
        "✗ Failed to analyze slide {} (number: {}): {}",
        slide.getId(),
        slide.getSlideNumber(),
        e.getMessage());

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
  }

  /** Complete batch processing and update final status. */
  private void completeBatchProcessing(UUID presentationId, Object progress) {
    AnalysisState finalState;
    String message;

    if (progress instanceof ConcurrentProgress) {
      ConcurrentProgress concurrentProgress = (ConcurrentProgress) progress;
      ProgressSnapshot snapshot = concurrentProgress.getSnapshot();
      finalState = determineFinalState(snapshot);

      int actuallyAnalyzed = snapshot.completed - snapshot.skipped;
      message =
          String.format(
              "Analysis complete (PARALLEL): %d analyzed, %d skipped, %d failed (Concurrency: %d)",
              actuallyAnalyzed, snapshot.skipped, snapshot.failed, maxConcurrentAnalyses);

      log.info("=== COMPLETED PARALLEL BATCH SLIDE ANALYSIS ===");
      log.info(
          "Results: {} analyzed, {} skipped, {} failed",
          actuallyAnalyzed,
          snapshot.skipped,
          snapshot.failed);

    } else {
      BatchProgress batchProgress = (BatchProgress) progress;
      finalState = determineFinalState(batchProgress);

      int actuallyAnalyzed = batchProgress.completed - batchProgress.skipped;
      message =
          String.format(
              "Analysis complete (SEQUENTIAL): %d analyzed, %d skipped, %d failed",
              actuallyAnalyzed, batchProgress.skipped, batchProgress.failed);

      log.info("=== COMPLETED SEQUENTIAL BATCH SLIDE ANALYSIS ===");
      log.info(
          "Results: {} analyzed, {} skipped, {} failed",
          actuallyAnalyzed,
          batchProgress.skipped,
          batchProgress.failed);
    }

    analysisStatusService.completeAnalysis(
        presentationId, AnalysisType.ALL_SLIDES_ANALYSIS, finalState, message);

    log.info("Presentation: {}", presentationId);
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

  /** Determine final analysis state based on progress snapshot. */
  private AnalysisState determineFinalState(ProgressSnapshot snapshot) {
    if (snapshot.failed == 0) {
      return AnalysisState.COMPLETED;
    } else if (snapshot.completed == 0) {
      return AnalysisState.FAILED;
    } else {
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

  /** Helper class to track batch processing progress (sequential mode). */
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

  /** Thread-safe progress tracking for concurrent processing. */
  private static class ConcurrentProgress {
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    private final AtomicInteger inProgress = new AtomicInteger(0);
    private final int total;

    ConcurrentProgress(int total) {
      this.total = total;
    }

    void startProcessing() {
      inProgress.incrementAndGet();
    }

    void incrementCompleted() {
      completed.incrementAndGet();
      inProgress.decrementAndGet();
    }

    void incrementFailed() {
      failed.incrementAndGet();
      inProgress.decrementAndGet();
    }

    void incrementSkipped() {
      skipped.incrementAndGet();
      completed.incrementAndGet(); // Skipped counts as completed
      inProgress.decrementAndGet();
    }

    ProgressSnapshot getSnapshot() {
      return new ProgressSnapshot(
          completed.get(), failed.get(), skipped.get(), inProgress.get(), total);
    }
  }

  /** Immutable snapshot of progress at a point in time. */
  private static class ProgressSnapshot {
    final int completed;
    final int failed;
    final int skipped;
    final int inProgress;
    final int total;

    ProgressSnapshot(int completed, int failed, int skipped, int inProgress, int total) {
      this.completed = completed;
      this.failed = failed;
      this.skipped = skipped;
      this.inProgress = inProgress;
      this.total = total;
    }
  }
}
