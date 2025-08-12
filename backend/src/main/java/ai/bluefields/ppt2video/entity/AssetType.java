package ai.bluefields.ppt2video.entity;

/**
 * Enumeration of asset types that can be stored in R2 buckets. Defines the different categories of
 * media assets for presentations.
 */
public enum AssetType {
  /** Rendered image of a presentation slide */
  SLIDE_IMAGE,

  /** Audio narration for a slide */
  SLIDE_AUDIO,

  /** Avatar video for a slide (future implementation) */
  SLIDE_AVATAR_VIDEO,

  /** Introduction video for a presentation (future implementation) */
  PRESENTATION_INTRO_VIDEO,

  /** Complete rendered video of the entire presentation (future implementation) */
  PRESENTATION_FULL_VIDEO
}
