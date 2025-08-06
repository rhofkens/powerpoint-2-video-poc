package ai.bluefields.ppt2video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper for consistent response structure.
 *
 * @param <T> The type of data contained in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  /** Indicates whether the request was successful. */
  private boolean success;

  /** The response data payload. */
  private T data;

  /** A message providing additional information about the response. */
  private String message;

  /** Error details if the request failed. */
  private ErrorDetails error;

  /** Timestamp of the response. */
  @Builder.Default private long timestamp = System.currentTimeMillis();

  /** Error details structure. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorDetails {
    private String code;
    private String description;
    private String field;
  }
}
