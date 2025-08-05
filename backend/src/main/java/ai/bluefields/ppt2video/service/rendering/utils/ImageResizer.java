package ai.bluefields.ppt2video.service.rendering.utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utility class for image resizing and manipulation operations. Provides high-quality image scaling
 * with aspect ratio preservation.
 */
@Component
@Slf4j
public class ImageResizer {

  /**
   * Resizes an image to exact dimensions with high quality. Centers the image if aspect ratios
   * don't match.
   *
   * @param original the original image
   * @param targetWidth the target width
   * @param targetHeight the target height
   * @return the resized image
   */
  public BufferedImage resize(BufferedImage original, int targetWidth, int targetHeight) {
    return resize(original, targetWidth, targetHeight, Color.WHITE);
  }

  /**
   * Resizes an image to exact dimensions with high quality. Centers the image if aspect ratios
   * don't match.
   *
   * @param original the original image
   * @param targetWidth the target width
   * @param targetHeight the target height
   * @param backgroundColor the background color for letterboxing
   * @return the resized image
   */
  public BufferedImage resize(
      BufferedImage original, int targetWidth, int targetHeight, Color backgroundColor) {

    if (original == null) {
      throw new IllegalArgumentException("Original image cannot be null");
    }

    // If already the right size, return as-is
    if (original.getWidth() == targetWidth && original.getHeight() == targetHeight) {
      return original;
    }

    BufferedImage resized =
        new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = resized.createGraphics();

    try {
      // Apply high-quality rendering hints
      applyQualityRenderingHints(g2d);

      // Fill background
      g2d.setColor(backgroundColor);
      g2d.fillRect(0, 0, targetWidth, targetHeight);

      // Calculate scaling to maintain aspect ratio
      ScalingInfo scalingInfo =
          calculateScaling(original.getWidth(), original.getHeight(), targetWidth, targetHeight);

      // Draw scaled image centered
      g2d.drawImage(
          original, scalingInfo.x, scalingInfo.y, scalingInfo.width, scalingInfo.height, null);

      log.debug(
          "Resized image from {}x{} to {}x{} (scaled content: {}x{})",
          original.getWidth(),
          original.getHeight(),
          targetWidth,
          targetHeight,
          scalingInfo.width,
          scalingInfo.height);

    } finally {
      g2d.dispose();
    }

    return resized;
  }

  /**
   * Scales an image by a factor while maintaining aspect ratio.
   *
   * @param original the original image
   * @param scaleFactor the scale factor (e.g., 2.0 for double size)
   * @return the scaled image
   */
  public BufferedImage scale(BufferedImage original, double scaleFactor) {
    if (original == null) {
      throw new IllegalArgumentException("Original image cannot be null");
    }

    int newWidth = (int) (original.getWidth() * scaleFactor);
    int newHeight = (int) (original.getHeight() * scaleFactor);

    return resizeExact(original, newWidth, newHeight);
  }

  /**
   * Resizes an image to exact dimensions without preserving aspect ratio.
   *
   * @param original the original image
   * @param width the target width
   * @param height the target height
   * @return the resized image
   */
  public BufferedImage resizeExact(BufferedImage original, int width, int height) {
    if (original == null) {
      throw new IllegalArgumentException("Original image cannot be null");
    }

    BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = resized.createGraphics();

    try {
      applyQualityRenderingHints(g2d);
      g2d.drawImage(original, 0, 0, width, height, null);
    } finally {
      g2d.dispose();
    }

    return resized;
  }

  /**
   * Creates a thumbnail with maximum dimensions while preserving aspect ratio.
   *
   * @param original the original image
   * @param maxWidth maximum width
   * @param maxHeight maximum height
   * @return the thumbnail image
   */
  public BufferedImage createThumbnail(BufferedImage original, int maxWidth, int maxHeight) {
    if (original == null) {
      throw new IllegalArgumentException("Original image cannot be null");
    }

    ScalingInfo scalingInfo =
        calculateScaling(original.getWidth(), original.getHeight(), maxWidth, maxHeight);

    return resizeExact(original, scalingInfo.width, scalingInfo.height);
  }

  /**
   * Applies high-quality rendering hints to a Graphics2D object.
   *
   * @param g2d the graphics context
   */
  private void applyQualityRenderingHints(Graphics2D g2d) {
    g2d.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(
        RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    g2d.setRenderingHint(
        RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
  }

  /**
   * Calculates scaling information to fit content within bounds while preserving aspect ratio.
   *
   * @param originalWidth original image width
   * @param originalHeight original image height
   * @param targetWidth target container width
   * @param targetHeight target container height
   * @return scaling information
   */
  private ScalingInfo calculateScaling(
      int originalWidth, int originalHeight, int targetWidth, int targetHeight) {

    double scaleX = (double) targetWidth / originalWidth;
    double scaleY = (double) targetHeight / originalHeight;
    double scale = Math.min(scaleX, scaleY);

    int scaledWidth = (int) (originalWidth * scale);
    int scaledHeight = (int) (originalHeight * scale);
    int x = (targetWidth - scaledWidth) / 2;
    int y = (targetHeight - scaledHeight) / 2;

    return new ScalingInfo(x, y, scaledWidth, scaledHeight);
  }

  /** Information about how to scale and position an image. */
  private record ScalingInfo(int x, int y, int width, int height) {}
}
