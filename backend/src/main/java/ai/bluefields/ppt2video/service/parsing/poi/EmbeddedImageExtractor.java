package ai.bluefields.ppt2video.service.parsing.poi;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideImage;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Component;

/**
 * Extracts embedded images from PowerPoint slides. This extracts images that are part of the slide
 * content (not renderings of the slides).
 */
@Component
@Slf4j
public class EmbeddedImageExtractor {

  /**
   * Extracts all images embedded in a slide.
   *
   * @param pptSlide the slide to extract images from
   * @param slide the slide entity
   * @param presentationDir the presentation directory
   * @return list of extracted slide images
   */
  public List<SlideImage> extractImages(XSLFSlide pptSlide, Slide slide, Path presentationDir) {
    List<SlideImage> slideImages = new ArrayList<>();

    if (pptSlide == null || slide == null || presentationDir == null) {
      return slideImages;
    }

    try {
      Path imagesDir = presentationDir.resolve("images");
      Files.createDirectories(imagesDir);

      int imageOrder = 0;
      List<? extends Shape<?, ?>> shapes = pptSlide.getShapes();
      if (shapes != null) {
        for (Shape<?, ?> shape : shapes) {
          if (shape == null) {
            continue;
          }

          imageOrder = processShape(shape, slide, imagesDir, slideImages, imageOrder);
        }
      }

    } catch (IOException e) {
      log.warn("Failed to create images directory: {}", e.getMessage());
    } catch (Exception e) {
      log.warn("Unexpected error extracting images from slide: {}", e.getMessage());
    }

    return slideImages;
  }

  private int processShape(
      Shape<?, ?> shape,
      Slide slide,
      Path imagesDir,
      List<SlideImage> slideImages,
      int imageOrder) {
    try {
      if (shape instanceof XSLFPictureShape) {
        XSLFPictureShape pictureShape = (XSLFPictureShape) shape;
        SlideImage slideImage = extractPicture(pictureShape, slide, imagesDir, imageOrder);
        if (slideImage != null) {
          slideImages.add(slideImage);
          imageOrder++;
        }
      } else if (shape instanceof XSLFGroupShape) {
        // Handle grouped shapes recursively
        imageOrder =
            extractImagesFromGroup(
                (XSLFGroupShape) shape, slide, imagesDir, slideImages, imageOrder);
      }
    } catch (Exception e) {
      log.debug("Error processing shape for image extraction: {}", e.getMessage());
    }

    return imageOrder;
  }

  private int extractImagesFromGroup(
      XSLFGroupShape group,
      Slide slide,
      Path imagesDir,
      List<SlideImage> slideImages,
      int startingOrder) {
    if (group == null) {
      return startingOrder;
    }

    int imageOrder = startingOrder;

    try {
      List<? extends Shape<?, ?>> shapes = group.getShapes();
      if (shapes != null) {
        for (Shape<?, ?> shape : shapes) {
          if (shape == null) {
            continue;
          }

          imageOrder = processShape(shape, slide, imagesDir, slideImages, imageOrder);
        }
      }
    } catch (Exception e) {
      log.warn("Error extracting images from group shape: {}", e.getMessage());
    }

    return imageOrder;
  }

  private SlideImage extractPicture(
      XSLFPictureShape pictureShape, Slide slide, Path imagesDir, int order) {
    if (pictureShape == null || slide == null || imagesDir == null) {
      return null;
    }

    try {
      PictureData pictureData = pictureShape.getPictureData();
      if (pictureData == null) {
        log.warn("Picture shape has no picture data");
        return null;
      }

      String contentType = pictureData.getContentType();
      if (contentType == null) {
        log.warn("Picture data has no content type");
        return null;
      }

      String extension = getImageExtension(contentType);
      if (extension == null) {
        log.warn("Unsupported image type: {}", contentType);
        return null;
      }

      byte[] imageData = pictureData.getData();
      if (imageData == null || imageData.length == 0) {
        log.warn("Picture data is empty");
        return null;
      }

      // Generate unique filename
      String filename = String.format("image_%d_%s.%s", order, UUID.randomUUID(), extension);
      Path imagePath = imagesDir.resolve(filename);

      // Save image data
      Files.write(imagePath, imageData);

      // Get image dimensions
      int width = 0;
      int height = 0;
      try {
        Rectangle2D anchor = pictureShape.getAnchor();
        if (anchor != null) {
          width = Math.max(1, (int) anchor.getWidth());
          height = Math.max(1, (int) anchor.getHeight());
        }
      } catch (Exception e) {
        log.debug("Could not get image dimensions: {}", e.getMessage());
      }

      // Create SlideImage entity
      SlideImage slideImage = new SlideImage();
      slideImage.setSlide(slide);
      slideImage.setImagePath(imagePath.toAbsolutePath().toString());
      slideImage.setImageType(extension.toUpperCase());
      slideImage.setWidth(width);
      slideImage.setHeight(height);
      slideImage.setOrderInSlide(order);

      log.debug("Extracted image: {} ({}x{})", filename, width, height);
      return slideImage;

    } catch (IOException e) {
      log.warn("Failed to extract picture: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn("Unexpected error extracting picture: {}", e.getMessage());
      return null;
    }
  }

  private String getImageExtension(String contentType) {
    if (contentType == null) {
      return null;
    }

    // Normalize content type to lowercase
    String normalizedType = contentType.toLowerCase().trim();

    return switch (normalizedType) {
      case "image/png" -> "png";
      case "image/jpeg", "image/jpg" -> "jpg";
      case "image/gif" -> "gif";
      case "image/bmp", "image/x-bmp", "image/x-ms-bmp" -> "bmp";
      case "image/tiff", "image/tif" -> "tiff";
      case "image/svg+xml" -> "svg";
      case "image/webp" -> "webp";
      default -> {
        log.debug("Unknown image content type: {}", contentType);
        yield null;
      }
    };
  }
}
