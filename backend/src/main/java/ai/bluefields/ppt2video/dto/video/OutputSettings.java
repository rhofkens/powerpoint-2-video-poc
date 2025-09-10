package ai.bluefields.ppt2video.dto.video;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutputSettings {
  @Builder.Default private String format = "mp4";

  @Builder.Default private String resolution = "hd";

  @Builder.Default private Integer fps = 25;

  @Builder.Default private String quality = "medium";

  private Integer width;
  private Integer height;
  private String aspectRatio;
}
