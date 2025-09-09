package ai.bluefields.ppt2video.dto.veo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for building prompts for Google Veo API. Contains the main prompt, negative prompt, and
 * generation parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VeoPrompt {

  private String promptText;
  private String negativePrompt;

  @Builder.Default private String aspectRatio = "16:9";

  @Builder.Default private String resolution = "1080p";

  @Builder.Default private boolean personGeneration = false;
}
