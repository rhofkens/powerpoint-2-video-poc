package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.rendering.RenderingStrategy;
import ai.bluefields.ppt2video.service.rendering.RenderingStrategyFactory;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Service responsible for coordinating the asynchronous rendering of presentation slides. This
 * service manages the rendering workflow using the strategy pattern.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlideRenderingService {

  private final PresentationRepository presentationRepository;
  private final SlideRepository slideRepository;
  private final RenderingStrategyFactory strategyFactory;
  private final PlaceholderImageService placeholderService;
  private final R2AssetService r2AssetService;

  @Value("${app.storage.presentations-path}")
  private String presentationsBasePath;

  @Value("${app.rendering.default-width:1920}")
  private int defaultWidth;

  @Value("${app.rendering.default-height:1080}")
  private int defaultHeight;

  /**
   * Asynchronously renders all slides in a presentation using the specified renderer. This method
   * runs in a separate virtual thread.
   *
   * @param presentationId the ID of the presentation to render
   * @param rendererName the name of the renderer to use
   */
  @Async("virtualThreadExecutor")
  @Transactional
  public void renderPresentation(UUID presentationId, String rendererName) {
    log.info(
        "Starting async rendering for presentation: {} with renderer: {}",
        presentationId,
        rendererName);

    try {
      // 1. Update status to RENDERING
      Presentation presentation =
          presentationRepository
              .findById(presentationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Presentation not found: " + presentationId));

      presentation.setStatus(Presentation.Status.RENDERING);
      presentation.setRenderingStartedAt(LocalDateTime.now());
      presentation.setRendererUsed(rendererName);
      presentationRepository.save(presentation);

      // 2. Get appropriate renderer
      RenderingStrategy renderer = strategyFactory.getStrategy(rendererName);
      log.info(
          "Using rendering strategy: {} for presentation: {}",
          renderer.getClass().getSimpleName(),
          presentationId);

      // 3. Execute renderer-specific workflow
      File presentationFile = new File(presentation.getFilePath());
      if (!presentationFile.exists()) {
        throw new IllegalStateException(
            "Presentation file not found: " + presentation.getFilePath());
      }

      // Log file size for MS Graph rendering
      if ("MSGRAPH".equals(rendererName)) {
        log.info(
            "Starting MS Graph rendering for file size: {} MB",
            presentationFile.length() / (1024 * 1024));
      }

      // Prepare renderer (e.g., upload to cloud, convert to PDF)
      renderer.prepareForRendering(presentationFile);

      // 4. Render each slide
      List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
      log.info("Found {} slides to render for presentation: {}", slides.size(), presentationId);

      Path presentationDir = Paths.get(presentationsBasePath, presentationId.toString());

      for (Slide slide : slides) {
        try {
          // Update slide status
          slide.setRenderingStatus(Slide.RenderingStatus.RENDERING);
          slideRepository.save(slide);

          // Render the slide
          RenderingStrategy.RenderedSlide renderedSlide =
              renderer.renderSlide(slide.getSlideNumber(), defaultWidth, defaultHeight);

          // Save the rendered image
          Path imagePath = renderedSlide.saveToPath(presentationDir, slide.getSlideNumber());

          // Update slide with image information
          slide.setImagePath(imagePath.toString());
          slide.setImageGeneratedAt(LocalDateTime.now());
          slide.setImageWidth(renderedSlide.width());
          slide.setImageHeight(renderedSlide.height());
          slide.setRenderingStatus(Slide.RenderingStatus.COMPLETED);
          slideRepository.save(slide);

          log.debug(
              "Successfully rendered slide {} for presentation: {}",
              slide.getSlideNumber(),
              presentationId);

          // Automatically upload to R2 (will replace existing if re-rendering)
          try {
            log.info("Uploading slide image to R2 for slide: {}", slide.getId());
            r2AssetService.publishExistingAsset(
                presentationId,
                slide.getId(),
                AssetType.SLIDE_IMAGE,
                true // forceRepublish to replace existing image if re-rendering
                );
            log.info("Successfully uploaded slide {} image to R2", slide.getSlideNumber());
          } catch (Exception uploadEx) {
            log.error(
                "Failed to upload slide {} image to R2, continuing without R2 upload: {}",
                slide.getSlideNumber(),
                uploadEx.getMessage());
            // Don't fail the whole rendering operation if R2 upload fails
          }

        } catch (Exception e) {
          log.error(
              "Failed to render slide {} for presentation {}: {}",
              slide.getSlideNumber(),
              presentationId,
              e.getMessage());

          slide.setRenderingStatus(Slide.RenderingStatus.FAILED);
          slide.setRenderingErrorMessage(e.getMessage());
          slideRepository.save(slide);
        }
      }

      // Clean up renderer resources
      renderer.cleanup();

      // 5. Update status to COMPLETED
      presentation.setStatus(Presentation.Status.COMPLETED);
      presentation.setProcessingStatus(Presentation.ProcessingStatus.COMPLETED);
      presentation.setRenderingCompletedAt(LocalDateTime.now());
      presentationRepository.save(presentation);

      // Remove placeholder images now that real images are ready
      placeholderService.removePlaceholders(presentationId.toString());

      log.info("Successfully completed rendering for presentation: {}", presentationId);

    } catch (Exception e) {
      log.error(
          "Error rendering presentation {} with {}: {}",
          presentationId,
          rendererName,
          e.getMessage(),
          e);

      // Try fallback renderer if MS Graph fails
      if ("MSGRAPH".equals(rendererName)) {
        log.info("MS Graph rendering failed, falling back to ASPOSE renderer");
        try {
          // Reset status and try with ASPOSE
          Presentation presentation = presentationRepository.findById(presentationId).orElse(null);
          if (presentation != null) {
            presentation.setStatus(Presentation.Status.RENDERING);
            presentation.setRenderingErrorMessage(null);
            presentation.setRendererUsed("ASPOSE");
            presentationRepository.save(presentation);

            // Try rendering with ASPOSE
            renderPresentation(presentationId, "ASPOSE");
            return; // Exit to avoid marking as failed
          }
        } catch (Exception fallbackError) {
          log.error("Fallback renderer also failed: {}", fallbackError.getMessage());
        }
      }

      // Update status to FAILED
      try {
        Presentation presentation = presentationRepository.findById(presentationId).orElse(null);
        if (presentation != null) {
          presentation.setStatus(Presentation.Status.FAILED);
          presentation.setProcessingStatus(Presentation.ProcessingStatus.FAILED);
          presentation.setRenderingErrorMessage("Rendering failed: " + e.getMessage());
          presentation.setRenderingCompletedAt(LocalDateTime.now());
          presentationRepository.save(presentation);
        }
      } catch (Exception updateError) {
        log.error("Failed to update presentation status: {}", updateError.getMessage());
      }
    }
  }

  /**
   * Re-renders a presentation with a different renderer.
   *
   * @param presentationId the presentation ID
   * @param rendererName the new renderer to use
   * @param force if true, re-render even if already rendered
   */
  @Transactional
  public void reRenderPresentation(UUID presentationId, String rendererName, boolean force) {
    Presentation presentation =
        presentationRepository
            .findById(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));

    // Check if already rendered with the same renderer
    if (!force
        && rendererName.equals(presentation.getRendererUsed())
        && presentation.getStatus() == Presentation.Status.COMPLETED) {
      log.info("Presentation {} already rendered with {}, skipping", presentationId, rendererName);
      return;
    }

    // Reset rendering status
    presentation.setStatus(Presentation.Status.RENDERING);
    presentation.setRenderingStartedAt(null);
    presentation.setRenderingCompletedAt(null);
    presentation.setRendererUsed(null);
    presentation.setRenderingErrorMessage(null);
    presentationRepository.save(presentation);

    // Reset slide rendering status
    List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
    for (Slide slide : slides) {
      slide.setRenderingStatus(Slide.RenderingStatus.PENDING);
      slide.setRenderingErrorMessage(null);
    }
    slideRepository.saveAll(slides);

    // Trigger async rendering
    renderPresentation(presentationId, rendererName);
  }

  /**
   * Gets the rendering progress for a presentation.
   *
   * @param presentationId the presentation ID
   * @return rendering progress information
   */
  public RenderingProgress getRenderingProgress(UUID presentationId) {
    Presentation presentation =
        presentationRepository
            .findById(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));

    List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);
    long completedSlides =
        slides.stream()
            .filter(s -> s.getRenderingStatus() == Slide.RenderingStatus.COMPLETED)
            .count();

    return new RenderingProgress(
        presentation.getStatus().name(),
        (int) completedSlides,
        slides.size(),
        presentation.getRendererUsed(),
        presentation.getRenderingStartedAt(),
        presentation.getRenderingCompletedAt(),
        presentation.getRenderingErrorMessage());
  }

  /** DTO for rendering progress information. */
  public record RenderingProgress(
      String status,
      int completedSlides,
      int totalSlides,
      String renderer,
      LocalDateTime startedAt,
      LocalDateTime completedAt,
      String errorMessage) {}
}
