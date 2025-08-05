package ai.bluefields.ppt2video.service.parsing.aspose;

import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideImage;
import ai.bluefields.ppt2video.exception.PowerPointParsingException;
import ai.bluefields.ppt2video.service.FileStorageService;
import ai.bluefields.ppt2video.service.PlaceholderImageService;
import ai.bluefields.ppt2video.service.parsing.PowerPointParser;
import com.aspose.slides.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Alternative PowerPoint parser using Aspose.Slides. This parser can handle files that POI cannot
 * process due to security restrictions.
 */
@Service
@ConditionalOnProperty(
    name = "app.slide-rendering.aspose.enabled",
    havingValue = "true",
    matchIfMissing = false)
@Slf4j
@RequiredArgsConstructor
public class AsposePowerPointParser implements PowerPointParser {

  private final FileStorageService fileStorageService;
  private final PlaceholderImageService placeholderService;

  @Override
  public List<Slide> parsePresentation(String filePath, Presentation presentation) {
    log.info("Starting Aspose PowerPoint parsing for file: {}", filePath);

    // No need to check renderer availability - SlideImageGenerator will handle that

    validateInputs(filePath, presentation);

    // Save current locale and set to US for Aspose compatibility
    Locale originalLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.US);
      log.debug("Set locale to US for Aspose parsing");

      // Load presentation using Aspose
      com.aspose.slides.Presentation asposePpt = new com.aspose.slides.Presentation(filePath);

