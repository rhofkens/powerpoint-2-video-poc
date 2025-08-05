package ai.bluefields.ppt2video.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for generating placeholder images for slides that haven't been rendered yet. This
 * provides immediate visual feedback to users while actual rendering happens asynchronously.
 */
@Service
@Slf4j
public class PlaceholderImageService {

  @Value("${app.storage.presentations-path}")
  private String presentationsBasePath;

  @Value("${app.placeholder.width:1920}")
  private int placeholderWidth;

  @Value("${app.placeholder.height:1080}")
  private int placeholderHeight;

  @Value("${app.placeholder.background-color:#f0f0f0}")
  private String backgroundColor;

  @Value("${app.placeholder.text-color:#666666}")
  private String textColor;

  /**
   * Gets or creates a placeholder image for a slide.
   *
   * @param presentationId the presentation ID
   * @param slideNumber the slide number
   * @return path to the placeholder image
   */
  public String getOrCreatePlaceholder(String presentationId, int slideNumber) {
    try {
      Path placeholderPath = getPlaceholderPath(presentationId, slideNumber);

      // Create if doesn't exist
      if (!Files.exists(placeholderPath)) {
        createPlaceholderImage(placeholderPath, slideNumber);
      }

      return placeholderPath.toAbsolutePath().toString();

    } catch (IOException e) {
      log.error(
          "Failed to create placeholder for slide {} in presentation {}",
          slideNumber,
          presentationId,
          e);
      return null;
    }
  }

  /** Creates a placeholder image with slide number. */
  private void createPlaceholderImage(Path outputPath, int slideNumber) throws IOException {
    // Create directories if needed
    Files.createDirectories(outputPath.getParent());

    // Create image
    BufferedImage image =
        new BufferedImage(placeholderWidth, placeholderHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = image.createGraphics();

    try {
      // Enable antialiasing
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      // Fill background
      g2d.setColor(Color.decode(backgroundColor));
      g2d.fillRect(0, 0, placeholderWidth, placeholderHeight);

      // Draw border
      g2d.setColor(Color.decode(textColor));
      g2d.setStroke(new BasicStroke(2));
      g2d.drawRect(10, 10, placeholderWidth - 20, placeholderHeight - 20);

      // Draw slide number
      String slideText = "Slide " + slideNumber;
      Font font = new Font("Arial", Font.BOLD, 72);
      g2d.setFont(font);

      FontMetrics metrics = g2d.getFontMetrics(font);
      int x = (placeholderWidth - metrics.stringWidth(slideText)) / 2;
      int y = placeholderHeight / 2 - metrics.getHeight() / 2;

      g2d.drawString(slideText, x, y);

      // Draw "Rendering..." text
      String renderingText = "Rendering in progress...";
      Font smallFont = new Font("Arial", Font.PLAIN, 36);
      g2d.setFont(smallFont);

      FontMetrics smallMetrics = g2d.getFontMetrics(smallFont);
      int smallX = (placeholderWidth - smallMetrics.stringWidth(renderingText)) / 2;
      int smallY = y + metrics.getHeight() + 20;

      g2d.drawString(renderingText, smallX, smallY);

    } finally {
      g2d.dispose();
    }

    // Save image
    ImageIO.write(image, "png", outputPath.toFile());
    log.debug("Created placeholder image: {}", outputPath);
  }

  /** Gets the path for a placeholder image. */
  private Path getPlaceholderPath(String presentationId, int slideNumber) {
    return Paths.get(presentationsBasePath)
        .resolve(presentationId)
        .resolve("placeholders")
        .resolve(String.format("placeholder-slide-%d.png", slideNumber));
  }

  /** Removes placeholder images when actual images are ready. */
  public void removePlaceholders(String presentationId) {
    try {
      Path placeholderDir =
          Paths.get(presentationsBasePath).resolve(presentationId).resolve("placeholders");

      if (Files.exists(placeholderDir)) {
        Files.walk(placeholderDir)
            .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    log.warn("Failed to delete placeholder: {}", path, e);
                  }
                });
        log.debug("Removed placeholders for presentation: {}", presentationId);
      }
    } catch (IOException e) {
      log.error("Failed to remove placeholders for presentation: {}", presentationId, e);
    }
  }
}
