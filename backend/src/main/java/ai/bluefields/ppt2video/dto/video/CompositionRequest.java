package ai.bluefields.ppt2video.dto.video;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompositionRequest {
  private UUID presentationId;
  private UUID videoStoryId;
  private String templateId;
  private List<AssetReference> assets;
  private Map<String, Object> parameters;
  private OutputSettings outputSettings;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AssetReference {
  private String assetId;
  private String type;
  private String url;
  private Map<String, Object> properties;
}
