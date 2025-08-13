package ai.bluefields.ppt2video.entity;

/**
 * Enumeration of avatar video provider types. Defines the different services that can be used for
 * generating avatar videos with synthesized presenters.
 */
public enum AvatarProviderType {
  /** HeyGen avatar video generation service */
  HEYGEN,

  /** Synthesia avatar video generation service (future implementation) */
  SYNTHESIA,

  /** D-ID avatar video generation service (future implementation) */
  D_ID
}
