package ai.bluefields.ppt2video.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating an intro video. Contains the presentation ID and optional regeneration
 * flag.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntroVideoRequest {

  @NotNull(message = "Presentation ID is required")
  private UUID presentationId;

  @Builder.Default private boolean regenerate = false;

  private String aspectRatio; // Optional: defaults to "16:9"

  private String resolution; // Optional: defaults to "1080p"
}
