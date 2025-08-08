package ai.bluefields.ppt2video.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for slide analysis information. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideAnalysisDto {

  private String id;
  private String generalMessage;
  private String visualConcepts;
  private String keyPoints;
  private String dataInsights;
  private String transitionContext;
  private String emphasisLevel;
  private String slideType;
  private String analysisMetadata;
  private String modelUsed;
  private String promptVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
