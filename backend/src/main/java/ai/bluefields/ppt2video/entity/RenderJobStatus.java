package ai.bluefields.ppt2video.entity;

public enum RenderJobStatus {
  QUEUED,
  PROCESSING,
  COMPLETED,
  FAILED,
  CANCELLED;

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == CANCELLED;
  }
}
