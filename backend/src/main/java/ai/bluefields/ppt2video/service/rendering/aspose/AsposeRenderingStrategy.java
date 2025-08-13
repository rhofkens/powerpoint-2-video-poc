package ai.bluefields.ppt2video.service.rendering.aspose;

import ai.bluefields.ppt2video.service.rendering.RenderingStrategy;
import com.aspose.slides.ISlide;
import com.aspose.slides.Presentation;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Aspose-based rendering strategy for local slide rendering. */
@Component("ASPOSE")
@ConditionalOnProperty(name = "app.aspose.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class AsposeRenderingStrategy implements RenderingStrategy {

  private Presentation presentation;
  private String presentationPath;

  @Override
  public void prepareForRendering(File presentationFile) throws IOException {
    log.info("Preparing Aspose rendering for file: {}", presentationFile.getName());

    try {
      // Load presentation once
      presentationPath = presentationFile.getAbsolutePath();
      presentation = new Presentation(presentationPath);

      log.info("Loaded presentation with {} slides", presentation.getSlides().size());

    } catch (Exception e) {
      log.error("Failed to prepare Aspose rendering: {}", e.getMessage());
      throw new IOException("Aspose preparation failed", e);
    }
  }

  @Override
  public RenderedSlide renderSlide(int slideNumber, int width, int height) throws IOException {
    if (presentation == null) {
      throw new IllegalStateException("Renderer not prepared - call prepareForRendering first");
    }

    try {
      // Get the slide (0-based index)
      ISlide slide = presentation.getSlides().get_Item(slideNumber - 1);

      // Render at the requested size
      float scaleX = width / (float) presentation.getSlideSize().getSize().getWidth();
      float scaleY = height / (float) presentation.getSlideSize().getSize().getHeight();

      @SuppressWarnings("deprecation")
      BufferedImage slideImage = slide.getThumbnail(scaleX, scaleY);

      return new RenderedSlide(slideImage, width, height);

    } catch (Exception e) {
      log.error("Failed to render slide {}: {}", slideNumber, e.getMessage());
      throw new IOException("Failed to render slide " + slideNumber, e);
    }
  }

  @Override
  public void cleanup() {
    // Dispose of the presentation
    if (presentation != null) {
      try {
        presentation.dispose();
        log.debug("Disposed Aspose presentation");
      } catch (Exception e) {
        log.warn("Failed to dispose presentation: {}", e.getMessage());
      }
      presentation = null;
    }
    presentationPath = null;
  }

  @Override
  public String getStrategyName() {
    return "Aspose Slides Local Renderer";
  }
}
