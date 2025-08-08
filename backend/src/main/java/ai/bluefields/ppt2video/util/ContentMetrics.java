package ai.bluefields.ppt2video.util;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for calculating content metrics such as word count, reading time, and content
 * density. These metrics are used to determine appropriate narrative lengths for slides.
 */
@Slf4j
public class ContentMetrics {

  /** Average reading speed in words per minute for presentation narration */
  private static final int WORDS_PER_MINUTE = 150;

  /** Pattern to match words (sequences of word characters) */
  private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");

  /** Pattern to split text into sentences */
  private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s+");

  /**
   * Calculate the word count from text content.
   *
   * @param text The text to analyze
   * @return The number of words in the text
   */
  public static int calculateWordCount(String text) {
    if (text == null || text.trim().isEmpty()) {
      return 0;
    }

    var matcher = WORD_PATTERN.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }

    log.debug("Calculated word count: {} for text length: {}", count, text.length());
    return count;
  }

  /**
   * Calculate estimated reading time in seconds.
   *
   * @param wordCount The number of words
   * @return Estimated reading time in seconds
   */
  public static int calculateReadingTimeSeconds(int wordCount) {
    if (wordCount <= 0) {
      return 0;
    }

    // Calculate based on average reading speed
    double minutes = (double) wordCount / WORDS_PER_MINUTE;
    int seconds = (int) Math.ceil(minutes * 60);

    log.debug("Calculated reading time: {} seconds for {} words", seconds, wordCount);
    return seconds;
  }

  /**
   * Calculate estimated reading time in seconds from text.
   *
   * @param text The text to analyze
   * @return Estimated reading time in seconds
   */
  public static int calculateReadingTimeSeconds(String text) {
    int wordCount = calculateWordCount(text);
    return calculateReadingTimeSeconds(wordCount);
  }

  /**
   * Calculate content density score (0.0 to 1.0) based on various factors.
   *
   * @param text The text to analyze
   * @param hasVisuals Whether the slide has visual elements
   * @param hasData Whether the slide contains data/charts
   * @return Content density score between 0.0 (low) and 1.0 (high)
   */
  public static double calculateContentDensity(String text, boolean hasVisuals, boolean hasData) {
    if (text == null || text.trim().isEmpty()) {
      return hasVisuals || hasData ? 0.3 : 0.0;
    }

    int wordCount = calculateWordCount(text);
    int sentenceCount = countSentences(text);

    // Base density from word count (normalized to 0-1 scale)
    // Assuming 200+ words is very dense for a slide
    double wordDensity = Math.min(wordCount / 200.0, 1.0);

    // Sentence complexity factor (average words per sentence)
    double avgWordsPerSentence = sentenceCount > 0 ? (double) wordCount / sentenceCount : wordCount;
    // Complex sentences have 20+ words on average
    double sentenceComplexity = Math.min(avgWordsPerSentence / 20.0, 1.0);

    // Visual complexity adds to density
    double visualFactor = hasVisuals ? 0.2 : 0.0;
    double dataFactor = hasData ? 0.3 : 0.0;

    // Weighted combination
    double density =
        (wordDensity * 0.4)
            + (sentenceComplexity * 0.2)
            + (visualFactor * 0.2)
            + (dataFactor * 0.2);

    // Ensure within bounds
    density = Math.max(0.0, Math.min(1.0, density));

    log.debug(
        "Content density: {} (words: {}, sentences: {}, visuals: {}, data: {})",
        String.format("%.2f", density),
        wordCount,
        sentenceCount,
        hasVisuals,
        hasData);

    return density;
  }

  /**
   * Count the number of sentences in text.
   *
   * @param text The text to analyze
   * @return Number of sentences
   */
  private static int countSentences(String text) {
    if (text == null || text.trim().isEmpty()) {
      return 0;
    }

    // Split by sentence-ending punctuation
    String[] sentences = SENTENCE_PATTERN.split(text.trim());
    int count = sentences.length;

    // Account for the last sentence if it doesn't end with punctuation
    if (!text.trim().matches(".*[.!?]\\s*$") && !text.trim().isEmpty()) {
      count = Math.max(1, count);
    }

    return count;
  }

  /**
   * Estimate the number of words needed for a target duration.
   *
   * @param targetDurationSeconds The target duration in seconds
   * @return Estimated word count needed
   */
  public static int estimateWordCountForDuration(int targetDurationSeconds) {
    if (targetDurationSeconds <= 0) {
      return 0;
    }

    double minutes = targetDurationSeconds / 60.0;
    int wordCount = (int) Math.round(minutes * WORDS_PER_MINUTE);

    log.debug("Estimated {} words needed for {} seconds", wordCount, targetDurationSeconds);
    return wordCount;
  }

  /**
   * Get a word count range for a duration range.
   *
   * @param minDurationSeconds Minimum duration in seconds
   * @param maxDurationSeconds Maximum duration in seconds
   * @return Array with [minWords, maxWords]
   */
  public static int[] getWordCountRange(int minDurationSeconds, int maxDurationSeconds) {
    return new int[] {
      estimateWordCountForDuration(minDurationSeconds),
      estimateWordCountForDuration(maxDurationSeconds)
    };
  }
}
