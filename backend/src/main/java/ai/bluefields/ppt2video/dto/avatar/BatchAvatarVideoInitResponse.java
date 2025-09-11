package ai.bluefields.ppt2video.dto.avatar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the initial batch avatar video generation request. Returns the count of videos
 * that will be generated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAvatarVideoInitResponse {

  /** Number of videos that will be generated (excluding skipped slides) */
  private int videosToGenerate;

  /** Total number of slides in the presentation */
  private int totalSlides;

  /** Number of slides that already have videos (will be skipped) */
  private int skippedSlides;
}
