package ai.bluefields.ppt2video.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed check results for an individual slide. Contains status for each required component and
 * any issues found.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlideCheckResult {

  /** Unique identifier of the slide */
  private UUID slideId;

  /** Sequential number of the slide in the presentation */
  private int slideNumber;

  /** Title or heading of the slide */
  private String slideTitle;

  /** Status of the base narrative text */
  private CheckStatus narrativeStatus;

  /** Status of the enhanced narrative text (with emotional markers) */
  private CheckStatus enhancedNarrativeStatus;

  /** Status of the TTS audio generation */
  private CheckStatus audioStatus;

  /** Status of the avatar video generation */
  private CheckStatus avatarVideoStatus;

  /** List of specific issues found during validation */
  @Builder.Default private List<String> issues = new ArrayList<>();

  /** Additional metadata about the check (file paths, URLs, etc.) */
  @Builder.Default private Map<String, Object> metadata = new HashMap<>();
}
