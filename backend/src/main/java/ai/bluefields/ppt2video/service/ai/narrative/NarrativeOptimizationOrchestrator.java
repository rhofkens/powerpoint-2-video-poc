package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import ai.bluefields.ppt2video.service.ai.narrative.optimization.EmotionalEnhancer;
import ai.bluefields.ppt2video.service.ai.narrative.optimization.EmotionalEnhancerFactory;
import ai.bluefields.ppt2video.service.ai.narrative.optimization.TransitionOptimizationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the two-step narrative optimization process: 1. Transition optimization - improves
 * flow between slides 2. Emotional enhancement - adds TTS engine-specific emotional markers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeOptimizationOrchestrator {

  private final TransitionOptimizationService transitionOptimizationService;
  private final EmotionalEnhancerFactory emotionalEnhancerFactory;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final AnalysisStatusService analysisStatusService;

  @Value("${app.ai.narrative.optimization.enabled:true}")
  private boolean optimizationEnabled;

  @Value("${app.ai.narrative.optimization.transition.enabled:true}")
  private boolean transitionOptimizationEnabled;

  @Value("${app.ai.narrative.optimization.emotional.enabled:true}")
  private boolean emotionalEnhancementEnabled;

  /**
   * Optimize all narratives for a presentation. This is called after initial narrative generation
   * completes.
   *
   * @param presentationId The presentation ID
   */
  @Async("virtualThreadExecutor")
  public void optimizeNarratives(UUID presentationId) {
    optimizeNarratives(presentationId, false);
  }

  /**
   * Optimize all narratives for a presentation with force option.
   *
   * @param presentationId The presentation ID
   * @param force If true, re-optimize even if already optimized
   */
  @Async("virtualThreadExecutor")
  public void optimizeNarratives(UUID presentationId, boolean force) {
    if (!optimizationEnabled) {
      log.info("Narrative optimization is disabled");
      return;
    }

    log.info("=== STARTING NARRATIVE OPTIMIZATION ===");
    log.info("Presentation ID: {}", presentationId);
    log.info("Transition optimization: {}", transitionOptimizationEnabled);
    log.info("Emotional enhancement: {}", emotionalEnhancementEnabled);

    try {
      // Load all active narratives for the presentation
      List<SlideNarrative> narratives = loadActiveNarratives(presentationId);

      if (narratives.isEmpty()) {
        log.info("No narratives found for optimization");
        return;
      }

      // Track optimization progress
      analysisStatusService.startAnalysis(
          presentationId,
          AnalysisType.ALL_NARRATIVES_GENERATION,
          narratives.size() * 2); // Two optimization steps

      int totalOptimized = 0;

      // Step 1: Optimize transitions
      if (transitionOptimizationEnabled) {
        totalOptimized += performTransitionOptimization(presentationId, narratives);
      }

      // Step 2: Enhance with emotional markers
      if (emotionalEnhancementEnabled) {
        totalOptimized += performEmotionalEnhancement(presentationId, narratives);
      }

      // Complete optimization
      completeOptimization(presentationId, narratives.size(), totalOptimized);

    } catch (Exception e) {
      handleOptimizationError(presentationId, e);
    }
  }

  /** Load all active narratives for a presentation. */
  private List<SlideNarrative> loadActiveNarratives(UUID presentationId) {
    List<SlideNarrative> narratives =
        slideNarrativeRepository.findBySlide_Presentation_IdAndIsActiveOrderBySlide_SlideNumber(
            presentationId, true);

    log.info("Loaded {} active narratives for optimization", narratives.size());
    return narratives;
  }

  /** Perform transition optimization step. */
  @Transactional
  private int performTransitionOptimization(UUID presentationId, List<SlideNarrative> narratives) {
    log.info("Starting transition optimization for {} narratives", narratives.size());

    try {
      int optimized = transitionOptimizationService.optimizeTransitions(presentationId, narratives);

      log.info(
          "Transition optimization complete: {}/{} narratives optimized",
          optimized,
          narratives.size());

      analysisStatusService.updateProgress(
          presentationId,
          AnalysisType.ALL_NARRATIVES_GENERATION,
          optimized,
          0,
          String.format("Transition optimization: %d/%d completed", optimized, narratives.size()));

      return optimized;

    } catch (Exception e) {
      log.error("Transition optimization failed", e);
      analysisStatusService.addError(
          presentationId,
          AnalysisType.ALL_NARRATIVES_GENERATION,
          "Transition optimization failed: " + e.getMessage());
      return 0;
    }
  }

  /** Perform emotional enhancement step. */
  @Transactional
  private int performEmotionalEnhancement(UUID presentationId, List<SlideNarrative> narratives) {
    log.info("Starting emotional enhancement for {} narratives", narratives.size());

    try {
      EmotionalEnhancer enhancer = emotionalEnhancerFactory.getDefaultEnhancer();

      if (enhancer == null) {
        log.error("No emotional enhancer available for configured TTS engine");
        return 0;
      }

      log.info("Using {} emotional enhancer", enhancer.getEngineType());
      int enhanced = enhancer.enhanceNarratives(narratives);

      log.info(
          "Emotional enhancement complete: {}/{} narratives enhanced", enhanced, narratives.size());

      analysisStatusService.updateProgress(
          presentationId,
          AnalysisType.ALL_NARRATIVES_GENERATION,
          narratives.size() + enhanced,
          0,
          String.format("Emotional enhancement: %d/%d completed", enhanced, narratives.size()));

      return enhanced;

    } catch (Exception e) {
      log.error("Emotional enhancement failed", e);
      analysisStatusService.addError(
          presentationId,
          AnalysisType.ALL_NARRATIVES_GENERATION,
          "Emotional enhancement failed: " + e.getMessage());
      return 0;
    }
  }

  /** Complete the optimization process and update status. */
  private void completeOptimization(UUID presentationId, int totalNarratives, int totalOptimized) {
    String message =
        String.format(
            "Narrative optimization complete: %d optimizations applied to %d narratives",
            totalOptimized, totalNarratives);

    log.info("=== COMPLETED NARRATIVE OPTIMIZATION ===");
    log.info(message);

    analysisStatusService.completeAnalysis(
        presentationId, AnalysisType.ALL_NARRATIVES_GENERATION, AnalysisState.COMPLETED, message);
  }

  /** Handle optimization errors. */
  private void handleOptimizationError(UUID presentationId, Exception e) {
    log.error("Narrative optimization failed for presentation {}", presentationId, e);

    analysisStatusService.completeAnalysis(
        presentationId,
        AnalysisType.ALL_NARRATIVES_GENERATION,
        AnalysisState.FAILED,
        "Optimization failed: " + e.getMessage());
  }
}
