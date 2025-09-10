package ai.bluefields.ppt2video.dto.video;

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
public class AssetUploadRequest {
  private UUID assetMetadataId;
  private String sourceUrl;
  private String filename;
  private String contentType;
  private Long size;
  private Map<String, String> metadata;
}
