package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for narrative shortening operation. Contains both original and shortened narratives
 * with metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenNarrativeResponse {

  private UUID narrativeId;
  private UUID slideId;

  // Original narrative details
  private String originalText;
  private int originalWordCount;
  private int originalDurationSeconds;

  // Shortened narrative details
  private String shortenedText;
  private int newWordCount;
  private int newDurationSeconds;

  // Metrics
  private int requestedReduction; // Requested reduction percentage
  private double actualReduction; // Actual reduction achieved

  // Metadata
  private int version;

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private LocalDateTime createdAt;

  private boolean success;
  private String message;

  /** Create response from narratives. */
  public static ShortenNarrativeResponse fromNarratives(
      SlideNarrative original, SlideNarrative shortened, int requestedReduction) {

    int originalWords = countWords(original.getNarrativeText());
    int newWords = countWords(shortened.getNarrativeText());
    double actualReduction = ((double) (originalWords - newWords) / originalWords) * 100;

    return ShortenNarrativeResponse.builder()
        .narrativeId(shortened.getId())
        .slideId(shortened.getSlide().getId())
        .originalText(original.getNarrativeText())
        .originalWordCount(originalWords)
        .originalDurationSeconds(
            original.getDurationSeconds() != null ? original.getDurationSeconds() : 0)
        .shortenedText(shortened.getNarrativeText())
        .newWordCount(newWords)
        .newDurationSeconds(
            shortened.getDurationSeconds() != null ? shortened.getDurationSeconds() : 0)
        .requestedReduction(requestedReduction)
        .actualReduction(Math.round(actualReduction * 10.0) / 10.0) // Round to 1 decimal
        .version(shortened.getVersion())
        .createdAt(shortened.getCreatedAt())
        .success(true)
        .message(
            String.format(
                "Narrative shortened by %.1f%% (requested %d%%)",
                actualReduction, requestedReduction))
        .build();
  }

  private static int countWords(String text) {
    if (text == null || text.trim().isEmpty()) {
      return 0;
    }
    return text.trim().split("\\s+").length;
  }
}
