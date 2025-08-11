package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
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
 * Orchestrator service for batch narrative generation with parallel processing support. Handles
 * concurrent generation of narratives for multiple slides with configurable concurrency limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchNarrativeOrchestrator {

  private final SlideRepository slideRepository;
  private final NarrativeGenerationService narrativeGenerationService;
  private final NarrativeParsingService narrativeParsingService;
  private final AnalysisStatusService analysisStatusService;
  private final NarrativeOptimizationOrchestrator narrativeOptimizationOrchestrator;

  @Autowired private AsyncTaskExecutor virtualThreadExecutor;

  @Value("${app.ai.narrative.parallel.enabled:true}")
  private boolean parallelProcessingEnabled;

  @Value("${app.ai.narrative.parallel.max-concurrent:3}")
  private int maxConcurrentGenerations;

  @Value("${app.ai.narrative.parallel.timeout-per-slide-seconds:90}")
  private int timeoutPerSlideSeconds;

  /**
   * Generate narratives for all slides in a presentation asynchronously with parallel processing.
   * Each narrative is generated in its own transaction to allow real-time progress visibility.
   *
   * @param presentationId The ID of the presentation
   * @param style The narrative style to use
   */
  @Async("virtualThreadExecutor")
  public void generateAllNarratives(UUID presentationId, String style) {
    log.info("=== STARTING BATCH NARRATIVE GENERATION ===");
    log.info("Presentation ID: {}", presentationId);
    log.info("Style: {}", style);
    log.info(
        "Parallel processing: {}, Max concurrent: {}",
        parallelProcessingEnabled,
        maxConcurrentGenerations);
    log.info("Thread: {}", Thread.currentThread().getName());

    try {
      List<Slide> slides = prepareSlides(presentationId);

      if (slides.isEmpty()) {
        handleEmptyPresentation(presentationId);
        return;
      }

      if (parallelProcessingEnabled && slides.size() > 1) {
        processSlidesConcurrently(slides, presentationId, style);
      } else {
        processSlidesSequentially(slides, presentationId, style);
      }

    } catch (Exception e) {
      handleUnexpectedError(presentationId, e);
    }
  }

  /** Process slides concurrently with controlled parallelism. */
  private void processSlidesConcurrently(List<Slide> slides, UUID presentationId, String style) {
    log.info(
        "Starting PARALLEL narrative generation for {} slides with max concurrency: {}",
        slides.size(),
        maxConcurrentGenerations);

    // Initialize concurrency control
    Semaphore semaphore = new Semaphore(maxConcurrentGenerations);

    // Thread-safe progress tracking
    ConcurrentProgress progress = new ConcurrentProgress(slides.size());

    // Start tracking
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, slides.size());

    // Create futures for all slides
    List<CompletableFuture<Void>> futures =
        slides.stream()
            .map(slide -> processSlideAsync(slide, presentationId, style, semaphore, progress))
            .collect(Collectors.toList());

    // Wait for all to complete or timeout
    CompletableFuture<Void> allFutures =
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

    try {
      // Total timeout calculation - considering narrative generation takes longer
      long totalTimeoutSeconds =
          Math.max(
              slides.size() * timeoutPerSlideSeconds / maxConcurrentGenerations,
              timeoutPerSlideSeconds * 2);

      log.info(
          "Waiting for all narratives to complete with timeout: {} seconds", totalTimeoutSeconds);
      allFutures.get(totalTimeoutSeconds, TimeUnit.SECONDS);

    } catch (TimeoutException e) {
      log.error("Batch narrative generation timed out after waiting for all slides");
      handleTimeout(futures, presentationId, progress);
    } catch (Exception e) {
      log.error("Error waiting for batch completion", e);
      handleBatchError(e, presentationId, progress);
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide narrative asynchronously. */
  private CompletableFuture<Void> processSlideAsync(
      Slide slide,
      UUID presentationId,
      String style,
      Semaphore semaphore,
      ConcurrentProgress progress) {

    return CompletableFuture.runAsync(
        () -> {
          String slideContext =
              String.format("Slide %d (ID: %s)", slide.getSlideNumber(), slide.getId());

          try {
            // Acquire permit (blocks if at max concurrency)
            log.debug("Waiting for permit to generate narrative for {}", slideContext);
            semaphore.acquire();
            progress.startProcessing();

            try {
              log.info("Starting narrative generation for {}", slideContext);

              // Check prerequisites
              if (!canGenerateNarrative(slide)) {
                log.warn("{} cannot generate narrative - missing analysis", slideContext);
                progress.incrementSkipped();
                updateProgressAsync(presentationId, progress, "Skipped - no analysis");
                return;
              }

              // Skip if already has active narrative (unless force regenerate)
              if (hasActiveNarrative(slide)) {
                log.debug("{} already has active narrative, skipping", slideContext);
                progress.incrementSkipped();
                updateProgressAsync(presentationId, progress, "Skipped - already has narrative");
                return;
              }

              // Generate narrative with timeout
              CompletableFuture<SlideNarrative> narrativeFuture =
                  CompletableFuture.supplyAsync(
                      () ->
                          narrativeGenerationService.generateNarrativeWithPresentationId(
                              slide.getId(), style, presentationId),
                      virtualThreadExecutor);

              SlideNarrative narrative =
                  narrativeFuture.get(timeoutPerSlideSeconds, TimeUnit.SECONDS);

              if (narrative != null) {
                progress.incrementCompleted();
                log.info("✓ Successfully generated narrative for {}", slideContext);
                updateProgressAsync(presentationId, progress, "Successfully generated narrative");
              } else {
                throw new RuntimeException("Narrative generation returned null");
              }

            } finally {
              semaphore.release();
              log.debug("Released permit for {}", slideContext);
            }

          } catch (TimeoutException e) {
            log.error(
                "✗ Timeout generating narrative for {} after {} seconds",
                slideContext,
                timeoutPerSlideSeconds);
            progress.incrementFailed();
            handleSlideError(
                slide,
                presentationId,
                progress,
                new RuntimeException(
                    "Narrative generation timeout after " + timeoutPerSlideSeconds + " seconds"));

          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("✗ Interrupted while generating narrative for {}", slideContext);
            progress.incrementFailed();
            handleSlideError(slide, presentationId, progress, e);

          } catch (Exception e) {
            log.error("✗ Failed to generate narrative for {}: {}", slideContext, e.getMessage());
            progress.incrementFailed();
            handleSlideError(slide, presentationId, progress, e);
          }
        },
        virtualThreadExecutor);
  }

  /** Process slides sequentially (fallback or when parallel is disabled). */
  private void processSlidesSequentially(List<Slide> slides, UUID presentationId, String style) {
    log.info("Starting SEQUENTIAL narrative generation for {} slides", slides.size());

    // Start tracking
    analysisStatusService.startAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, slides.size());

    BatchProgress progress = new BatchProgress();

    for (Slide slide : slides) {
      processSingleSlide(slide, presentationId, style, progress, slides.size());
    }

    completeBatchProcessing(presentationId, progress);
  }

  /** Process a single slide for narrative generation (sequential mode). */
  private void processSingleSlide(
      Slide slide, UUID presentationId, String style, BatchProgress progress, int totalSlides) {

    try {
      // Check prerequisites
      if (!canGenerateNarrative(slide)) {
        log.warn(
            "Slide {} (number: {}) cannot generate narrative - missing analysis",
            slide.getId(),
            slide.getSlideNumber());
        progress.incrementSkipped();
        updateProgress(presentationId, progress, totalSlides, "Skipped - no analysis");
        return;
      }

      // Check for existing narrative
      if (hasActiveNarrative(slide)) {
        log.debug("Slide {} already has active narrative, deactivating old", slide.getId());
        narrativeParsingService.deactivateExistingNarrative(slide.getId());
      }

      log.info(
          "Generating narrative {} of {} (Slide ID: {}, Number: {})",
          progress.getProcessedCount() + 1,
          totalSlides,
          slide.getId(),
          slide.getSlideNumber());

      // Generate narrative (includes its own error handling)
      SlideNarrative narrative =
          narrativeGenerationService.generateNarrativeWithPresentationId(
              slide.getId(), style, presentationId);

      if (narrative != null) {
        progress.incrementCompleted();
        logSuccess(slide, progress, totalSlides);
        updateProgress(presentationId, progress, totalSlides, "Successfully generated narrative");
      } else {
        throw new RuntimeException("Narrative generation returned null");
      }

    } catch (Exception e) {
      handleSlideError(slide, presentationId, progress, e);
    }
  }

  /** Check if slide can generate narrative. */
  private boolean canGenerateNarrative(Slide slide) {
    return slide.getSlideAnalysis() != null;
  }

  /** Check if slide has active narrative. */
  private boolean hasActiveNarrative(Slide slide) {
    // Check if there are any active narratives in the collection
    return slide.getSlideNarratives() != null
        && slide.getSlideNarratives().stream()
            .anyMatch(narrative -> Boolean.TRUE.equals(narrative.getIsActive()));
  }

  /** Update progress for concurrent processing. */
  private void updateProgressAsync(
      UUID presentationId, ConcurrentProgress progress, String action) {
    ProgressSnapshot snapshot = progress.getSnapshot();

    int totalProcessed = snapshot.completed + snapshot.failed;
    int actuallyGenerated = snapshot.completed - snapshot.skipped;

    String message =
        String.format(
            "Progress: %d/%d processed (%d generated, %d skipped, %d failed) - %d in progress",
            totalProcessed,
            snapshot.total,
            actuallyGenerated,
            snapshot.skipped,
            snapshot.failed,
            snapshot.inProgress);

    log.debug("{}: {}", action, message);

    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        totalProcessed,
        snapshot.failed,
        message);
  }

  /** Handle timeout for batch processing. */
  private void handleTimeout(
      List<CompletableFuture<Void>> futures, UUID presentationId, ConcurrentProgress progress) {

    log.error("Batch narrative generation timeout - cancelling remaining tasks");

    // Cancel remaining futures
    futures.forEach(
        future -> {
          if (!future.isDone()) {
            future.cancel(true);
          }
        });

    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        "Batch processing timeout - some narratives may not have been generated");
  }

  /** Handle unexpected batch error. */
  private void handleBatchError(Exception e, UUID presentationId, ConcurrentProgress progress) {
    log.error("Batch narrative generation error", e);

    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        "Batch processing error: " + e.getMessage());
  }

  /** Prepare slides for batch processing. */
  private List<Slide> prepareSlides(UUID presentationId) {
    // Use eager loading to fetch narratives to avoid lazy loading issues in parallel processing
    List<Slide> slides = slideRepository.findByPresentationIdWithNarratives(presentationId);
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
        "No slides to process");
  }

  /** Log successful narrative generation. */
  private void logSuccess(Slide slide, BatchProgress progress, int totalSlides) {
    log.info(
        "✓ Successfully generated narrative for slide {} ({}/{}) - Slide Number: {}",
        slide.getId(),
        progress.completed,
        totalSlides,
        slide.getSlideNumber());
  }

  /** Update progress for batch processing (sequential mode). */
  private void updateProgress(
      UUID presentationId, BatchProgress progress, int totalSlides, String action) {

    String progressMessage =
        String.format("Generated %d of %d narratives", progress.completed, totalSlides);

    log.debug("{}: {}", action, progressMessage);

    analysisStatusService.updateProgress(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        progress.completed,
        progress.failed,
        progressMessage);
  }

  /** Handle error for single slide processing. */
  private void handleSlideError(Slide slide, UUID presentationId, Object progress, Exception e) {

    // Increment failed count for sequential progress
    if (progress instanceof BatchProgress) {
      ((BatchProgress) progress).incrementFailed();
    }

    log.error(
        "✗ Failed to generate narrative for slide {} (number: {}): {}",
        slide.getId(),
        slide.getSlideNumber(),
        e.getMessage());

    // Update slide status
    try {
      slide.setProcessingStatus(Slide.ProcessingStatus.FAILED);
      slideRepository.save(slide);
    } catch (Exception saveError) {
      log.error("Failed to update slide status", saveError);
    }

    // Record the error
    analysisStatusService.addError(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
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

      int actuallyGenerated = snapshot.completed - snapshot.skipped;
      message =
          String.format(
              "Narrative generation complete (PARALLEL): %d generated, %d skipped, %d failed (Concurrency: %d)",
              actuallyGenerated, snapshot.skipped, snapshot.failed, maxConcurrentGenerations);

      log.info("=== COMPLETED PARALLEL NARRATIVE GENERATION ===");
      log.info(
          "Results: {} generated, {} skipped, {} failed",
          actuallyGenerated,
          snapshot.skipped,
          snapshot.failed);

    } else {
      BatchProgress batchProgress = (BatchProgress) progress;
      finalState = determineFinalState(batchProgress);

      int actuallyGenerated = batchProgress.completed - batchProgress.skipped;
      message =
          String.format(
              "Narrative generation complete (SEQUENTIAL): %d generated, %d skipped, %d failed",
              actuallyGenerated, batchProgress.skipped, batchProgress.failed);

      log.info("=== COMPLETED SEQUENTIAL NARRATIVE GENERATION ===");
      log.info(
          "Results: {} generated, {} skipped, {} failed",
          actuallyGenerated,
          batchProgress.skipped,
          batchProgress.failed);
    }

    analysisStatusService.completeAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, finalState, message);

    log.info("Presentation: {}", presentationId);

    // Trigger optimization if narratives were successfully generated
    if (finalState == AnalysisState.COMPLETED
        || (progress instanceof ConcurrentProgress
            && ((ConcurrentProgress) progress).getSnapshot().completed > 0)
        || (progress instanceof BatchProgress && ((BatchProgress) progress).completed > 0)) {

      log.info("Triggering narrative optimization for presentation: {}", presentationId);
      narrativeOptimizationOrchestrator.optimizeNarratives(presentationId);
    }
  }

  /** Determine final state based on progress. */
  private AnalysisState determineFinalState(BatchProgress progress) {
    if (progress.failed == 0) {
      return AnalysisState.COMPLETED;
    } else if (progress.completed == 0) {
      return AnalysisState.FAILED;
    } else {
      return AnalysisState.COMPLETED;
    }
  }

  /** Determine final state based on progress snapshot. */
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
      completed++; // Skipped counts as completed
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
