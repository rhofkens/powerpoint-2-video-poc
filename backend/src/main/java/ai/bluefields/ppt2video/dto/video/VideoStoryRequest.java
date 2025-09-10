package ai.bluefields.ppt2video.dto.video;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for creating a new video story. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStoryRequest {
  private UUID presentationId;
  private UUID introVideoId;
  private String title;
  private String description;
  private String providerType; // "shotstack" for now
}
