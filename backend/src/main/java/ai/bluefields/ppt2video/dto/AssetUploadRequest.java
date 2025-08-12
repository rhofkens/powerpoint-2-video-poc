package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AssetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for requesting asset upload. Contains metadata required to initiate an asset upload to R2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetUploadRequest {

  @NotNull(message = "Presentation ID is required")
  private UUID presentationId;

  private UUID slideId;

  @NotNull(message = "Asset type is required")
  private AssetType assetType;

  @NotNull(message = "File name is required")
  @Size(min = 1, max = 255, message = "File name must be between 1 and 255 characters")
  private String fileName;

  @NotNull(message = "Content type is required")
  @Size(min = 1, max = 100, message = "Content type must be between 1 and 100 characters")
  private String contentType;

  private Long fileSize;

  private String checksum;

  private String metadata;
}
