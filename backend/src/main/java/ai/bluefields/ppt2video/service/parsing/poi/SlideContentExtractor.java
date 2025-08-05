package ai.bluefields.ppt2video.service.parsing.poi;

import java.awt.geom.Rectangle2D;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.PlaceholderDetails;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

/** Extracts text content from PowerPoint slides including titles, body text, and speaker notes. */
@Component
@Slf4j
public class SlideContentExtractor {

  /**
   * Extracts all text content from a slide.
   *
   * @param pptSlide the slide to extract content from
   * @return extracted content
   */
  public SlideContent extractContent(XSLFSlide pptSlide) {
    if (pptSlide == null) {
      return new SlideContent();
    }

    SlideContent content = new SlideContent();

    // Extract layout type
    content.setLayoutType(extractLayoutType(pptSlide));

    // Extract text content
    extractTextContent(pptSlide, content);

    // Extract speaker notes
    content.setSpeakerNotes(extractSpeakerNotes(pptSlide));

    return content;
  }

  private String extractLayoutType(XSLFSlide pptSlide) {
    try {
      if (pptSlide.getSlideLayout() != null) {
        return pptSlide.getSlideLayout().getName();
      }
    } catch (Exception e) {
      log.debug("Could not get slide layout name: {}", e.getMessage());
    }
    return null;
  }

  private void extractTextContent(XSLFSlide pptSlide, SlideContent content) {
    StringBuilder contentText = new StringBuilder();
    String title = null;

    List<? extends Shape<?, ?>> shapes = pptSlide.getShapes();
    if (shapes != null) {
      for (Shape<?, ?> shape : shapes) {
        if (shape == null) {
          continue;
        }

        try {
          // Check if shape is a text shape
          if (shape instanceof XSLFTextShape) {
            XSLFTextShape textShape = (XSLFTextShape) shape;
            String text = extractTextFromShape(textShape);

            if (text != null && !text.trim().isEmpty()) {
              // Determine if this is likely a title
              if (title == null && isLikelyTitle(textShape)) {
                title = text.trim();
                log.info("Found title: {}", title);
              } else {
                if (contentText.length() > 0) {
                  contentText.append("\n");
                }
                contentText.append(text.trim());
                log.info("Found content text: {}", text.trim());
              }
            }
          } else {
            // Try to get text from other shape types
            String shapeText = shape.getShapeName();
            log.debug(
                "Non-text shape found: {} of type {}", shapeText, shape.getClass().getSimpleName());
          }
        } catch (Exception e) {
          log.debug("Error extracting text from shape: {}", e.getMessage());
        }
      }
    }

    content.setTitle(title);
    content.setContentText(contentText.toString());

    // Log final results
    log.debug("Final title: {}", title);
    log.debug("Final content: {}", contentText.toString());
  }

  private String extractSpeakerNotes(XSLFSlide pptSlide) {
    try {
      XSLFNotes notes = pptSlide.getNotes();
      if (notes == null) {
        log.debug("No notes found for slide");
        return null;
      }

      log.info("Found notes for slide, extracting text...");
      StringBuilder speakerNotes = new StringBuilder();
      List<? extends Shape<?, ?>> noteShapes = notes.getShapes();

      if (noteShapes != null) {
        log.debug("Found {} shapes in notes", noteShapes.size());

        for (Shape<?, ?> shape : noteShapes) {
          if (shape == null) {
            continue;
          }

          if (shape instanceof XSLFTextShape) {
            try {
              String noteText = extractTextFromShape((XSLFTextShape) shape);
              log.debug("Extracted note text: {}", noteText);

              if (noteText != null && !noteText.trim().isEmpty()) {
                // Skip the slide number placeholder that's often in notes
                if (!noteText.matches("^\\d+$")) {
                  if (speakerNotes.length() > 0) {
                    speakerNotes.append("\n");
                  }
                  speakerNotes.append(noteText.trim());
                  log.info("Added speaker note text: {}", noteText.trim());
                }
              }
            } catch (Exception e) {
              log.warn("Error extracting speaker note text: {}", e.getMessage());
            }
          } else {
            log.debug("Non-text shape in notes: {}", shape.getClass().getSimpleName());
          }
        }
      }

      String result = speakerNotes.length() > 0 ? speakerNotes.toString() : null;
      log.info("Final speaker notes: {}", result);
      return result;
    } catch (Exception e) {
      log.warn("Error extracting speaker notes: {}", e.getMessage(), e);
      return null;
    }
  }

  private String extractTextFromShape(XSLFTextShape textShape) {
    if (textShape == null) {
      return null;
    }

    StringBuilder text = new StringBuilder();
    try {
      List<? extends TextParagraph<?, ?, ?>> paragraphs = textShape.getTextParagraphs();
      if (paragraphs != null) {
        for (TextParagraph<?, ?, ?> paragraph : paragraphs) {
          if (paragraph == null) {
            continue;
          }

          List<? extends TextRun> textRuns = paragraph.getTextRuns();
          if (textRuns != null) {
            for (TextRun textRun : textRuns) {
              if (textRun != null) {
                String rawText = textRun.getRawText();
                if (rawText != null) {
                  text.append(rawText);
                }
              }
            }
          }
          text.append("\n");
        }
      }
    } catch (Exception e) {
      log.debug("Error extracting text from shape: {}", e.getMessage());
    }

    return text.toString().trim();
  }

  private boolean isLikelyTitle(XSLFTextShape textShape) {
    // Check if it's a title placeholder
    PlaceholderDetails placeholder = textShape.getPlaceholderDetails();
    if (placeholder != null) {
      try {
        if (placeholder.getPlaceholder() != null) {
          String placeholderType = placeholder.getPlaceholder().toString();
          log.debug("Placeholder type: {}", placeholderType);
          return placeholderType.toLowerCase().contains("title");
        }
      } catch (Exception e) {
        log.debug("Could not check placeholder type: {}", e.getMessage());
      }
    }

    // Check position (titles are usually at the top)
    Rectangle2D anchor = textShape.getAnchor();
    if (anchor != null) {
      log.debug("Shape position - Y: {}, Height: {}", anchor.getY(), anchor.getHeight());
      return anchor.getY() < 100; // Assume titles are in the top 100 points
    }

    return false;
  }

  /** Container for extracted slide content. */
  public static class SlideContent {
    private String title;
    private String contentText;
    private String speakerNotes;
    private String layoutType;

    // Getters and setters
    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public String getContentText() {
      return contentText;
    }

    public void setContentText(String contentText) {
      this.contentText = contentText;
    }

    public String getSpeakerNotes() {
      return speakerNotes;
    }

    public void setSpeakerNotes(String speakerNotes) {
      this.speakerNotes = speakerNotes;
    }

    public String getLayoutType() {
      return layoutType;
    }

    public void setLayoutType(String layoutType) {
      this.layoutType = layoutType;
    }
  }
}
