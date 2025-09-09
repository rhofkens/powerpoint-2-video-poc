package ai.bluefields.ppt2video.dto.veo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response from Google Veo API when initiating video generation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VeoGenerationResponse {

  @JsonProperty("generation_id")
  private String generationId;

  @JsonProperty("status")
  private String status;

  @JsonProperty("message")
  private String message;

  @JsonProperty("estimated_time_seconds")
  private Integer estimatedTimeSeconds;
}
