package ai.bluefields.ppt2video.dto.video;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoComposition {
  private String id;
  private JsonNode timeline;
  private OutputSettings output;
  private Map<String, Object> metadata;
  private Double duration;
}
