package ai.bluefields.ppt2video.dto.veo.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw response from Google Veo API when checking video generation status. This DTO maps directly to
 * the API response structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VeoApiStatusResponse {

  @JsonProperty("name")
  private String name; // The operation/generation ID

  @JsonProperty("done")
  private Boolean done; // True when operation is complete

  @JsonProperty("metadata")
  private VeoMetadata metadata;

  @JsonProperty("response")
  private VeoOperationResponse response;

  @JsonProperty("error")
  private VeoError error;

  /** Metadata about the generation progress. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VeoMetadata {
    @JsonProperty("progressPercent")
    private Integer progressPercent;

    @JsonProperty("createTime")
    private String createTime;

    @JsonProperty("updateTime")
    private String updateTime;
  }

  /** Operation response wrapper. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VeoOperationResponse {
    @JsonProperty("@type")
    private String type;

    @JsonProperty("generateVideoResponse")
    private GenerateVideoResponse generateVideoResponse;
  }

  /** Video generation response. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GenerateVideoResponse {
    @JsonProperty("generatedSamples")
    private List<GeneratedSample> generatedSamples;
  }

  /** Generated video sample. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class GeneratedSample {
    @JsonProperty("video")
    private VideoInfo video;
  }

  /** Video information. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VideoInfo {
    @JsonProperty("uri")
    private String uri;
  }

  /** Error information if generation failed. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VeoError {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("details")
    private String details;
  }
}
