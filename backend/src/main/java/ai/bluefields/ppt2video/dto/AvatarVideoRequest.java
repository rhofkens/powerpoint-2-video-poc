package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AvatarProviderType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating an avatar video. Contains all necessary parameters for initiating avatar
 * video generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarVideoRequest {

  @NotNull(message = "Presentation ID is required")
  private UUID presentationId;

  @NotNull(message = "Slide ID is required")
  private UUID slideId;

  /** Avatar ID to use (optional, uses default if not specified) */
  private String avatarId;

  /** Background color for chroma key (optional, uses default if not specified) */
  private String backgroundColor;

  /** Provider type (optional, uses default if not specified) */
  private AvatarProviderType providerType;

  /** Whether to use published audio from R2 (default: true) */
  @Builder.Default private Boolean usePublishedAudio = true;

  /** Custom audio URL (optional, used if usePublishedAudio is false) */
  private String customAudioUrl;

  /** Additional metadata for the request */
  private String metadata;
}
