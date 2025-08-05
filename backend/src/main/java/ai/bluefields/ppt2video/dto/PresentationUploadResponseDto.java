package ai.bluefields.ppt2video.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for presentation upload response containing presentation metadata. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresentationUploadResponseDto {

  private String id;
  private String originalFilename;
  private Long fileSize;
  private String status;
  private LocalDateTime uploadTimestamp;
  private Integer slideCount;
  private String message;
}
