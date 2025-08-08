package ai.bluefields.ppt2video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Data Transfer Object for SlideSpeech entities. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlideSpeechDto {

  private String id;
  private String presentationId;
  private String slideId;
  private String slideNarrativeId;
  private String audioFilePath;
  private String voiceId;
  private String voiceStyle;
  private String modelUsed;
  private Double durationSeconds;
  private List<TimestampDto> timestamps;
  private String requestId;
  private String outputFormat;
  private Map<String, Object> generationMetadata;
  private Boolean isActive;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** DTO for character-level timestamps. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TimestampDto {
    private String character;
    private Double startTime;
    private Double endTime;
  }
}
