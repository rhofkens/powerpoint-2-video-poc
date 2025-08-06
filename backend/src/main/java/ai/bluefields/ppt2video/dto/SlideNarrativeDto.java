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
  private String emotionIndicators;
  private String avatarInstructions;
  private String speechMarkers;
  private Integer durationSeconds;
  private String transitionPhrase;
  private String emphasisWords;
  private Integer version;
  private Boolean isActive;
  private String generationMetadata;
  private String modelUsed;
  private String promptVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
