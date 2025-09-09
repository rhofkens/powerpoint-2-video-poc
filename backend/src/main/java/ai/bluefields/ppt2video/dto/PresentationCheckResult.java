package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of presentation-level checks in the preflight system. Contains validation results for
 * presentation-wide assets like intro videos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresentationCheckResult {

  /** Status of the intro video check */
  private CheckStatus introVideoStatus;

  /** ID of the intro video if it exists */
  private UUID introVideoId;

  /** Published URL of the intro video if available */
  private String introVideoUrl;

  /** Current generation status of the intro video */
  private AvatarGenerationStatusType generationStatus;

  /** When the intro video was created */
  private LocalDateTime createdAt;

  /** When the intro video generation completed */
  private LocalDateTime completedAt;

  /** Duration of the intro video in seconds */
  private Double durationSeconds;

  /** List of issues found during presentation-level checks */
  private List<String> issues;

  /** Additional metadata about the presentation checks */
  private Map<String, Object> metadata;
}
