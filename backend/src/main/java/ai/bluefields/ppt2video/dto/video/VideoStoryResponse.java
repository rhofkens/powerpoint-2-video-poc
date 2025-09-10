package ai.bluefields.ppt2video.dto.video;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for video story operations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStoryResponse {
  private UUID id;
  private String title;
  private String status;
  private UUID renderJobId;
  private String renderStatus;
  private Double progress;
  private String videoUrl;
  private boolean videoStored; // Whether video is stored in R2 or still at provider
  private Double duration;
  private String errorMessage;
  private String compositionData; // JSON composition for preview
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
