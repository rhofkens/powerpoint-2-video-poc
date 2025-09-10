package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shotstack Timeline containing tracks */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackTimeline {
  private String soundtrack;
  private String background;
  private List<ShotstackFont> fonts;
  private List<ShotstackTrack> tracks;
  private ShotstackCache cache;
}
