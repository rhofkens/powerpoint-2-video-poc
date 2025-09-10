package ai.bluefields.ppt2video.dto.video;

import ai.bluefields.ppt2video.entity.RenderJobStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderStatus {
  private String renderJobId;
  private RenderJobStatus status;
  private Integer progress;
  private String outputUrl;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private Long renderTimeMs;

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }
}
