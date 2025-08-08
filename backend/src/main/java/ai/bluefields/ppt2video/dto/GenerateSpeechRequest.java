package ai.bluefields.ppt2video.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for generating speech for a slide. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateSpeechRequest {

  @NotNull(message = "Presentation ID is required")
  private String presentationId;

  @NotNull(message = "Slide ID is required")
  private String slideId;

  @Builder.Default private String narrativeStyle = "business";

  @Builder.Default private Boolean forceRegenerate = false;
}
