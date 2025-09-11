package ai.bluefields.ppt2video.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTO for tracking the status of long-running analysis operations. */
public record AnalysisStatusDto(
    UUID presentationId,
    AnalysisType analysisType,
    AnalysisState state,
    int totalItems,
    int completedItems,
    int failedItems,
    LocalDateTime startTime,
    LocalDateTime endTime,
    List<String> errors,
    String message) {

  /** Type of analysis being performed. */
  public enum AnalysisType {
    DECK_ANALYSIS,
    ALL_SLIDES_ANALYSIS,
    ALL_NARRATIVES_GENERATION,
    ALL_AVATAR_VIDEOS_GENERATION
  }

  /** Current state of the analysis. */
  public enum AnalysisState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  /** Calculate progress percentage. */
  public int getProgressPercentage() {
    if (totalItems == 0) return 0;
    return (int) ((completedItems + failedItems) * 100.0 / totalItems);
  }

  /** Check if the analysis is still running. */
  public boolean isRunning() {
    return state == AnalysisState.IN_PROGRESS || state == AnalysisState.PENDING;
  }
}
