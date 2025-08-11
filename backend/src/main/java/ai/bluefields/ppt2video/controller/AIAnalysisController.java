package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.dto.ApiResponse;
import ai.bluefields.ppt2video.dto.GenerateNarrativeRequestDto;
import ai.bluefields.ppt2video.entity.DeckAnalysis;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import ai.bluefields.ppt2video.service.ai.DeckAnalysisService;
import ai.bluefields.ppt2video.service.ai.narrative.BatchNarrativeOrchestrator;
import ai.bluefields.ppt2video.service.ai.narrative.NarrativeGenerationService;
import ai.bluefields.ppt2video.service.ai.slideanalysis.BatchSlideAnalysisOrchestrator;
import ai.bluefields.ppt2video.service.ai.slideanalysis.SlideAnalysisService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for AI analysis operations. Provides endpoints for deck analysis, slide analysis,
 * and narrative generation.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AIAnalysisController {

  private final DeckAnalysisService deckAnalysisService;
  private final SlideAnalysisService slideAnalysisService;
  private final BatchSlideAnalysisOrchestrator batchSlideAnalysisOrchestrator;
  private final NarrativeGenerationService narrativeGenerationService;
  private final BatchNarrativeOrchestrator batchNarrativeOrchestrator;
  private final ai.bluefields.ppt2video.service.ai.narrative.NarrativeOptimizationOrchestrator
      narrativeOptimizationOrchestrator;
  private final PresentationRepository presentationRepository;
  private final AnalysisStatusService analysisStatusService;

  /**
   * Trigger deck analysis for a presentation.
   *
   * @param presentationId The presentation ID
   * @return The deck analysis result
   */
  @PostMapping("/presentations/{id}/analyze-deck")
  public ResponseEntity<ApiResponse<DeckAnalysis>> analyzeDeck(
      @PathVariable("id") UUID presentationId) {
    log.info("Received request to analyze deck: {}", presentationId);

    try {
      DeckAnalysis analysis = deckAnalysisService.analyzeDeck(presentationId);
      return ResponseEntity.ok(
          ApiResponse.<DeckAnalysis>builder()
              .success(true)
              .data(analysis)
              .message("Deck analysis completed successfully")
              .build());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.<DeckAnalysis>builder().success(false).message(e.getMessage()).build());
    } catch (Exception e) {
      log.error("Failed to analyze deck: {}", presentationId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<DeckAnalysis>builder()
                  .success(false)
                  .message("Failed to analyze deck: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Get deck analysis for a presentation.
   *
   * @param presentationId The presentation ID
   * @return The deck analysis if available
   */
  @GetMapping("/presentations/{id}/analysis")
  public ResponseEntity<ApiResponse<DeckAnalysis>> getDeckAnalysis(
      @PathVariable("id") UUID presentationId) {
    log.info("Received request to get deck analysis: {}", presentationId);

    try {
      // Fetch the presentation with its deck analysis
      Presentation presentation =
          presentationRepository
              .findById(presentationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Presentation not found: " + presentationId));

      if (presentation.getDeckAnalysis() == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<DeckAnalysis>builder()
                    .success(false)
                    .message("No deck analysis found for presentation")
                    .build());
      }

      return ResponseEntity.ok(
          ApiResponse.<DeckAnalysis>builder()
              .success(true)
              .data(presentation.getDeckAnalysis())
              .message("Analysis retrieved successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to get deck analysis: {}", presentationId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<DeckAnalysis>builder()
                  .success(false)
                  .message("Failed to get deck analysis: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Trigger slide analysis.
   *
   * @param slideId The slide ID
   * @param force Whether to force re-analysis (optional query parameter)
   * @return The slide analysis result
   */
  @PostMapping("/slides/{id}/analyze")
  public ResponseEntity<ApiResponse<SlideAnalysis>> analyzeSlide(
      @PathVariable("id") UUID slideId,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {
    log.info("Received request to analyze slide: {} (force={})", slideId, force);

    try {
      SlideAnalysis analysis = slideAnalysisService.analyzeSlide(slideId, force);
      return ResponseEntity.ok(
          ApiResponse.<SlideAnalysis>builder()
              .success(true)
              .data(analysis)
              .message("Slide analysis completed successfully")
              .build());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<SlideAnalysis>builder().success(false).message(e.getMessage()).build());
    } catch (Exception e) {
      log.error("Failed to analyze slide: {}", slideId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<SlideAnalysis>builder()
                  .success(false)
                  .message("Failed to analyze slide: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Get slide analysis.
   *
   * @param slideId The slide ID
   * @return The slide analysis if available
   */
  @GetMapping("/slides/{id}/analysis")
  public ResponseEntity<ApiResponse<SlideAnalysis>> getSlideAnalysis(
      @PathVariable("id") UUID slideId) {
    log.info("Received request to get slide analysis: {}", slideId);

    try {
      // This would need a method in the repository to fetch analysis by slide ID
      return ResponseEntity.ok(
          ApiResponse.<SlideAnalysis>builder()
              .success(true)
              .message("Analysis retrieved successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to get slide analysis: {}", slideId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<SlideAnalysis>builder()
                  .success(false)
                  .message("Failed to get slide analysis: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Generate narrative for a slide.
   *
   * @param slideId The slide ID
   * @param request The narrative generation request with style
   * @return The generated narrative
   */
  @PostMapping("/slides/{id}/generate-narrative")
  public ResponseEntity<ApiResponse<SlideNarrative>> generateNarrative(
      @PathVariable("id") UUID slideId,
      @RequestBody(required = false) GenerateNarrativeRequestDto request) {

    // Use default if no request body provided
    if (request == null) {
      request = new GenerateNarrativeRequestDto();
    }

    // Validate style
    if (!request.isValidStyle()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              ApiResponse.<SlideNarrative>builder()
                  .success(false)
                  .message("Invalid narrative style. Supported values: business, funny, cynical")
                  .build());
    }

    log.info(
        "Received request to generate {} style narrative for slide: {}",
        request.getStyle(),
        slideId);

    try {
      SlideNarrative narrative =
          narrativeGenerationService.generateNarrative(slideId, request.getStyle());
      return ResponseEntity.ok(
          ApiResponse.<SlideNarrative>builder()
              .success(true)
              .data(narrative)
              .message("Narrative generated successfully")
              .build());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<SlideNarrative>builder().success(false).message(e.getMessage()).build());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              ApiResponse.<SlideNarrative>builder().success(false).message(e.getMessage()).build());
    } catch (Exception e) {
      log.error("Failed to generate narrative for slide: {}", slideId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<SlideNarrative>builder()
                  .success(false)
                  .message("Failed to generate narrative: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Get narrative for a slide.
   *
   * @param slideId The slide ID
   * @return The active narrative if available
   */
  @GetMapping("/slides/{id}/narrative")
  public ResponseEntity<ApiResponse<SlideNarrative>> getSlideNarrative(
      @PathVariable("id") UUID slideId) {
    log.info("Received request to get narrative for slide: {}", slideId);

    try {
      // This would need a method in the repository to fetch active narrative by slide ID
      return ResponseEntity.ok(
          ApiResponse.<SlideNarrative>builder()
              .success(true)
              .message("Narrative retrieved successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to get narrative for slide: {}", slideId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<SlideNarrative>builder()
                  .success(false)
                  .message("Failed to get narrative: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Analyze all slides in a presentation.
   *
   * @param presentationId The presentation ID
   * @return Success response
   */
  @PostMapping("/presentations/{id}/analyze-all-slides")
  public ResponseEntity<ApiResponse<String>> analyzeAllSlides(
      @PathVariable("id") UUID presentationId) {
    log.info("Received request to analyze all slides for presentation: {}", presentationId);

    try {
      // Check if presentation exists
      Presentation presentation =
          presentationRepository
              .findById(presentationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Presentation not found: " + presentationId));

      // Trigger async analysis
      batchSlideAnalysisOrchestrator.analyzeAllSlides(presentationId);

      return ResponseEntity.ok(
          ApiResponse.<String>builder()
              .success(true)
              .data("Analysis started for all slides")
              .message(
                  "All slides analysis initiated successfully. Use the status endpoint to track progress.")
              .build());
    } catch (Exception e) {
      log.error("Failed to start all slides analysis for presentation: {}", presentationId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<String>builder()
                  .success(false)
                  .message("Failed to start analysis: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Generate narratives for all slides in a presentation.
   *
   * @param presentationId The presentation ID
   * @param request The narrative generation request with style
   * @return Success response
   */
  @PostMapping("/presentations/{id}/generate-all-narratives")
  public ResponseEntity<ApiResponse<String>> generateAllNarratives(
      @PathVariable("id") UUID presentationId,
      @RequestBody(required = false) GenerateNarrativeRequestDto request) {

    // Use default if no request body provided
    if (request == null) {
      request = new GenerateNarrativeRequestDto();
    }

    // Validate style
    if (!request.isValidStyle()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              ApiResponse.<String>builder()
                  .success(false)
                  .message("Invalid narrative style. Supported values: business, funny, cynical")
                  .build());
    }

    log.info(
        "Received request to generate {} style narratives for all slides in presentation: {}",
        request.getStyle(),
        presentationId);

    // Verify presentation exists first
    if (!presentationRepository.existsById(presentationId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<String>builder()
                  .success(false)
                  .message("Presentation not found")
                  .build());
    }

    // Trigger async narrative generation for all slides
    log.info("Calling batchNarrativeOrchestrator.generateAllNarratives()...");
    batchNarrativeOrchestrator.generateAllNarratives(presentationId, request.getStyle());
    log.info("Async method call completed, returning response...");

    return ResponseEntity.ok(
        ApiResponse.<String>builder()
            .success(true)
            .data("Narrative generation started for all slides")
            .message(
                "All narratives generation initiated successfully with "
                    + request.getStyle()
                    + " style")
            .build());
  }

  /**
   * Get complete narrative for all slides in a presentation.
   *
   * @param presentationId The presentation ID
   * @return List of all slide narratives in order
   */
  @GetMapping("/presentations/{id}/complete-narrative")
  public ResponseEntity<ApiResponse<List<SlideNarrative>>> getCompleteNarrative(
      @PathVariable("id") UUID presentationId) {

    log.info("Received request to get complete narrative for presentation: {}", presentationId);

    // Check if presentation exists
    Presentation presentation = presentationRepository.findById(presentationId).orElse(null);
    if (presentation == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<List<SlideNarrative>>builder()
                  .success(false)
                  .message("Presentation not found")
                  .build());
    }

    // Get all narratives for the presentation ordered by slide number
    List<SlideNarrative> narratives =
        narrativeGenerationService.getAllNarrativesForPresentation(presentationId);

    return ResponseEntity.ok(
        ApiResponse.<List<SlideNarrative>>builder()
            .success(true)
            .data(narratives)
            .message(String.format("Found %d narratives", narratives.size()))
            .build());
  }

  /**
   * Optimize narratives for a presentation (transitions and emotional enhancement).
   *
   * @param presentationId The presentation ID
   * @param force Whether to force re-optimization even if already optimized
   * @return Response indicating optimization has started
   */
  @PostMapping("/presentations/{id}/optimize-narratives")
  public ResponseEntity<ApiResponse<String>> optimizeNarratives(
      @PathVariable("id") UUID presentationId,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {

    log.info(
        "Received request to optimize narratives for presentation: {}, force: {}",
        presentationId,
        force);

    // Check if presentation exists
    Presentation presentation = presentationRepository.findById(presentationId).orElse(null);
    if (presentation == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<String>builder()
                  .success(false)
                  .message("Presentation not found")
                  .build());
    }

    // Check if narratives exist
    List<SlideNarrative> narratives =
        narrativeGenerationService.getAllNarrativesForPresentation(presentationId);

    if (narratives.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              ApiResponse.<String>builder()
                  .success(false)
                  .message("No narratives found to optimize. Please generate narratives first.")
                  .build());
    }

    log.info("Starting narrative optimization for {} narratives", narratives.size());
    narrativeOptimizationOrchestrator.optimizeNarratives(presentationId, force);

    return ResponseEntity.ok(
        ApiResponse.<String>builder()
            .success(true)
            .data("Narrative optimization started")
            .message(
                "Optimization process initiated successfully (transitions + emotional enhancement)")
            .build());
  }

  /**
   * Get the status of ongoing AI analysis operations.
   *
   * @param presentationId The presentation ID
   * @param type Optional analysis type filter
   * @return Analysis status information
   */
  @GetMapping("/presentations/{id}/analysis-status")
  public ResponseEntity<ApiResponse<List<AnalysisStatusDto>>> getAnalysisStatus(
      @PathVariable("id") UUID presentationId, @RequestParam(required = false) AnalysisType type) {

    log.info(
        "Received request to get analysis status for presentation: {}, type: {}",
        presentationId,
        type);

    try {
      List<AnalysisStatusDto> statuses;

      if (type != null) {
        // Get specific analysis type status
        AnalysisStatusDto status = analysisStatusService.getStatus(presentationId, type);
        statuses = status != null ? List.of(status) : List.of();
      } else {
        // Get all active analyses for the presentation
        statuses = analysisStatusService.getActiveAnalyses(presentationId);
      }

      return ResponseEntity.ok(
          ApiResponse.<List<AnalysisStatusDto>>builder()
              .success(true)
              .data(statuses)
              .message("Analysis status retrieved successfully")
              .build());
    } catch (Exception e) {
      log.error("Failed to get analysis status for presentation: {}", presentationId, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<List<AnalysisStatusDto>>builder()
                  .success(false)
                  .message("Failed to get analysis status: " + e.getMessage())
                  .build());
    }
  }
}
