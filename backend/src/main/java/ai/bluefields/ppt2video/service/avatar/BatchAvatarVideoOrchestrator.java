package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import ai.bluefields.ppt2video.dto.AvatarVideoRequest;
import ai.bluefields.ppt2video.dto.AvatarVideoResponse;
import ai.bluefields.ppt2video.dto.avatar.BatchAvatarVideoRequest;
import ai.bluefields.ppt2video.dto.avatar.BatchAvatarVideoResponse;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.repository.*;
import ai.bluefields.ppt2video.service.ai.AnalysisStatusService;
import ai.bluefields.ppt2video.service.avatar.providers.HeyGenConfiguration;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates batch generation of avatar videos for all slides in a presentation. Uses Virtual
 * Threads for concurrent processing with configurable concurrency limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAvatarVideoOrchestrator {

  private final SlideRepository slideRepository;
  private final AvatarVideoService avatarVideoService;
  private final AvatarVideoRepository avatarVideoRepository;
  private final AnalysisStatusService analysisStatusService;
  private final PresentationRepository presentationRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final HeyGenConfiguration heyGenConfiguration;

  @Autowired
  @Qualifier("virtualThreadExecutor")
  private Executor virtualThreadExecutor;

  @Value("${app.avatar.batch.max-concurrent:5}")
  private int maxConcurrentGenerations;

  @Value("${app.avatar.batch.timeout-per-video-seconds:180}")
  private int timeoutPerVideoSeconds;

  @Value("${app.avatar.batch.retry-attempts:2}")
  private int retryAttempts;

  /**
   * Count how many slides need avatar video generation.
   *
   * @param presentationId The presentation ID
   * @param request The batch generation request parameters
   * @return The number of slides that will have videos generated
   */
  @Transactional(readOnly = true)
  public int countSlidesNeedingVideos(UUID presentationId, BatchAvatarVideoRequest request) {
    log.info("Counting slides needing videos for presentation: {}", presentationId);

    // Fetch presentation with slides
    Presentation presentation =
        presentationRepository
            .findByIdWithSlides(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));

    // Determine which slides to process
    List<Slide> slidesToProcess = getSlidesToProcess(presentation, request);

    if (slidesToProcess.isEmpty()) {
      return 0;
    }

    // If regenerating existing videos, all slides need generation
    if (request.isRegenerateExisting()) {
      return slidesToProcess.size();
    }

    // Check for existing videos and count only those that need generation
    Map<UUID, AvatarVideo> existingVideos = getExistingVideos(slidesToProcess);
    int slidesNeedingGeneration = slidesToProcess.size() - existingVideos.size();

    log.info(
        "Presentation {} has {} slides, {} already have videos, {} need generation",
        presentationId,
        slidesToProcess.size(),
        existingVideos.size(),
        slidesNeedingGeneration);

    return slidesNeedingGeneration;
  }

  /**
   * Asynchronously generates avatar videos for all slides in a presentation.
   *
   * @param presentationId The presentation ID
   * @param request The batch generation request parameters
   */
  @Async("virtualThreadExecutor")
  public void generateAllAvatarVideos(UUID presentationId, BatchAvatarVideoRequest request) {
    log.info(
        "Starting batch avatar video generation for presentation: {} with concurrency: {}",
        presentationId,
        maxConcurrentGenerations);
    log.debug(
        "Request parameters - regenerateExisting: {}, avatarId: {}, usePublishedAudio: {}",
        request.isRegenerateExisting(),
        request.getAvatarId(),
        request.isUsePublishedAudio());

    String analysisId = null;
    try {
      // Initialize progress tracking
      log.debug("Creating analysis status for tracking");
      analysisId =
          analysisStatusService.createAnalysisStatus(
              presentationId, AnalysisType.ALL_AVATAR_VIDEOS_GENERATION);
      log.info("Created analysis status with ID: {}", analysisId);

      // Process the batch
      log.info("Beginning batch processing for presentation: {}", presentationId);
      BatchAvatarVideoResponse response = processBatch(presentationId, request, analysisId);
      log.info(
          "Batch video initiation completed - Initiated: {}, Failed: {}, Skipped: {}",
          response.getSuccessCount(),
          response.getFailedCount(),
          response.getSkippedCount());

      // Update final status
      analysisStatusService.updateAnalysisStatus(
          analysisId,
          response.getSuccessCount() > 0 && response.getFailedCount() == 0
              ? AnalysisState.COMPLETED
              : response.getFailedCount() > 0 ? AnalysisState.FAILED : AnalysisState.COMPLETED,
          String.format(
              "Batch avatar video generation initiated. Started: %d, Failed: %d, Skipped: %d",
              response.getSuccessCount(), response.getFailedCount(), response.getSkippedCount()));

      log.info(
          "Completed batch avatar video initiation for presentation: {}. Initiated: {}, Failed: {}, Skipped: {}. "
              + "Note: Videos are still being processed by HeyGen and will complete asynchronously.",
          presentationId,
          response.getSuccessCount(),
          response.getFailedCount(),
          response.getSkippedCount());

    } catch (Exception e) {
      log.error("Failed to generate batch avatar videos for presentation: {}", presentationId, e);
      if (analysisId != null) {
        analysisStatusService.updateAnalysisStatus(
            analysisId, AnalysisState.FAILED, "Batch generation failed: " + e.getMessage());
      }
    }
  }

  /**
   * Processes the batch avatar video generation with concurrent execution.
   *
   * @param presentationId The presentation ID
   * @param request The batch generation request
   * @param analysisId The analysis tracking ID
   * @return The batch response with results
   */
  @Transactional(readOnly = true)
  private BatchAvatarVideoResponse processBatch(
      UUID presentationId, BatchAvatarVideoRequest request, String analysisId) {

    // Fetch presentation with slides eagerly loaded
    log.debug("Fetching presentation with slides for ID: {}", presentationId);
    Presentation presentation =
        presentationRepository
            .findByIdWithSlides(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));
    log.info(
        "Found presentation '{}' with {} slides",
        presentation.getTitle(),
        presentation.getSlides().size());

    // Determine which slides to process
    log.debug("Determining slides to process");
    List<Slide> slidesToProcess = getSlidesToProcess(presentation, request);
    log.info("Selected {} slides for processing", slidesToProcess.size());

    if (slidesToProcess.isEmpty()) {
      log.info("No slides to process for presentation: {}", presentationId);
      return BatchAvatarVideoResponse.builder()
          .presentationId(presentationId)
          .totalSlides(0)
          .successCount(0)
          .failedCount(0)
          .skippedCount(0)
          .startTime(LocalDateTime.now())
          .endTime(LocalDateTime.now())
          .build();
    }

    // Check for existing videos and filter if not regenerating
    log.debug("Checking for existing avatar videos");
    Map<UUID, AvatarVideo> existingVideos = getExistingVideos(slidesToProcess);
    log.info("Found {} existing avatar videos", existingVideos.size());

    List<Slide> slidesNeedingGeneration =
        filterSlidesNeedingGeneration(
            slidesToProcess, existingVideos, request.isRegenerateExisting());
    log.info(
        "Will generate avatar videos for {} slides (regenerateExisting={})",
        slidesNeedingGeneration.size(),
        request.isRegenerateExisting());

    // Update initial progress
    analysisStatusService.updateProgress(
        analysisId,
        slidesToProcess.size(),
        0,
        0,
        String.format(
            "Starting video generation for %d slides (%d already have videos)",
            slidesNeedingGeneration.size(),
            slidesToProcess.size() - slidesNeedingGeneration.size()));

    // Process slides concurrently
    log.info(
        "Starting concurrent processing with {} max concurrent generations",
        maxConcurrentGenerations);
    return processSlidesWithConcurrency(
        presentationId,
        slidesNeedingGeneration,
        slidesToProcess.size(),
        existingVideos.size(),
        request,
        analysisId);
  }

  /** Processes slides with controlled concurrency using Semaphore and Virtual Threads. */
  private BatchAvatarVideoResponse processSlidesWithConcurrency(
      UUID presentationId,
      List<Slide> slides,
      int totalSlides,
      int skippedCount,
      BatchAvatarVideoRequest request,
      String analysisId) {

    LocalDateTime startTime = LocalDateTime.now();
    Semaphore semaphore = new Semaphore(maxConcurrentGenerations);
    ConcurrentHashMap<UUID, CompletableFuture<AvatarVideoResult>> futures =
        new ConcurrentHashMap<>();

    // Track progress
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failedCount = new AtomicInteger(0);
    AtomicInteger processedCount = new AtomicInteger(0);

    // Submit all slide processing tasks
    log.info("Submitting {} slide processing tasks", slides.size());
    for (Slide slide : slides) {
      log.debug("Submitting task for slide {} (number: {})", slide.getId(), slide.getSlideNumber());
      CompletableFuture<AvatarVideoResult> future =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  // Acquire permit for concurrency control
                  log.trace("Attempting to acquire semaphore permit for slide {}", slide.getId());
                  semaphore.acquire();
                  log.debug(
                      "Acquired permit for slide {} (available permits: {})",
                      slide.getId(),
                      semaphore.availablePermits());
                  try {
                    log.info("Starting avatar video generation for slide {}", slide.getId());

                    // Generate avatar video for this slide
                    long generationStartTime = System.currentTimeMillis();
                    AvatarVideoResult result = generateAvatarVideoForSlide(slide, request);
                    long duration = System.currentTimeMillis() - generationStartTime;
                    log.info(
                        "Completed slide {} in {}ms - Success: {}",
                        slide.getId(),
                        duration,
                        result.isSuccess());

                    // Update progress
                    int processed = processedCount.incrementAndGet();
                    if (result.isSuccess()) {
                      successCount.incrementAndGet();
                    } else {
                      failedCount.incrementAndGet();
                    }

                    analysisStatusService.updateProgress(
                        analysisId,
                        totalSlides,
                        processed + skippedCount,
                        failedCount.get(),
                        String.format(
                            "Initiated video %d of %d (Initiated: %d, Failed: %d)",
                            processed, slides.size(), successCount.get(), failedCount.get()));

                    return result;
                  } finally {
                    semaphore.release();
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  log.error("Interrupted while processing slide: {}", slide.getId());
                  return new AvatarVideoResult(slide.getId(), false, "Processing interrupted");
                }
              },
              virtualThreadExecutor);

      futures.put(slide.getId(), future);
    }

    // Wait for all tasks to complete with timeout
    try {
      CompletableFuture<Void> allFutures =
          CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));
      long timeoutSeconds = (long) slides.size() * timeoutPerVideoSeconds;
      log.info(
          "Waiting for all {} tasks to complete with timeout of {} seconds",
          futures.size(),
          timeoutSeconds);
      allFutures.get(timeoutSeconds, TimeUnit.SECONDS);
      log.info("All tasks completed successfully");
    } catch (TimeoutException e) {
      log.error("Batch avatar video generation timed out for presentation: {}", presentationId);
      futures.values().forEach(f -> f.cancel(true));
    } catch (Exception e) {
      log.error("Error waiting for batch avatar video generation", e);
    }

    // Collect results
    List<AvatarVideoResult> results =
        futures.values().stream()
            .map(
                f -> {
                  try {
                    return f.getNow(null);
                  } catch (Exception e) {
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    LocalDateTime endTime = LocalDateTime.now();

    return BatchAvatarVideoResponse.builder()
        .presentationId(presentationId)
        .totalSlides(totalSlides)
        .successCount(successCount.get())
        .failedCount(failedCount.get())
        .skippedCount(skippedCount)
        .startTime(startTime)
        .endTime(endTime)
        .durationSeconds(Duration.between(startTime, endTime).getSeconds())
        .results(results)
        .build();
  }

  /** Generates avatar video for a single slide with retry logic. */
  private AvatarVideoResult generateAvatarVideoForSlide(
      Slide slide, BatchAvatarVideoRequest request) {

    int attempts = 0;
    Exception lastException = null;

    while (attempts < retryAttempts) {
      attempts++;
      try {
        // Check if slide has active narrative
        log.debug("Checking for active narrative for slide: {}", slide.getId());
        Optional<SlideNarrative> narrative =
            slideNarrativeRepository.findBySlideIdAndIsActiveTrue(slide.getId());

        if (narrative.isEmpty()) {
          log.warn("No active narrative found for slide: {}", slide.getId());
          return new AvatarVideoResult(slide.getId(), false, "No active narrative available");
        }
        log.debug(
            "Found active narrative for slide {} with {} characters",
            slide.getId(),
            narrative.get().getNarrativeText() != null
                ? narrative.get().getNarrativeText().length()
                : 0);

        // Determine avatar ID based on narrative style or request
        String avatarId = request.getAvatarId();
        if (avatarId == null || avatarId.isEmpty()) {
          avatarId = determineAvatarByStyle(slide.getPresentation());
        }

        // Create avatar video request
        AvatarVideoRequest avatarRequest =
            AvatarVideoRequest.builder()
                .presentationId(slide.getPresentation().getId())
                .slideId(slide.getId())
                .avatarId(avatarId)
                .backgroundColor(
                    request.getBackgroundColor() != null
                        ? request.getBackgroundColor()
                        : heyGenConfiguration.getAvatar().getBackgroundColor())
                .usePublishedAudio(request.isUsePublishedAudio())
                .customAudioUrl(request.getCustomAudioUrl())
                .build();

        // Generate avatar video
        log.info(
            "Calling avatar video service for slide {} with avatar ID: {}",
            slide.getId(),
            avatarId);
        AvatarVideoResponse avatarResponse = avatarVideoService.generateAvatarVideo(avatarRequest);
        log.info(
            "Avatar video service returned response for slide {} with video ID: {}",
            slide.getId(),
            avatarResponse.getId());

        // Get the created avatar video from repository
        AvatarVideo avatarVideo =
            avatarVideoRepository
                .findById(avatarResponse.getId())
                .orElseThrow(() -> new RuntimeException("Avatar video not found after creation"));

        // Note: The video is created but still processing in HeyGen
        // The AvatarVideoMonitorService will handle completion tracking
        log.info(
            "Successfully initiated avatar video generation for slide: {} (status: {})",
            slide.getId(),
            avatarVideo.getStatus());

        // Return success only if we successfully started the generation
        // The actual completion will be tracked by the monitor service
        boolean initiated =
            avatarVideo.getStatus() == AvatarGenerationStatusType.PENDING
                || avatarVideo.getStatus() == AvatarGenerationStatusType.PROCESSING;
        return new AvatarVideoResult(slide.getId(), initiated, null, avatarVideo.getId());

      } catch (Exception e) {
        lastException = e;
        log.error(
            "Failed to generate avatar video for slide: {} (attempt {}/{})",
            slide.getId(),
            attempts,
            retryAttempts,
            e);

        if (attempts < retryAttempts) {
          try {
            Thread.sleep(2000); // Wait 2 seconds before retry
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }

    return new AvatarVideoResult(
        slide.getId(),
        false,
        lastException != null ? lastException.getMessage() : "Generation failed after retries");
  }

  /** Determines the avatar ID based on the presentation's narrative style. */
  private String determineAvatarByStyle(Presentation presentation) {
    // Use the HeyGenConfiguration to get the appropriate avatar ID for the narrative style
    String narrativeStyle = presentation.getNarrativeStyle();
    return heyGenConfiguration.getAvatar().getAvatarIdForStyle(narrativeStyle);
  }

  /** Gets the list of slides to process based on the request. */
  private List<Slide> getSlidesToProcess(
      Presentation presentation, BatchAvatarVideoRequest request) {

    List<Slide> allSlides =
        presentation.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getSlideNumber))
            .collect(Collectors.toList());

    // If specific slides are requested, filter them
    if (request.getSelectedSlideIds() != null && !request.getSelectedSlideIds().isEmpty()) {
      Set<UUID> selectedIds = new HashSet<>(request.getSelectedSlideIds());
      return allSlides.stream()
          .filter(slide -> selectedIds.contains(slide.getId()))
          .collect(Collectors.toList());
    }

    return allSlides;
  }

  /** Gets existing avatar videos for the given slides. */
  private Map<UUID, AvatarVideo> getExistingVideos(List<Slide> slides) {
    List<UUID> slideIds = slides.stream().map(Slide::getId).collect(Collectors.toList());

    List<AvatarVideo> existingVideos =
        avatarVideoRepository.findBySlideIdInAndStatus(
            slideIds, AvatarGenerationStatusType.COMPLETED);

    return existingVideos.stream()
        .collect(Collectors.toMap(AvatarVideo::getSlideId, v -> v, (v1, v2) -> v2));
  }

  /** Filters slides that need generation based on existing videos and regeneration flag. */
  private List<Slide> filterSlidesNeedingGeneration(
      List<Slide> slides, Map<UUID, AvatarVideo> existingVideos, boolean regenerateExisting) {

    if (regenerateExisting) {
      return slides; // Process all slides
    }

    // Only process slides without existing videos
    return slides.stream()
        .filter(slide -> !existingVideos.containsKey(slide.getId()))
        .collect(Collectors.toList());
  }

  /** Result class for individual avatar video generation. */
  public static class AvatarVideoResult {
    private final UUID slideId;
    private final boolean success;
    private final String errorMessage;
    private final UUID avatarVideoId;

    public AvatarVideoResult(UUID slideId, boolean success, String errorMessage) {
      this(slideId, success, errorMessage, null);
    }

    public AvatarVideoResult(
        UUID slideId, boolean success, String errorMessage, UUID avatarVideoId) {
      this.slideId = slideId;
      this.success = success;
      this.errorMessage = errorMessage;
      this.avatarVideoId = avatarVideoId;
    }

    // Getters
    public UUID getSlideId() {
      return slideId;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public UUID getAvatarVideoId() {
      return avatarVideoId;
    }
  }
}
