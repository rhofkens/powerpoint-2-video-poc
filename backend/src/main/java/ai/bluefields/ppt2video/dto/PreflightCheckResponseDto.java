package ai.bluefields.ppt2video.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO containing the complete results of a preflight check. Includes detailed status for
 * each slide and an overall summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreflightCheckResponseDto {

  /** The ID of the presentation that was checked */
  private UUID presentationId;

  /** Overall status of the presentation readiness */
  private PreflightStatus overallStatus;

  /** Detailed check results for each slide */
  private List<SlideCheckResult> slideResults;

  /** Summary statistics of the check results */
  private PreflightSummary summary;

  /** Presentation-level check results */
  private PresentationCheckResult presentationCheckResult;

  /** Timestamp when the check was performed */
  private Instant checkedAt;
}
