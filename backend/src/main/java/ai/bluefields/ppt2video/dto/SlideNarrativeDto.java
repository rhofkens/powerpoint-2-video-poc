package ai.bluefields.ppt2video.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for slide narrative information. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideNarrativeDto {

  private String id;
  private String narrativeText;
  private String enhancedNarrativeText;
  private String emotionIndicators;
  private String avatarInstructions;
  private String speechMarkers;
  private Integer durationSeconds;
  private Integer targetDurationSeconds;
  private String transitionPhrase;
  private String emphasisWords;
  private Integer version;
  private Boolean isActive;
  private Boolean hasEnhancement;
  private String generationMetadata;
  private String enhancementMetadata;
  private LocalDateTime enhancementTimestamp;
  private String enhancementModelUsed;
  private String modelUsed;
  private String promptVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /**
   * Gets the appropriate narrative text based on preference.
   *
   * @param preferEnhanced If true, returns enhanced text when available, otherwise original
   * @return The narrative text
   */
  public String getDisplayNarrative(boolean preferEnhanced) {
    return preferEnhanced && enhancedNarrativeText != null && !enhancedNarrativeText.isEmpty()
        ? enhancedNarrativeText
        : narrativeText;
  }

  /**
   * Gets the narrative text for subtitles (always original).
   *
   * @return The original narrative text without enhancements
   */
  public String getNarrativeForSubtitles() {
    return narrativeText;
  }

  /**
   * Gets the narrative text for audio (enhanced if available).
   *
   * @return The enhanced narrative text if available, otherwise original
   */
  public String getNarrativeForAudio() {
    return enhancedNarrativeText != null && !enhancedNarrativeText.isEmpty()
        ? enhancedNarrativeText
        : narrativeText;
  }
}
