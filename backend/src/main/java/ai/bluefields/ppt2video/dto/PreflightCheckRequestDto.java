package ai.bluefields.ppt2video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for initiating a preflight check on a presentation. Contains optional configuration
 * for the check process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreflightCheckRequestDto {

  /**
   * Whether to check for enhanced narrative text in addition to the base narrative. Enhanced
   * narratives include emotional markers for TTS.
   */
  @Builder.Default private boolean checkEnhancedNarrative = false;

  /**
   * Force a fresh check even if a recent cached result exists. Useful when assets may have been
   * updated externally.
   */
  @Builder.Default private boolean forceRefresh = false;

  /**
   * Whether to check for intro video generation status at the presentation level. Intro videos are
   * optional presentation-level assets.
   */
  @Builder.Default private boolean checkIntroVideo = true;
}
