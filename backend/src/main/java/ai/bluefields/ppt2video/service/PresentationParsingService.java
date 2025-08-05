package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.parsing.ParserFactory;
import ai.bluefields.ppt2video.service.parsing.PowerPointParser;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for asynchronously parsing PowerPoint presentations. This service extracts
 * metadata and structure without generating images.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresentationParsingService {

  @Value("${app.rendering.default-renderer:MSGRAPH}")
  private String defaultRenderer;

  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;
  private final ParserFactory parserFactory;
  private final SlideRenderingService renderingService;

  /**
   * Asynchronously parses a presentation to extract metadata and structure. This method runs in a
   * separate virtual thread and does not generate images.
   *
   * @param presentationId the ID of the presentation to parse
   */
  @Async("virtualThreadExecutor")
  @Transactional
  public void parsePresentation(UUID presentationId) {
    log.info("Starting async parsing for presentation: {}", presentationId);

    try {
      // 1. Update status to PARSING
      Presentation presentation =
          presentationRepository
              .findById(presentationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Presentation not found: " + presentationId));

      presentation.setStatus(Presentation.Status.PARSING);
      presentation.setProcessingStatus(Presentation.ProcessingStatus.PARSING);
      presentation.setParsingStartedAt(LocalDateTime.now());
      presentationRepository.save(presentation);

      // 2. Load file
      File presentationFile = new File(presentation.getFilePath());
      if (!presentationFile.exists()) {
        throw new IllegalStateException(
            "Presentation file not found: " + presentation.getFilePath());
      }

      // 3. Extract metadata using parser
      PowerPointParser parser = parserFactory.getParser();
      log.info("Using parser: {} for presentation: {}", parser.getParserName(), presentationId);

      List<Slide> slides = parser.parsePresentation(presentationFile, presentation);

      // 4. Save slides to database (without images)
      for (Slide slide : slides) {
        // Ensure slide doesn't have image information yet
        slide.setImagePath(null);
        slide.setImageGeneratedAt(null);
        slide.setImageWidth(null);
        slide.setImageHeight(null);
        slide.setRenderingStatus(Slide.RenderingStatus.PENDING);
      }
      slideRepository.saveAll(slides);

      // 5. Update status to PARSED
      presentation.setStatus(Presentation.Status.PARSED);
      presentation.setParsingCompletedAt(LocalDateTime.now());
      presentationRepository.save(presentation);

      log.info(
          "Successfully parsed presentation: {} with {} slides", presentationId, slides.size());

      // 6. Trigger rendering after parsing completes
      renderingService.renderPresentation(presentationId, defaultRenderer);

    } catch (Exception e) {
      log.error("Error parsing presentation {}: {}", presentationId, e.getMessage(), e);

      // Update status to FAILED
      try {
        Presentation presentation = presentationRepository.findById(presentationId).orElse(null);
        if (presentation != null) {
          presentation.setStatus(Presentation.Status.FAILED);
          presentation.setProcessingStatus(Presentation.ProcessingStatus.FAILED);
          presentation.setErrorMessage("Parsing failed: " + e.getMessage());
          presentation.setParsingCompletedAt(LocalDateTime.now());
          presentationRepository.save(presentation);
        }
      } catch (Exception updateError) {
        log.error("Failed to update presentation status: {}", updateError.getMessage());
      }
    }
  }

  /**
   * Checks if a presentation has been parsed.
   *
   * @param presentationId the presentation ID
   * @return true if parsed, false otherwise
   */
  public boolean isParsed(UUID presentationId) {
    return presentationRepository
        .findById(presentationId)
        .map(
            p ->
                p.getStatus() == Presentation.Status.PARSED
                    || p.getStatus() == Presentation.Status.RENDERING
                    || p.getStatus() == Presentation.Status.COMPLETED)
        .orElse(false);
  }

  /**
   * Gets the parsing progress for a presentation.
   *
   * @param presentationId the presentation ID
   * @return parsing progress information
   */
  public ParsingProgress getParsingProgress(UUID presentationId) {
    Presentation presentation =
        presentationRepository
            .findById(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));

    int slideCount = slideRepository.countByPresentationId(presentationId);

    return new ParsingProgress(
        presentation.getStatus().name(),
        slideCount,
        presentation.getParsingStartedAt(),
        presentation.getParsingCompletedAt(),
        presentation.getErrorMessage());
  }

  /** DTO for parsing progress information. */
  public record ParsingProgress(
      String status,
      int slideCount,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      String errorMessage) {}
}
