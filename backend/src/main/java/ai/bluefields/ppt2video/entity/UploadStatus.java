package ai.bluefields.ppt2video.entity;

/** Enumeration of upload status states for assets. Tracks the lifecycle of asset uploads to R2. */
public enum UploadStatus {
  /** Initial state when upload is requested but not started */
  PENDING,

  /** Upload is currently in progress */
  UPLOADING,

  /** Upload completed successfully */
  COMPLETED,

  /** Upload failed due to an error */
  FAILED
}
