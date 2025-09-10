package ai.bluefields.ppt2video.dto.video;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderResponse {
  private String renderJobId;
  private String status;
  private String message;
  private LocalDateTime submittedAt;
  private String estimatedDuration;
}
