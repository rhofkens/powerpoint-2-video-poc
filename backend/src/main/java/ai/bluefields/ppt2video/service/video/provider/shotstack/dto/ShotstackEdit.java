package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shotstack Edit object - the root of a composition */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackEdit {
  private ShotstackTimeline timeline;
  private ShotstackOutput output;
  private String callback;
  private ShotstackDisk disk;
}
