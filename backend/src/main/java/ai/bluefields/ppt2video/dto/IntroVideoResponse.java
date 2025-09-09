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
 * Response DTO for intro video operations. Contains the intro video details and generation status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntroVideoResponse {

  private UUID id;
  private UUID presentationId;
  private String veoGenerationId;
  private AvatarGenerationStatusType status;
  private String promptText;
  private String negativePrompt;
  private ColorPaletteDto colorPalette;
  private String veoModelVersion;
  private String aspectRatio;
  private String resolution;
  private Double durationSeconds;
  private String googleVideoUrl;
  private String publishedUrl;
  private UUID r2AssetId;
  private String errorMessage;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
