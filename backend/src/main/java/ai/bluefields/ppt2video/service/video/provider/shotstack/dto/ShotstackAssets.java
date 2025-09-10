package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/** Simple asset types for Shotstack */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackAssets {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VideoAsset {
    private final String type = "video";
    private String src;
    private Double volume;
    private Double trim;
    private Double volumeEffect;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ImageAsset {
    private final String type = "image";
    private String src;
    private Object crop;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HtmlAsset {
    private final String type = "html";
    private String html;
    private String css;
    private Integer width;
    private Integer height;
    private String background;
    private String position;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LumaAsset {
    private final String type = "luma";
    private String src;
    private Double trim;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Offset {
    private Double x;
    private Double y;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Transition {
    @Builder.Default private String in = "fade";
    @Builder.Default private String out = "fade";
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Size {
    private Integer width;
    private Integer height;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Output {
    @Builder.Default private String format = "mp4";
    @Builder.Default private String resolution = "hd";
    @Builder.Default private Integer fps = 25;
    @Builder.Default private String quality = "medium";
    private Size size;
    private String aspectRatio;
    private Integer scaleTo;
    private Range range;
    private Poster poster;
    private Thumbnail thumbnail;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Range {
    private Double start;
    private Double length;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Poster {
    private Double capture;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Thumbnail {
    private Double capture;
    private Integer scale;
  }
}
