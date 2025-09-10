package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.ColorPaletteDto;
import ai.bluefields.ppt2video.dto.IntroVideoRequest;
import ai.bluefields.ppt2video.dto.IntroVideoResponse;
import ai.bluefields.ppt2video.dto.veo.VeoGenerationResponse;
import ai.bluefields.ppt2video.dto.veo.VeoPrompt;
import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.DeckAnalysis;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.exception.ResourceNotFoundException;
import ai.bluefields.ppt2video.repository.DeckAnalysisRepository;
import ai.bluefields.ppt2video.repository.IntroVideoRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main orchestrator service for intro video generation. Coordinates color extraction, prompt
 * generation, and Veo API interaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntroVideoService {

  private final IntroVideoRepository introVideoRepository;
  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;
  private final DeckAnalysisRepository deckAnalysisRepository;
  private final ColorExtractionService colorExtractionService;
  private final VeoApiService veoApiService;
  private final VeoPromptBuilder veoPromptBuilder;
  private final VeoPromptLLMService veoPromptLLMService;
  private final IntroVideoMonitorService monitorService;
  private final R2AssetService r2AssetService;
  private final ObjectMapper objectMapper;

  @Value("${google.veo.model:veo-3.0-fast-generate-001}")
  private String veoModelVersion;

  @Value("${app.ai.veo-prompt.llm-enabled:true}")
  private boolean llmPromptEnabled;

  /**
   * Generate an intro video for a presentation.
   *
   * @param request the intro video generation request
   * @return response with intro video details
   */
  @Transactional
  public IntroVideoResponse generateIntroVideo(IntroVideoRequest request) {
    log.info("Starting intro video generation for presentation: {}", request.getPresentationId());

    try {
      // 1. Validate presentation exists and has deck analysis
      Presentation presentation = validatePresentation(request.getPresentationId());
      DeckAnalysis deckAnalysis = validateAndGetDeckAnalysis(presentation);

      // 2. Check for existing intro video if not regenerating
      if (!request.isRegenerate()) {
        Optional<IntroVideo> existingVideo =
            introVideoRepository.findLatestByPresentationId(request.getPresentationId());

        if (existingVideo.isPresent()
            && existingVideo.get().getStatus() == AvatarGenerationStatusType.PROCESSING) {
          log.info(
              "Intro video already being generated for presentation: {}",
              request.getPresentationId());
          return convertToResponse(existingVideo.get());
        }
      }

      // 3. Get first slide and extract colors
      Slide firstSlide = getFirstSlide(presentation);
      ColorPaletteDto colorPalette = colorExtractionService.extractColors(firstSlide);

      // 4. Build Veo prompt using LLM or template
      VeoPrompt veoPrompt;
      String promptGenerationMethod = "TEMPLATE";
      long promptGenerationTime = 0;

      if (llmPromptEnabled) {
        try {
          log.info("Using LLM to generate Veo prompt for presentation: {}", presentation.getId());
          long startTime = System.currentTimeMillis();
          veoPrompt = veoPromptLLMService.generatePromptWithLLM(deckAnalysis, colorPalette);
          promptGenerationTime = System.currentTimeMillis() - startTime;
          promptGenerationMethod = "LLM";
          log.info("LLM prompt generation successful, took {} ms", promptGenerationTime);
        } catch (Exception e) {
          log.warn("LLM prompt generation failed, falling back to template: {}", e.getMessage());
          veoPrompt = veoPromptBuilder.buildPrompt(deckAnalysis, colorPalette);
        }
      } else {
        log.info("Using template-based prompt generation (LLM disabled)");
        veoPrompt = veoPromptBuilder.buildPrompt(deckAnalysis, colorPalette);
      }

      // Override aspect ratio and resolution if provided
      if (request.getAspectRatio() != null) {
        veoPrompt.setAspectRatio(request.getAspectRatio());
      }
      if (request.getResolution() != null) {
        veoPrompt.setResolution(request.getResolution());
      }

      // 5. Create IntroVideo entity with prompt tracking
      IntroVideo introVideo = createIntroVideoEntity(presentation, veoPrompt, colorPalette);
      introVideo.setPromptGenerationMethod(promptGenerationMethod);
      introVideo.setPromptGenerationTimeMs(promptGenerationTime);
      introVideo.setPromptTextFull(veoPrompt.getPromptText());

      // 6. Call Veo API to start generation
      VeoGenerationResponse veoResponse = veoApiService.generateVideo(veoPrompt);

      // 7. Update entity with Veo response
      introVideo.setVeoGenerationId(veoResponse.getGenerationId());
      introVideo.setStatus(AvatarGenerationStatusType.PROCESSING);
      introVideo.setStartedAt(LocalDateTime.now());

      // Store generation metadata
      String metadata = objectMapper.writeValueAsString(veoResponse);
      introVideo.setGenerationMetadata(metadata);

      introVideo = introVideoRepository.save(introVideo);

      // 8. Start async monitoring
      monitorService.startMonitoring(introVideo.getId(), veoResponse.getGenerationId());

      log.info(
          "Intro video generation initiated successfully. ID: {}, Veo ID: {}",
          introVideo.getId(),
          veoResponse.getGenerationId());

      return convertToResponse(introVideo);

    } catch (ResourceNotFoundException e) {
      log.error("Resource not found: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error generating intro video", e);
      throw new ProcessingException("Failed to generate intro video: " + e.getMessage());
    }
  }

  /**
   * Get the status of an intro video generation.
   *
   * @param introVideoId the intro video ID
   * @return intro video response with current status
   */
  @Transactional(readOnly = true)
  public IntroVideoResponse getIntroVideoStatus(UUID introVideoId) {
    IntroVideo introVideo =
        introVideoRepository
            .findById(introVideoId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Intro video not found: " + introVideoId));

    return convertToResponse(introVideo);
  }

  /**
   * Get the latest intro video for a presentation.
   *
   * @param presentationId the presentation ID
   * @return intro video response if exists
   */
  @Transactional(readOnly = true)
  public Optional<IntroVideoResponse> getLatestIntroVideo(UUID presentationId) {
    return introVideoRepository
        .findLatestByPresentationId(presentationId)
        .map(this::convertToResponse);
  }

  /**
   * Cancel an intro video generation.
   *
   * @param introVideoId the intro video ID
   */
  @Transactional
  public void cancelGeneration(UUID introVideoId) {
    IntroVideo introVideo =
        introVideoRepository
            .findById(introVideoId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Intro video not found: " + introVideoId));

    if (introVideo.getStatus() == AvatarGenerationStatusType.PROCESSING
        || introVideo.getStatus() == AvatarGenerationStatusType.PENDING) {

      introVideo.setStatus(AvatarGenerationStatusType.CANCELLED);
      introVideo.setCompletedAt(LocalDateTime.now());
      introVideoRepository.save(introVideo);

      // Stop monitoring
      monitorService.stopMonitoring(introVideoId);

      log.info("Cancelled intro video generation: {}", introVideoId);
    }
  }

  /** Validate that presentation exists. */
  private Presentation validatePresentation(UUID presentationId) {
    return presentationRepository
        .findById(presentationId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Presentation not found: " + presentationId));
  }

  /** Validate and get deck analysis for presentation. */
  private DeckAnalysis validateAndGetDeckAnalysis(Presentation presentation) {
    return deckAnalysisRepository
        .findByPresentationId(presentation.getId())
        .orElseThrow(
            () ->
                new ProcessingException(
                    "Deck analysis not found. Please run AI analysis first for presentation: "
                        + presentation.getId()));
  }

  /** Get the first slide of a presentation. */
  private Slide getFirstSlide(Presentation presentation) {
    return slideRepository.findByPresentationIdOrderBySlideNumber(presentation.getId()).stream()
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No slides found for presentation: " + presentation.getId()));
  }

  /** Create IntroVideo entity. */
  private IntroVideo createIntroVideoEntity(
      Presentation presentation, VeoPrompt veoPrompt, ColorPaletteDto colorPalette) {
    try {
      String colorPaletteJson = objectMapper.writeValueAsString(colorPalette);

      return IntroVideo.builder()
          .presentation(presentation)
          .status(AvatarGenerationStatusType.PENDING)
          .promptText(veoPrompt.getPromptText())
          .negativePrompt(veoPrompt.getNegativePrompt())
          .colorPalette(colorPaletteJson)
          .veoModelVersion(veoModelVersion)
          .aspectRatio(veoPrompt.getAspectRatio())
          .resolution(veoPrompt.getResolution())
          .build();
    } catch (Exception e) {
      log.error("Error creating intro video entity", e);
      throw new ProcessingException("Failed to create intro video entity");
    }
  }

  /** Convert IntroVideo entity to response DTO. */
  private IntroVideoResponse convertToResponse(IntroVideo introVideo) {
    try {
      ColorPaletteDto colorPalette = null;
      if (introVideo.getColorPalette() != null) {
        colorPalette = objectMapper.readValue(introVideo.getColorPalette(), ColorPaletteDto.class);
      }

      return IntroVideoResponse.builder()
          .id(introVideo.getId())
          .presentationId(introVideo.getPresentationId())
          .veoGenerationId(introVideo.getVeoGenerationId())
          .status(introVideo.getStatus())
          .promptText(introVideo.getPromptText())
          .negativePrompt(introVideo.getNegativePrompt())
          .colorPalette(colorPalette)
          .veoModelVersion(introVideo.getVeoModelVersion())
          .aspectRatio(introVideo.getAspectRatio())
          .resolution(introVideo.getResolution())
          .durationSeconds(introVideo.getDurationSeconds())
          .googleVideoUrl(introVideo.getGoogleVideoUrl())
          .publishedUrl(
              introVideo.getR2Asset() != null
                  ? r2AssetService.regeneratePresignedUrl(introVideo.getR2Asset().getId())
                  : null)
          .r2AssetId(introVideo.getR2Asset() != null ? introVideo.getR2Asset().getId() : null)
          .errorMessage(introVideo.getErrorMessage())
          .startedAt(introVideo.getStartedAt())
          .completedAt(introVideo.getCompletedAt())
          .createdAt(introVideo.getCreatedAt())
          .updatedAt(introVideo.getUpdatedAt())
          .build();
    } catch (Exception e) {
      log.error("Error converting intro video to response", e);
      // Return basic response without color palette if parsing fails
      return IntroVideoResponse.builder()
          .id(introVideo.getId())
          .presentationId(introVideo.getPresentationId())
          .status(introVideo.getStatus())
          .publishedUrl(
              introVideo.getR2Asset() != null
                  ? r2AssetService.regeneratePresignedUrl(introVideo.getR2Asset().getId())
                  : null)
          .errorMessage(introVideo.getErrorMessage())
          .createdAt(introVideo.getCreatedAt())
          .build();
    }
  }
}
