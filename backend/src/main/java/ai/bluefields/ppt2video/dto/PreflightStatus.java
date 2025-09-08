package ai.bluefields.ppt2video.dto;

/** Represents the overall status of a presentation's preflight check. */
public enum PreflightStatus {
  /** All required components are present and published - ready for video generation */
  READY,

  /** One or more required components are missing */
  INCOMPLETE,

  /** All components present but some have warnings (e.g., unpublished assets) */
  HAS_WARNINGS,

  /** Preflight check is currently in progress */
  CHECKING,

  /** An error occurred during the preflight check */
  ERROR
}
