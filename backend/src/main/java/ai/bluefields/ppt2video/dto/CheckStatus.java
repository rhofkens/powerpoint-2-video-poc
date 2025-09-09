package ai.bluefields.ppt2video.dto;

/** Represents the status of an individual check within a slide validation. */
public enum CheckStatus {
  /** The check passed successfully */
  PASSED,

  /** The check failed - required component is missing or invalid */
  FAILED,

  /** The check passed but with warnings (e.g., unpublished assets) */
  WARNING,

  /** The check is not applicable for this slide */
  NOT_APPLICABLE,

  /** The check is currently in progress */
  CHECKING,

  /** The resource is currently being generated/processed */
  IN_PROGRESS,

  /** The required resource was not found */
  NOT_FOUND
}
