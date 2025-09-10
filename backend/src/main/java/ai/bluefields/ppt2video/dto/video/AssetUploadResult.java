package ai.bluefields.ppt2video.dto.video;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadResult {
  private String providerAssetId;
  private String providerUrl;
  private String status;
  private LocalDateTime uploadedAt;
  private LocalDateTime expiresAt;
}
