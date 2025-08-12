package ai.bluefields.ppt2video.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for asset upload response. Contains the pre-signed upload URL and asset metadata. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadResponse {

  private UUID assetId;

  private String uploadUrl;

  private Instant expiresAt;

  private String bucketName;

  private String objectKey;

  private String uploadMethod;

  @Builder.Default private String instructions = "Use PUT method to upload file to the uploadUrl";
}
