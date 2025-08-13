package ai.bluefields.ppt2video.service.avatar.providers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response model for HeyGen video generation API. Based on HeyGen API v2 specifications. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeyGenVideoResponse {

  /** Status code of the response */
  @JsonProperty("code")
  private Integer code;

  /** Response message */
  @JsonProperty("message")
  private String message;

  /** Response data containing video details */
  @JsonProperty("data")
  private VideoData data;

  /** Error details if request failed */
  @JsonProperty("error")
  private ErrorDetails error;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VideoData {
    /** Unique video ID from HeyGen (v2 API uses video_id, v1 uses id) */
    @JsonProperty("video_id")
    private String videoId;

    /** Unique video ID from HeyGen v1 API */
    @JsonProperty("id")
    private String id;

    /** Current status of the video */
    @JsonProperty("status")
    private String status;

    /** Video URL when completed */
    @JsonProperty("video_url")
    private String videoUrl;

    /** Thumbnail URL */
    @JsonProperty("thumbnail_url")
    private String thumbnailUrl;

    /** Video duration in seconds */
    @JsonProperty("duration")
    private Double duration;

    /** Estimated time to complete in seconds */
    @JsonProperty("eta")
    private Integer eta;

    /** Creation timestamp */
    @JsonProperty("created_at")
    private Long createdAt;

    /** Update timestamp */
    @JsonProperty("updated_at")
    private Long updatedAt;

    /** Additional metadata */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorDetails {
    /** Error code */
    @JsonProperty("code")
    private String code;

    /** Error message */
    @JsonProperty("message")
    private String message;

    /** Additional error details */
    @JsonProperty("details")
    private Map<String, Object> details;
  }

  /**
   * Check if the response indicates success.
   *
   * @return true if successful
   */
  public boolean isSuccess() {
    // For v1 API (status checks): code should be 100 and data should have id field
    // For v2 API (creation): data should have video_id field
    // Both cases: error should be null
    if (error != null) {
      return false;
    }

    // Check for v1 API (status endpoint)
    if (code != null && code == 100 && data != null) {
      return true;
    }

    // Check for v2 API (creation endpoint)
    if (data != null && data.getVideoId() != null) {
      return true;
    }

    return false;
  }

  /**
   * Get the video ID from the response.
   *
   * @return the video ID or null
   */
  public String getVideoId() {
    if (data != null) {
      // v2 API uses video_id, v1 API uses id
      return data.getVideoId() != null ? data.getVideoId() : data.getId();
    }
    return null;
  }

  /**
   * Get the video status from the response.
   *
   * @return the status or null
   */
  public String getStatus() {
    return data != null ? data.getStatus() : null;
  }
}
