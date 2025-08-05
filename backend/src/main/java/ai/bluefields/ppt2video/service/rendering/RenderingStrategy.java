package ai.bluefields.ppt2video.service.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Strategy interface for different rendering implementations. Each implementation handles the
 * specific workflow for its renderer type.
 */
public interface RenderingStrategy {

  /**
   * Prepares the renderer for rendering a presentation. This may include uploading files,
   * converting formats, or other initialization.
   *
   * @param presentationFile the PowerPoint file to render
   * @throws IOException if preparation fails
   */
  void prepareForRendering(File presentationFile) throws IOException;

  /**
   * Renders a single slide to an image.
   *
   * @param slideNumber the slide number (1-based)
   * @param width the target image width
   * @param height the target image height
   * @return the rendered slide
   * @throws IOException if rendering fails
   */
  RenderedSlide renderSlide(int slideNumber, int width, int height) throws IOException;

  /** Cleans up any resources used by the renderer. Called after all slides have been rendered. */
  void cleanup();

  /**
   * Gets the name of this rendering strategy.
   *
   * @return the strategy name
   */
  String getStrategyName();

  /** Represents a rendered slide with its image data. */
  record RenderedSlide(BufferedImage image, int width, int height) {
    /**
     * Saves the rendered slide to a file.
     *
     * @param presentationDir the presentation directory
     * @param slideNumber the slide number
     * @return the path to the saved image
     * @throws IOException if save fails
     */
    public Path saveToPath(Path presentationDir, int slideNumber) throws IOException {
      // Create slides directory
      Path slideImagesDir = presentationDir.resolve("slides");
      Files.createDirectories(slideImagesDir);

      // Generate filename
      String filename = String.format("slide-%d.png", slideNumber);
      Path imagePath = slideImagesDir.resolve(filename);

      // Write image
      if (!ImageIO.write(image, "png", imagePath.toFile())) {
        throw new IOException("Failed to write PNG image");
      }

      return imagePath;
    }
  }
}
