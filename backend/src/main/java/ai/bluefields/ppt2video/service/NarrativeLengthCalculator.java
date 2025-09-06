package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import ai.bluefields.ppt2video.entity.SlideType;
import ai.bluefields.ppt2video.model.DurationRange;
import ai.bluefields.ppt2video.util.ContentMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for calculating appropriate narrative lengths based on slide type, content density, and
 * other factors. Ensures narratives are properly paced for video generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeLengthCalculator {

  private final SlideTypeDetector slideTypeDetector;

  // Duration ranges for different slide types (in seconds)
  @Value("${app.narrative.duration.intro.min:5}")
  private int introDurationMin;

  @Value("${app.narrative.duration.intro.max:15}")
  private int introDurationMax;

  @Value("${app.narrative.duration.separator.min:3}")
  private int separatorDurationMin;

  @Value("${app.narrative.duration.separator.max:8}")
  private int separatorDurationMax;

  @Value("${app.narrative.duration.thank-you.min:6}")
  private int thankYouDurationMin;

  @Value("${app.narrative.duration.thank-you.max:12}")
  private int thankYouDurationMax;

  @Value("${app.narrative.duration.content.min:5}")
  private int contentDurationMin;

  @Value("${app.narrative.duration.content.max:30}")
  private int contentDurationMax;

  // Content density thresholds
  @Value("${app.narrative.density.low-threshold:0.3}")
  private double lowDensityThreshold;

  @Value("${app.narrative.density.high-threshold:0.7}")
  private double highDensityThreshold;

  /**
   * Calculate the target duration range for a slide based on its type and content.
   *
   * @param slide The slide to calculate duration for
   * @param slideType The detected slide type
   * @return Duration range with min and max seconds
   */
  public DurationRange calculateTargetDuration(Slide slide, SlideType slideType) {
    log.debug("Calculating target duration for slide {} of type {}", slide.getId(), slideType);

    // Get base duration range by slide type
    DurationRange baseDuration = getBaseDurationByType(slideType);

    // Adjust for content density if it's a content slide
    if (slideType == SlideType.CONTENT) {
      baseDuration = adjustForContentDensity(slide, baseDuration);
    }

    log.info(
        "Calculated duration range for slide {}: {} (type: {})",
        slide.getSlideNumber(),
        baseDuration.toFormattedString(),
        slideType);

    return baseDuration;
  }

  /**
   * Calculate the target duration range for a slide, auto-detecting its type.
   *
   * @param slide The slide to calculate duration for
   * @param totalSlides Total number of slides in the presentation
   * @return Duration range with min and max seconds
   */
  public DurationRange calculateTargetDuration(Slide slide, int totalSlides) {
    SlideType slideType = slideTypeDetector.detectSlideType(slide, totalSlides);
    return calculateTargetDuration(slide, slideType);
  }

  /**
   * Get base duration range by slide type.
   *
   * @param slideType The type of slide
   * @return Base duration range
   */
  private DurationRange getBaseDurationByType(SlideType slideType) {
    return switch (slideType) {
      case INTRO -> new DurationRange(introDurationMin, introDurationMax);
      case SEPARATOR -> new DurationRange(separatorDurationMin, separatorDurationMax);
      case THANK_YOU -> new DurationRange(thankYouDurationMin, thankYouDurationMax);
      case CONTENT, UNKNOWN -> new DurationRange(contentDurationMin, contentDurationMax);
    };
  }

  /**
   * Adjust duration range based on content density.
   *
   * @param slide The slide to analyze
   * @param baseDuration The base duration range
   * @return Adjusted duration range
   */
  private DurationRange adjustForContentDensity(Slide slide, DurationRange baseDuration) {
    // Calculate content metrics
    String content = slide.getContentText() != null ? slide.getContentText() : "";
    int wordCount = ContentMetrics.calculateWordCount(content);

    // Check for visual elements and data
    boolean hasVisuals = hasVisualElements(slide);
    boolean hasData = hasDataElements(slide);

    // Calculate density
    double density = ContentMetrics.calculateContentDensity(content, hasVisuals, hasData);

    log.debug(
        "Content density for slide {}: {} (words: {}, visuals: {}, data: {})",
        slide.getSlideNumber(),
        String.format("%.2f", density),
        wordCount,
        hasVisuals,
        hasData);

    // Adjust duration based on density
    if (density < lowDensityThreshold) {
      // Low density: use lower end of range
      int adjustedMin = baseDuration.getMinSeconds();
      int adjustedMax =
          baseDuration.getMinSeconds()
              + (baseDuration.getMaxSeconds() - baseDuration.getMinSeconds()) / 2;
      return new DurationRange(adjustedMin, adjustedMax);
    } else if (density > highDensityThreshold) {
      // High density: use upper end of range
      int adjustedMin =
          baseDuration.getMinSeconds()
              + (baseDuration.getMaxSeconds() - baseDuration.getMinSeconds()) / 2;
      int adjustedMax = baseDuration.getMaxSeconds();
      return new DurationRange(adjustedMin, adjustedMax);
    }

    // Medium density: use full range
    return baseDuration;
  }

  /**
   * Check if a slide has visual elements based on its analysis.
   *
   * @param slide The slide to check
   * @return true if visual elements are detected
   */
  private boolean hasVisualElements(Slide slide) {
    SlideAnalysis analysis = slide.getSlideAnalysis();
    if (analysis == null) {
      return false;
    }

    String visualConcepts = analysis.getVisualConcepts();
    if (visualConcepts != null && !visualConcepts.equals("[]") && !visualConcepts.isEmpty()) {
      return true;
    }

    // Check for images in slide
    return slide.getSlideImages() != null && !slide.getSlideImages().isEmpty();
  }

  /**
   * Check if a slide has data elements (charts, tables, etc.) based on its analysis.
   *
   * @param slide The slide to check
   * @return true if data elements are detected
   */
  private boolean hasDataElements(Slide slide) {
    SlideAnalysis analysis = slide.getSlideAnalysis();
    if (analysis == null) {
      return false;
    }

    String dataInsights = analysis.getDataInsights();
    if (dataInsights != null && !dataInsights.trim().isEmpty()) {
      return true;
    }

    // Check for data-related keywords in content
    String content = slide.getContentText();
    if (content != null) {
      String lowerContent = content.toLowerCase();
      return lowerContent.contains("chart")
          || lowerContent.contains("graph")
          || lowerContent.contains("table")
          || lowerContent.contains("data")
          || lowerContent.contains("%")
          || lowerContent.matches(".*\\d+.*"); // Contains numbers
    }

    return false;
  }

  /**
   * Get the optimal target duration (midpoint of range) for a slide.
   *
   * @param slide The slide to calculate duration for
   * @param totalSlides Total number of slides
   * @return Target duration in seconds
   */
  public int getOptimalDuration(Slide slide, int totalSlides) {
    DurationRange range = calculateTargetDuration(slide, totalSlides);
    return range.getMidpoint();
  }

  /**
   * Validate if a generated narrative duration is appropriate for the slide.
   *
   * @param slide The slide
   * @param actualDurationSeconds The actual duration of generated narrative
   * @param slideType The slide type
   * @return true if duration is within acceptable range
   */
  public boolean isDurationAppropriate(
      Slide slide, int actualDurationSeconds, SlideType slideType) {
    DurationRange targetRange = calculateTargetDuration(slide, slideType);
    boolean isAppropriate = targetRange.isWithinRange(actualDurationSeconds);

    if (!isAppropriate) {
      log.warn(
          "Narrative duration {} seconds is outside target range {} for slide {} (type: {})",
          actualDurationSeconds,
          targetRange.toFormattedString(),
          slide.getSlideNumber(),
          slideType);
    }

    return isAppropriate;
  }
}
