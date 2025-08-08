package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideType;
import ai.bluefields.ppt2video.util.ContentMetrics;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for detecting and classifying slide types based on their content, position, and patterns.
 * This classification helps determine appropriate narrative lengths and styles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideTypeDetector {

  /** Patterns for detecting thank you slides */
  private static final List<Pattern> THANK_YOU_PATTERNS =
      List.of(
          Pattern.compile("\\bthank\\s+you\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bthanks\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bquestions\\?", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bq\\s*&\\s*a\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bcontact\\s+us\\b", Pattern.CASE_INSENSITIVE),
          Pattern.compile("\\bget\\s+in\\s+touch\\b", Pattern.CASE_INSENSITIVE));

  /** Patterns for detecting separator/divider slides */
  private static final List<Pattern> SEPARATOR_PATTERNS =
      List.of(
          Pattern.compile("^section\\s+\\d+", Pattern.CASE_INSENSITIVE),
          Pattern.compile("^part\\s+\\d+", Pattern.CASE_INSENSITIVE),
          Pattern.compile("^chapter\\s+\\d+", Pattern.CASE_INSENSITIVE),
          Pattern.compile("^module\\s+\\d+", Pattern.CASE_INSENSITIVE));

  /** Maximum word count for a separator slide */
  private static final int SEPARATOR_MAX_WORDS = 10;

  /** Maximum word count for a thank you slide */
  private static final int THANK_YOU_MAX_WORDS = 20;

  /**
   * Detect the type of a slide based on its content and position.
   *
   * @param slide The slide to analyze
   * @param totalSlides Total number of slides in the presentation
   * @return The detected slide type
   */
  public SlideType detectSlideType(Slide slide, int totalSlides) {
    if (slide == null) {
      return SlideType.UNKNOWN;
    }

    int slideNumber = slide.getSlideNumber();
    String title = slide.getTitle() != null ? slide.getTitle() : "";
    String content = slide.getContentText() != null ? slide.getContentText() : "";
    String combinedText = title + " " + content;

    log.debug(
        "Detecting type for slide {} of {}: title='{}', content length={}",
        slideNumber,
        totalSlides,
        title,
        content.length());

    // Check if it's the first slide (intro)
    if (slideNumber == 1) {
      log.debug("Detected INTRO slide (first slide)");
      return SlideType.INTRO;
    }

    // Check if it's the last slide and looks like a thank you slide
    if (slideNumber == totalSlides && isThankYouSlide(combinedText)) {
      log.debug("Detected THANK_YOU slide (last slide with thank you pattern)");
      return SlideType.THANK_YOU;
    }

    // Check for thank you patterns anywhere in the presentation
    if (isThankYouSlide(combinedText)) {
      log.debug("Detected THANK_YOU slide (contains thank you pattern)");
      return SlideType.THANK_YOU;
    }

    // Check for separator slides
    if (isSeparatorSlide(title, content)) {
      log.debug("Detected SEPARATOR slide");
      return SlideType.SEPARATOR;
    }

    // Default to content slide
    log.debug("Detected CONTENT slide (default)");
    return SlideType.CONTENT;
  }

  /**
   * Check if a slide is a thank you slide based on text patterns.
   *
   * @param text The text to check
   * @return true if it matches thank you patterns
   */
  private boolean isThankYouSlide(String text) {
    if (text == null || text.trim().isEmpty()) {
      return false;
    }

    // Check word count - thank you slides are typically brief
    int wordCount = ContentMetrics.calculateWordCount(text);
    if (wordCount > THANK_YOU_MAX_WORDS) {
      return false;
    }

    // Check for thank you patterns
    for (Pattern pattern : THANK_YOU_PATTERNS) {
      if (pattern.matcher(text).find()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if a slide is a separator/divider slide.
   *
   * @param title The slide title
   * @param content The slide content
   * @return true if it's a separator slide
   */
  private boolean isSeparatorSlide(String title, String content) {
    // Separator slides typically have minimal content
    int contentWordCount = ContentMetrics.calculateWordCount(content);
    if (contentWordCount > SEPARATOR_MAX_WORDS) {
      return false;
    }

    // Check title for separator patterns
    if (title != null && !title.trim().isEmpty()) {
      for (Pattern pattern : SEPARATOR_PATTERNS) {
        if (pattern.matcher(title).find()) {
          return true;
        }
      }

      // Check if title is very short (1-3 words) and content is minimal
      int titleWordCount = ContentMetrics.calculateWordCount(title);
      if (titleWordCount <= 3 && contentWordCount <= 5) {
        // Likely a section header
        return true;
      }
    }

    return false;
  }

  /**
   * Detect types for all slides in a list.
   *
   * @param slides List of slides to analyze
   * @return List of slide types in the same order
   */
  public List<SlideType> detectSlideTypes(List<Slide> slides) {
    if (slides == null || slides.isEmpty()) {
      return List.of();
    }

    int totalSlides = slides.size();
    return slides.stream().map(slide -> detectSlideType(slide, totalSlides)).toList();
  }

  /**
   * Get a human-readable description of the slide type.
   *
   * @param slideType The slide type
   * @return Description of the slide type
   */
  public String getSlideTypeDescription(SlideType slideType) {
    return switch (slideType) {
      case INTRO -> "Introduction slide";
      case CONTENT -> "Content slide";
      case SEPARATOR -> "Section separator";
      case THANK_YOU -> "Thank you slide";
      case UNKNOWN -> "Unknown slide type";
    };
  }
}
