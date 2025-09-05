package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarProviderType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for avatar video operations. Contains the avatar video details and generation
 * status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AvatarVideoResponse {

  private UUID id;
  private UUID presentationId;
  private UUID slideId;
  private AvatarProviderType providerType;
  private String providerVideoId;
  private AvatarGenerationStatusType status;
  private String avatarId;
  private String backgroundColor;
  private String audioUrl;
  private String videoUrl;
  private String publishedUrl;
  private UUID r2AssetId;
  private Double durationSeconds;
  private Integer progressPercentage;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String metadata;
}