      try {
        List<Slide> slides = new ArrayList<>();
        ISlideCollection pptSlides = asposePpt.getSlides();

        if (pptSlides == null || pptSlides.size() == 0) {
          log.warn("PowerPoint presentation has no slides: {}", filePath);
          return slides;
        }

        // Create presentation directory structure
        Path presentationDir = fileStorageService.createPresentationDirectory(presentation.getId());

        // Process each slide
        for (int i = 0; i < pptSlides.size(); i++) {
          ISlide asposeSlide = pptSlides.get_Item(i);

          try {
            Slide slide = parseSlide(asposeSlide, presentation, i + 1, presentationDir, filePath);
            if (slide != null) {
              slides.add(slide);
            }
          } catch (Exception e) {
            log.error(
                "Failed to parse slide {} in presentation {}: {}", i + 1, filePath, e.getMessage());
            // Continue with next slide instead of failing entire presentation
          }
        }

        log.info("Successfully parsed {} slides from presentation using Aspose", slides.size());
        return slides;

      } finally {
        asposePpt.dispose();
      }

    } catch (Exception e) {
      log.error("Failed to parse PowerPoint presentation with Aspose: {}", filePath, e);
      throw new PowerPointParsingException(
          "Failed to parse PowerPoint presentation with Aspose", e);
    } finally {
      // Restore original locale
      Locale.setDefault(originalLocale);
      log.debug("Restored original locale: {}", originalLocale);
    }
  }

  /** Parses a single slide using Aspose. */
  private Slide parseSlide(
      ISlide asposeSlide,
      Presentation presentation,
      int slideNumber,
      Path presentationDir,
      String filePath)
      throws IOException {

    Slide slide = new Slide();
    slide.setPresentation(presentation);
    slide.setSlideNumber(slideNumber);

    // Extract slide content
    String title = extractSlideTitle(asposeSlide);
    slide.setTitle(title != null ? title : "Slide " + slideNumber);

    String content = extractSlideText(asposeSlide);
    slide.setContent(content);

    String speakerNotes = extractSpeakerNotes(asposeSlide);
    slide.setSpeakerNotes(speakerNotes);

    // Slide image will be generated in the rendering phase
    // Create placeholder image for immediate UI feedback
    String placeholderPath =
        placeholderService.getOrCreatePlaceholder(presentation.getId().toString(), slideNumber);
    slide.setImagePath(placeholderPath);

    // Extract embedded images from slide (not renderings of the slide)
    List<SlideImage> embeddedImages = extractImagesFromSlide(asposeSlide, slide, presentationDir);
    slide.setSlideImages(embeddedImages);
    log.info("Extracted {} embedded images from slide {}", embeddedImages.size(), slideNumber);

    return slide;
  }

  /** Extracts the title from a slide. */
  private String extractSlideTitle(ISlide slide) {
    try {
      if (slide.getShapes() != null) {
        for (int i = 0; i < slide.getShapes().size(); i++) {
          IShape shape = slide.getShapes().get_Item(i);
          if (shape instanceof IAutoShape) {
            IAutoShape autoShape = (IAutoShape) shape;
            if (autoShape.getTextFrame() != null) {
              String text = autoShape.getTextFrame().getText();
              if (text != null && !text.trim().isEmpty()) {
                // Check if this is likely a title
                if (shape.getPlaceholder() != null) {
                  int placeholderType = shape.getPlaceholder().getType();
                  if (placeholderType == PlaceholderType.Title
                      || placeholderType == PlaceholderType.CenteredTitle) {
                    return text.trim();
                  }
                }
                // If no title placeholder, use the first text as title
                if (i == 0) {
                  return text.trim();
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("Error extracting slide title: {}", e.getMessage());
    }
    return null;
  }

  /** Extracts all text content from a slide. */
  private String extractSlideText(ISlide slide) {
    StringBuilder content = new StringBuilder();

    try {
      if (slide.getShapes() != null) {
        for (int i = 0; i < slide.getShapes().size(); i++) {
          IShape shape = slide.getShapes().get_Item(i);
          if (shape instanceof IAutoShape) {
            IAutoShape autoShape = (IAutoShape) shape;
            if (autoShape.getTextFrame() != null) {
              String text = autoShape.getTextFrame().getText();
              if (text != null && !text.trim().isEmpty()) {
                if (content.length() > 0) {
                  content.append("\n");
                }
                content.append(text.trim());
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("Error extracting slide text: {}", e.getMessage());
    }

    return content.toString();
  }

  /** Extracts speaker notes from a slide. */
  private String extractSpeakerNotes(ISlide slide) {
    try {
      INotesSlide notesSlide = slide.getNotesSlideManager().getNotesSlide();
      if (notesSlide != null && notesSlide.getNotesTextFrame() != null) {
        return notesSlide.getNotesTextFrame().getText();
      }
    } catch (Exception e) {
      log.debug("Error extracting speaker notes: {}", e.getMessage());
    }
    return null;
  }

  /** Validates input parameters. */
  private void validateInputs(String filePath, Presentation presentation) {
    if (filePath == null || filePath.trim().isEmpty()) {
      throw new PowerPointParsingException("File path cannot be null or empty");
    }

    if (presentation == null || presentation.getId() == null) {
      throw new PowerPointParsingException("Presentation entity must have a valid ID");
    }
  }

  /**
   * Extracts all images from a slide using Aspose.
   *
   * @param slide the Aspose slide
   * @param slideEntity the slide entity
   * @param presentationDir the presentation directory
   * @return list of extracted slide images
   */
  private List<SlideImage> extractImagesFromSlide(
      ISlide slide, Slide slideEntity, Path presentationDir) {
    List<SlideImage> slideImages = new ArrayList<>();

    if (slide == null || slideEntity == null || presentationDir == null) {
      return slideImages;
    }

    try {
      // Create images directory
      Path imagesDir = presentationDir.resolve("images");
      Files.createDirectories(imagesDir);

      int imageOrder = 0;

      // Iterate through all shapes on the slide
      for (int i = 0; i < slide.getShapes().size(); i++) {
        IShape shape = slide.getShapes().get_Item(i);

        // Extract images from the shape (recursively handles group shapes)
        List<SlideImage> extractedImages =
            extractImagesFromShape(shape, slideEntity, imagesDir, imageOrder);

        slideImages.addAll(extractedImages);
        imageOrder += extractedImages.size();
      }

      log.debug(
          "Extracted {} images from slide {}", slideImages.size(), slideEntity.getSlideNumber());

    } catch (Exception e) {
      log.warn("Failed to extract images from slide: {}", e.getMessage());
    }

    return slideImages;
  }

  /**
   * Extracts images from a shape (handles both picture shapes and group shapes).
   *
   * @param shape the shape to extract images from
   * @param slide the slide entity
   * @param imagesDir the directory to save images
   * @param startingOrder the starting order index
   * @return list of extracted images
   */
  private List<SlideImage> extractImagesFromShape(
      IShape shape, Slide slide, Path imagesDir, int startingOrder) {
    List<SlideImage> images = new ArrayList<>();

    if (shape == null) {
      return images;
    }

    try {
      // Check if it's a picture frame
      if (shape instanceof IPictureFrame) {
        IPictureFrame pictureFrame = (IPictureFrame) shape;
        SlideImage slideImage =
            extractPictureFromFrame(pictureFrame, slide, imagesDir, startingOrder);
        if (slideImage != null) {
          images.add(slideImage);
        }
      }
      // Check if it's a group shape
      else if (shape instanceof IGroupShape) {
        IGroupShape groupShape = (IGroupShape) shape;
        int imageOrder = startingOrder;

        // Recursively extract from shapes within the group
        for (int i = 0; i < groupShape.getShapes().size(); i++) {
          IShape innerShape = groupShape.getShapes().get_Item(i);
          List<SlideImage> innerImages =
              extractImagesFromShape(innerShape, slide, imagesDir, imageOrder);
          images.addAll(innerImages);
          imageOrder += innerImages.size();
        }
      }
      // Check if it's an OLE object (embedded objects like images)
      else if (shape instanceof IOleObjectFrame) {
        IOleObjectFrame oleFrame = (IOleObjectFrame) shape;
        SlideImage slideImage =
            extractPictureFromOleObject(oleFrame, slide, imagesDir, startingOrder);
        if (slideImage != null) {
          images.add(slideImage);
        }
      }
    } catch (Exception e) {
      log.debug("Error extracting image from shape: {}", e.getMessage());
    }

    return images;
  }

  /**
   * Extracts a picture from a picture frame.
   *
   * @param pictureFrame the picture frame
   * @param slide the slide entity
   * @param imagesDir the directory to save the image
   * @param order the order index
   * @return the extracted slide image, or null if extraction fails
   */
  private SlideImage extractPictureFromFrame(
      IPictureFrame pictureFrame, Slide slide, Path imagesDir, int order) {
    try {
      // Get the picture fill format
      IPictureFillFormat pictureFill = pictureFrame.getPictureFormat();
      if (pictureFill == null || pictureFill.getPicture() == null) {
        return null;
      }

      // Get the image
      IPPImage image = pictureFill.getPicture().getImage();
      if (image == null) {
        return null;
      }

      // Get image data
      byte[] imageData = image.getBinaryData();
      if (imageData == null || imageData.length == 0) {
        return null;
      }

      // Determine image format
      String contentType = image.getContentType();
      String extension = getImageExtension(contentType);
      if (extension == null) {
        log.warn("Unsupported image type: {}", contentType);
        return null;
      }

      // Generate unique filename
      String filename = String.format("image_%d_%s.%s", order, UUID.randomUUID(), extension);
      Path imagePath = imagesDir.resolve(filename);

      // Save image data
      Files.write(imagePath, imageData);

      // Get dimensions
      int width = (int) pictureFrame.getWidth();
      int height = (int) pictureFrame.getHeight();

      // Create SlideImage entity
      SlideImage slideImage = new SlideImage();
      slideImage.setSlide(slide);
      slideImage.setImagePath(imagePath.toAbsolutePath().toString());
      slideImage.setImageType(extension.toUpperCase());
      slideImage.setWidth(width);
      slideImage.setHeight(height);
      slideImage.setOrderInSlide(order);

      log.debug("Extracted image from picture frame: {} ({}x{})", filename, width, height);
      return slideImage;

    } catch (Exception e) {
      log.warn("Failed to extract picture from frame: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Extracts a picture from an OLE object frame.
   *
   * @param oleFrame the OLE object frame
   * @param slide the slide entity
   * @param imagesDir the directory to save the image
   * @param order the order index
   * @return the extracted slide image, or null if extraction fails
   */
  private SlideImage extractPictureFromOleObject(
      IOleObjectFrame oleFrame, Slide slide, Path imagesDir, int order) {
    try {
      // Get substitute picture data
      IPPImage image = oleFrame.getSubstitutePictureFormat().getPicture().getImage();
      if (image == null) {
        return null;
      }

      // Get image data
      byte[] imageData = image.getBinaryData();
      if (imageData == null || imageData.length == 0) {
        return null;
      }

      // Determine image format
      String contentType = image.getContentType();
      String extension = getImageExtension(contentType);
      if (extension == null) {
        log.warn("Unsupported image type: {}", contentType);
        return null;
      }

      // Generate unique filename
      String filename = String.format("ole_image_%d_%s.%s", order, UUID.randomUUID(), extension);
      Path imagePath = imagesDir.resolve(filename);

      // Save image data
      Files.write(imagePath, imageData);

      // Get dimensions
      int width = (int) oleFrame.getWidth();
      int height = (int) oleFrame.getHeight();

      // Create SlideImage entity
      SlideImage slideImage = new SlideImage();
      slideImage.setSlide(slide);
      slideImage.setImagePath(imagePath.toAbsolutePath().toString());
      slideImage.setImageType(extension.toUpperCase());
      slideImage.setWidth(width);
      slideImage.setHeight(height);
      slideImage.setOrderInSlide(order);

      log.debug("Extracted image from OLE object: {} ({}x{})", filename, width, height);
      return slideImage;

    } catch (Exception e) {
      log.debug("Failed to extract picture from OLE object: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Gets the file extension for an image content type.
   *
   * @param contentType the content type
   * @return the file extension, or null if unsupported
   */
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
      case "image/x-wmf" -> "wmf";
      case "image/x-emf" -> "emf";
      default -> {
        log.debug("Unknown image content type: {}", contentType);
        yield null;
      }
    };
  }

  @Override
  public boolean isAvailable() {
    try {
      // Try to create a simple presentation to check if Aspose is properly licensed/available
      com.aspose.slides.Presentation test = new com.aspose.slides.Presentation();
      test.dispose();
      return true;
    } catch (Exception e) {
      log.debug("Aspose is not available: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public String getParserName() {
    return "Aspose PowerPoint Parser";
  }
}
