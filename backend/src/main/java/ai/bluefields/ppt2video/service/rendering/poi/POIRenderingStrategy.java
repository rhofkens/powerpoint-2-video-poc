package ai.bluefields.ppt2video.service.rendering.poi;

import ai.bluefields.ppt2video.service.rendering.RenderingStrategy;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Component;

/** Apache POI-based rendering strategy for local slide rendering. */
@Component("POI")
@Slf4j
public class POIRenderingStrategy implements RenderingStrategy {

  private XMLSlideShow slideShow;

  @Override
  public void prepareForRendering(File presentationFile) throws IOException {
    log.info("Preparing POI rendering for file: {}", presentationFile.getName());

    try (FileInputStream fis = new FileInputStream(presentationFile)) {
      // Load presentation once
      slideShow = new XMLSlideShow(fis);

      log.info("Loaded presentation with {} slides", slideShow.getSlides().size());

    } catch (Exception e) {
      log.error("Failed to prepare POI rendering: {}", e.getMessage());
      throw new IOException("POI preparation failed", e);
    }
  }

  @Override
  public RenderedSlide renderSlide(int slideNumber, int width, int height) throws IOException {
    if (slideShow == null) {
      throw new IllegalStateException("Renderer not prepared - call prepareForRendering first");
    }

    try {
      // Get the slide (0-based index)
      XSLFSlide slide = slideShow.getSlides().get(slideNumber - 1);

      // Create image with proper dimensions
      BufferedImage slideImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = slideImage.createGraphics();

      try {
        // Configure graphics for quality rendering
        graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(
            RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fill background with white
        graphics.setPaint(Color.white);
        graphics.fill(new Rectangle2D.Float(0, 0, width, height));

        // Get original slide dimensions
        Dimension pgsize = slideShow.getPageSize();

        // Calculate scale to fit the target dimensions
        double scaleX = width / (double) pgsize.width;
        double scaleY = height / (double) pgsize.height;
        double scale = Math.min(scaleX, scaleY);

        // Center the slide if aspect ratios don't match
        double scaledWidth = pgsize.width * scale;
        double scaledHeight = pgsize.height * scale;
        double offsetX = (width - scaledWidth) / 2;
        double offsetY = (height - scaledHeight) / 2;

        // Apply transformations
        graphics.translate(offsetX, offsetY);
        graphics.scale(scale, scale);

        // Render the slide
        slide.draw(graphics);

      } finally {
        graphics.dispose();
      }

      return new RenderedSlide(slideImage, width, height);

    } catch (Exception e) {
      log.error("Failed to render slide {}: {}", slideNumber, e.getMessage());
      throw new IOException("Failed to render slide " + slideNumber, e);
    }
  }

  @Override
  public void cleanup() {
    // Close the slideshow
    if (slideShow != null) {
      try {
        slideShow.close();
        log.debug("Closed POI slideshow");
      } catch (Exception e) {
        log.warn("Failed to close slideshow: {}", e.getMessage());
      }
      slideShow = null;
    }
  }

  @Override
  public String getStrategyName() {
    return "Apache POI Local Renderer";
  }
}
