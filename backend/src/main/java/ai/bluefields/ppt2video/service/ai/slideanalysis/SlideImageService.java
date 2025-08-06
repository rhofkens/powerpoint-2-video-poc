package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.SlideRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for handling slide image operations. Manages image loading, encoding, and
 * dimension verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideImageService {

  private final SlideRepository slideRepository;

  @Value("${app.storage.base-path:./storage}")
  private String storagePath;

  /**
   * Get base64 encoded image for a slide. Ensures that only full slide renderings are used for AI
   * analysis.
   *
   * @param slide The slide to get image for
   * @return Base64 encoded image string
   * @throws IOException If image cannot be read
   */
  public String getSlideImageBase64(Slide slide) throws IOException {
    // STRICT: Only use full slide rendering for AI analysis
    // Do not use embedded images as they are partial/cropped and lose context
    String imagePath = slide.getImagePath();

    if (imagePath == null) {
      throw new IllegalStateException(
          "No slide rendering available for slide: "
              + slide.getId()
              + ". AI analysis requires a complete slide image, not embedded fragments.");
    }

    log.info(
        "Using slide rendering: path={}, dimensions={}x{}",
        slide.getImagePath(),
        slide.getImageWidth(),
        slide.getImageHeight());

    Path path = resolvePath(imagePath);
    validatePath(path);

    byte[] imageBytes = Files.readAllBytes(path);
    log.info("Read image file: {} bytes from {}", imageBytes.length, path);

    // Verify and update dimensions if needed
    verifyAndUpdateDimensions(slide, imageBytes);

    return Base64.getEncoder().encodeToString(imageBytes);
  }

  /**
   * Get MIME type for slide images.
   *
   * @return The MIME type (currently always "image/png")
   */
  public String getImageMimeType() {
    return "image/png";
  }

  /** Resolve image path relative to storage path if needed. */
  private Path resolvePath(String imagePath) {
    Path path = Paths.get(imagePath);

    // If the path is not absolute and doesn't already start with the storage path,
    // resolve it relative to the storage path
    if (!path.isAbsolute() && !imagePath.startsWith(storagePath)) {
      path = Paths.get(storagePath).resolve(imagePath);
    }

    return path;
  }

  /** Validate that the image file exists. */
  private void validatePath(Path path) throws IOException {
    if (!Files.exists(path)) {
      log.error("Image file not found at path: {}", path);
      throw new IOException("Image file not found: " + path);
    }
  }

  /** Verify actual image dimensions match stored metadata and update if needed. */
  private void verifyAndUpdateDimensions(Slide slide, byte[] imageBytes) {
    try {
      BufferedImage actualImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (actualImage != null) {
        int actualWidth = actualImage.getWidth();
        int actualHeight = actualImage.getHeight();
        log.info("Actual image dimensions from file: {}x{}", actualWidth, actualHeight);

        // Check for dimension mismatch and warn
        Integer storedWidth = slide.getImageWidth();
        Integer storedHeight = slide.getImageHeight();

        if (storedWidth != null && storedHeight != null) {
          if (!storedWidth.equals(actualWidth) || !storedHeight.equals(actualHeight)) {
            log.warn(
                "DIMENSION MISMATCH: Stored={}x{}, Actual={}x{} for slide {}",
                storedWidth,
                storedHeight,
                actualWidth,
                actualHeight,
                slide.getId());

            // Auto-correct the dimensions in database
            slide.setImageWidth(actualWidth);
            slide.setImageHeight(actualHeight);
            slideRepository.save(slide);
            log.info("Updated slide dimensions to {}x{}", actualWidth, actualHeight);
          }
        } else {
          // Set dimensions if they were null
          slide.setImageWidth(actualWidth);
          slide.setImageHeight(actualHeight);
          slideRepository.save(slide);
          log.info("Set slide dimensions to {}x{}", actualWidth, actualHeight);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to read actual image dimensions: {}", e.getMessage());
    }
  }
}
