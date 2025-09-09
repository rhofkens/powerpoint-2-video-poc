package ai.bluefields.ppt2video.dto.veo.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw response from Google Veo API when initiating video generation. This DTO maps directly to the
 * API response structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VeoApiGenerationResponse {

  @JsonProperty("name")
  private String name; // The operation/generation ID

  @JsonProperty("status")
  private String status;

  @JsonProperty("message")
  private String message;

  @JsonProperty("estimatedTimeSeconds")
  private Integer estimatedTimeSeconds;
}
