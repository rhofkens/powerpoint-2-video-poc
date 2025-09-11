package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for avatar video status updates. Provides current status and progress information for a video
 * generation job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvatarVideoStatusDto {

  private UUID id;
  private UUID slideId;
  private Integer slideNumber;
  private AvatarGenerationStatusType status;
  private Integer progressPercentage;
  private String videoUrl;
  private String publishedUrl; // R2 published URL
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private Double durationSeconds;
}
