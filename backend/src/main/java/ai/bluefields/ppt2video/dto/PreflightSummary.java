package ai.bluefields.ppt2video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary statistics for a preflight check. Provides a high-level overview of the presentation's
 * readiness.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreflightSummary {

  /** Total number of slides in the presentation */
  private int totalSlides;

  /** Number of slides that are fully ready for video generation */
  private int slidesReady;

  /** Number of slides missing narrative text */
  private int slidesMissingNarrative;

  /** Number of slides missing TTS audio */
  private int slidesMissingAudio;

  /** Number of slides missing avatar video */
  private int slidesMissingVideo;

  /** Number of slides missing rendered images */
  private int slidesMissingImages;

  /** Number of slides with assets not published to R2 */
  private int slidesWithUnpublishedAssets;

  /** Whether all mandatory checks have passed */
  private boolean allMandatoryChecksPassed;
}
