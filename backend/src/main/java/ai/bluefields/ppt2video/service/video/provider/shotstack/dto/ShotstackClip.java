package ai.bluefields.ppt2video.service.video.provider.shotstack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Shotstack Clip with asset and timing */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShotstackClip {
  private ShotstackAsset asset;
  private Double start;
  private Double length;
  private String fit;
  private Double scale;
  private String position;
  private ShotstackOffset offset;
  private ShotstackTransition transition;
  private ShotstackEffect effect;
  private ShotstackFilter filter;
  private Double opacity;
  private ShotstackTransform transform;
}
