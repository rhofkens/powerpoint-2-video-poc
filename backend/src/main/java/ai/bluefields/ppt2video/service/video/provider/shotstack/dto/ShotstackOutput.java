package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shotstack output configuration */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackOutput {
  @Builder.Default private String format = "mp4";
  @Builder.Default private String resolution = "hd";
  @Builder.Default private Integer fps = 25;
  @Builder.Default private String quality = "medium";
  private ShotstackSize size;
  private String aspectRatio;
  private Integer scaleTo;
  private ShotstackRange range;
  private ShotstackPoster poster;
  private ShotstackThumbnail thumbnail;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShotstackSize {
    private Integer width;
    private Integer height;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShotstackRange {
    private Double start;
    private Double length;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShotstackPoster {
    private Double capture;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ShotstackThumbnail {
    private Double capture;
    private Integer scale;
  }
}
