package ai.bluefields.ppt2video.service.parsing.poi;

import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideImage;
import ai.bluefields.ppt2video.exception.PowerPointParsingException;
import ai.bluefields.ppt2video.service.FileStorageService;
import ai.bluefields.ppt2video.service.PlaceholderImageService;
import ai.bluefields.ppt2video.service.parsing.PowerPointParser;
import ai.bluefields.ppt2video.service.parsing.poi.SlideContentExtractor.SlideContent;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Apache POI-based PowerPoint parser implementation. Uses Apache POI library to extract content,
 * metadata, and images from PowerPoint presentations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class POIPowerPointParser implements PowerPointParser {

  @Value("${app.storage.presentations-path}")
  private String presentationsPath;

  private final FileStorageService fileStorageService;
  private final SlideContentExtractor contentExtractor;
  private final EmbeddedImageExtractor imageExtractor;
  private final PlaceholderImageService placeholderService;

  /**
   * Parses a PowerPoint presentation and extracts all slides with their content.
   *
   * @param filePath the path to the PPTX file
   * @param presentation the presentation entity to associate slides with
   * @return list of parsed slides
   * @throws PowerPointParsingException if parsing fails
   */
  public List<Slide> parsePresentation(String filePath, Presentation presentation) {
    log.info("Starting PowerPoint parsing for file: {}", filePath);

    validateInputs(filePath, presentation);

    // Increase POI security limits for large files
    org.apache.poi.openxml4j.util.ZipSecureFile.setMaxFileCount(10000);
    org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.001);

    try (FileInputStream fis = new FileInputStream(filePath);
        XMLSlideShow ppt = new XMLSlideShow(fis)) {

      List<Slide> slides = new ArrayList<>();
      List<XSLFSlide> pptSlides = ppt.getSlides();

      if (pptSlides == null || pptSlides.isEmpty()) {
        log.warn("PowerPoint presentation has no slides: {}", filePath);
        return slides;
      }

      // Create presentation directory structure
      Path presentationDir = fileStorageService.createPresentationDirectory(presentation.getId());

      // Process each slide
      for (int i = 0; i < pptSlides.size(); i++) {
        XSLFSlide pptSlide = pptSlides.get(i);
        if (pptSlide == null) {
          log.warn("Null slide encountered at position {} in presentation {}", i, filePath);
          continue;
        }

        try {
          Slide slide = parseSlide(pptSlide, presentation, i + 1, presentationDir);
          if (slide != null) {
            slides.add(slide);
          }
        } catch (Exception e) {
          log.error(
              "Failed to parse slide {} in presentation {}: {}", i + 1, filePath, e.getMessage());
          // Continue with next slide instead of failing entire presentation
        }
      }

      log.info("Successfully parsed {} slides from presentation", slides.size());
      return slides;

    } catch (IOException e) {
      log.error("Failed to parse PowerPoint presentation with POI: {}", filePath, e);
      throw new PowerPointParsingException("Failed to parse PowerPoint presentation", e);
    } catch (Exception e) {
      log.error("Unexpected error parsing PowerPoint presentation: {}", filePath, e);
      throw new PowerPointParsingException("Unexpected error during PowerPoint parsing", e);
    }
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
   * Parses an individual slide and extracts its content.
   *
   * @param pptSlide the POI slide object
   * @param presentation the parent presentation
   * @param slideNumber the slide number (1-based)
   * @param presentationDir the directory for storing extracted content
   * @return parsed slide entity
   */
  private Slide parseSlide(
      XSLFSlide pptSlide, Presentation presentation, int slideNumber, Path presentationDir) {
    log.debug("Parsing slide {} of presentation {}", slideNumber, presentation.getId());

    Slide slide = new Slide();
    slide.setPresentation(presentation);
    slide.setSlideNumber(slideNumber);

    // Extract text content
    SlideContent content = contentExtractor.extractContent(pptSlide);
    slide.setTitle(content.getTitle());
    slide.setContent(content.getContentText()); // Set the content field (used by frontend)
    slide.setContentText(content.getContentText()); // Also set contentText for compatibility
    slide.setSpeakerNotes(content.getSpeakerNotes());
    slide.setLayoutType(content.getLayoutType());

    // Debug logging
    log.info("Slide {} - Title: {}", slideNumber, content.getTitle());
    log.info("Slide {} - Content: {}", slideNumber, content.getContentText());
    log.info("Slide {} - Notes: {}", slideNumber, content.getSpeakerNotes());

    // Slide image will be generated in the rendering phase
    // Create placeholder image for immediate UI feedback
    String placeholderPath =
        placeholderService.getOrCreatePlaceholder(presentation.getId().toString(), slideNumber);
    slide.setImagePath(placeholderPath);

    // Extract embedded images from slide (not renderings of the slide)
    List<SlideImage> embeddedImages =
        imageExtractor.extractImages(pptSlide, slide, presentationDir);
    slide.setSlideImages(embeddedImages);

    log.debug("Successfully parsed slide {} with title: {}", slideNumber, slide.getTitle());
    return slide;
  }

  @Override
  public boolean isAvailable() {
    // POI parser is always available
    return true;
  }

  @Override
  public String getParserName() {
    return "Apache POI Parser";
  }
}
