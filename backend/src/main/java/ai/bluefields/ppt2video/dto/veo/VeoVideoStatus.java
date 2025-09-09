package ai.bluefields.ppt2video.dto.veo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Status response from Google Veo API when checking video generation progress. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VeoVideoStatus {

  @JsonProperty("generation_id")
  private String generationId;

  @JsonProperty("state")
  private VeoState state;

  @JsonProperty("video_url")
  private String videoUrl;

  @JsonProperty("thumbnail_url")
  private String thumbnailUrl;

  @JsonProperty("progress")
  private Integer progress;

  @JsonProperty("error_message")
  private String errorMessage;

  @JsonProperty("duration_seconds")
  private Double durationSeconds;

  @JsonProperty("resolution")
  private String resolution;

  @JsonProperty("aspect_ratio")
  private String aspectRatio;

  /** Enum representing the state of video generation in Veo API. */
  public enum VeoState {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
  }
}
