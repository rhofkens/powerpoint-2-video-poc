package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shotstack transition effects */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackTransition {
  @Builder.Default private String in = "fade";
  @Builder.Default private String out = "fade";
}
