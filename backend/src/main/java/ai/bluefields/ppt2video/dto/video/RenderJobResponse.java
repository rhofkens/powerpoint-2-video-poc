package ai.bluefields.ppt2video.dto.video;

import ai.bluefields.ppt2video.entity.RenderJob;
import ai.bluefields.ppt2video.entity.RenderJobStatus;
import ai.bluefields.ppt2video.entity.RenderJobType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderJobResponse {
  private UUID id;
  private UUID presentationId;
  private UUID videoStoryId;
  private RenderJobType type;
  private RenderJobStatus status;
  private Integer progress;
  private String outputUrl;
  private String errorMessage;
  private LocalDateTime createdAt;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;

  public static RenderJobResponse from(RenderJob job) {
    return RenderJobResponse.builder()
        .id(job.getId())
        .presentationId(job.getPresentation().getId())
        .videoStoryId(job.getVideoStory() != null ? job.getVideoStory().getId() : null)
        .type(job.getType())
        .status(job.getStatus())
        .progress(job.getProgress())
        .outputUrl(job.getOutputUrl())
        .errorMessage(job.getErrorMessage())
        .createdAt(job.getCreatedAt())
        .startedAt(job.getStartedAt())
        .completedAt(job.getCompletedAt())
        .build();
  }
}
