package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.UploadStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for asset metadata representation. Used for API responses containing asset information. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDto {

  private UUID id;

  private UUID presentationId;

  private UUID slideId;

  private AssetType assetType;

  private String bucketName;

  private String objectKey;

  private String fileName;

  private Long fileSize;

  private String contentType;

  private String checksum;

  private String metadata;

  private UploadStatus uploadStatus;

  private String errorMessage;

  private String downloadUrl;

  private LocalDateTime downloadUrlExpiresAt;

  private LocalDateTime createdAt;

  private LocalDateTime updatedAt;
}
