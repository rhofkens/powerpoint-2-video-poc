package ai.bluefields.ppt2video.entity;

/**
 * Enumeration of avatar video generation statuses. Tracks the lifecycle of an avatar video
 * generation request from submission to completion.
 */
public enum AvatarGenerationStatusType {
  /** Initial state when a generation request is submitted */
  PENDING,

  /** Video is being processed by the avatar provider */
  PROCESSING,

  /** Video generation completed successfully */
  COMPLETED,

  /** Video generation failed with an error */
  FAILED,

  /** Video generation was cancelled before completion */
  CANCELLED
}
