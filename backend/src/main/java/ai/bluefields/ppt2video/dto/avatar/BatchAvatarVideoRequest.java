package ai.bluefields.ppt2video.dto.avatar;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for batch avatar video generation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAvatarVideoRequest {

  /** Optional avatar ID to use for all videos. If null, selects based on narrative style */
  private String avatarId;

  /** Optional background color for videos. Defaults to #FFFFFF */
  private String backgroundColor;

  /** Whether to regenerate existing videos */
  private boolean regenerateExisting;

  /** Optional list of specific slide IDs to process. If null, processes all slides */
  private List<UUID> selectedSlideIds;

  /** Whether to use published audio from R2 */
  private boolean usePublishedAudio;

  /** Optional custom audio URL to use */
  private String customAudioUrl;
}
