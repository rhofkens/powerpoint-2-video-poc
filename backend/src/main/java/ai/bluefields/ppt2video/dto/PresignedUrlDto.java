package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.UrlType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for pre-signed URL representation. Used for API responses containing URL information. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlDto {

  private UUID id;

  private UUID assetMetadataId;

  private UrlType urlType;

  private String presignedUrl;

  private LocalDateTime expiresAt;

  private Boolean isActive;

  private Integer accessCount;

  private LocalDateTime createdAt;

  private String createdBy;
}
