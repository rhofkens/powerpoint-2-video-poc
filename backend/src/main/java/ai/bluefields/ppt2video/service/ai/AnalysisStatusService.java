package ai.bluefields.ppt2video.service.ai;

import ai.bluefields.ppt2video.dto.AnalysisStatusDto;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisState;
import ai.bluefields.ppt2video.dto.AnalysisStatusDto.AnalysisType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Service for tracking the status of long-running AI analysis operations. */
@Slf4j
@Service
public class AnalysisStatusService {

  // In-memory storage for analysis status
  private final Map<String, AnalysisStatusDto> statusMap = new ConcurrentHashMap<>();

  /** Create a unique key for status tracking. */
  private String createKey(UUID presentationId, AnalysisType type) {
    return presentationId.toString() + "_" + type.name();
  }

  /** Start tracking a new analysis operation. */
  public AnalysisStatusDto startAnalysis(UUID presentationId, AnalysisType type, int totalItems) {
    String key = createKey(presentationId, type);

    AnalysisStatusDto status =
        new AnalysisStatusDto(
            presentationId,
            type,
            AnalysisState.IN_PROGRESS,
            totalItems,
            0,
            0,
            LocalDateTime.now(),
            null,
            new ArrayList<>(),
            "Analysis started");

    statusMap.put(key, status);
    log.info("=== ANALYSIS TRACKING STARTED ===");
    log.info("Type: {}", type);
    log.info("Presentation: {}", presentationId);
    log.info("Total items: {}", totalItems);
    log.info("Status key: {}", key);

    return status;
  }

  /** Update progress for an ongoing analysis. */
  public AnalysisStatusDto updateProgress(
      UUID presentationId, AnalysisType type, int completedItems, int failedItems, String message) {
    String key = createKey(presentationId, type);
    AnalysisStatusDto current = statusMap.get(key);

    if (current == null) {
      log.warn("No analysis found for {} - {}", presentationId, type);
      return null;
    }

    AnalysisStatusDto updated =
        new AnalysisStatusDto(
            current.presentationId(),
            current.analysisType(),
            current.state(),
            current.totalItems(),
            completedItems,
            failedItems,
            current.startTime(),
            current.endTime(),
            current.errors(),
            message != null ? message : current.message());

    statusMap.put(key, updated);
    log.info(
        "Progress Update - {}: {}/{} completed, {} failed - {}",
        type,
        completedItems,
        current.totalItems(),
        failedItems,
        message != null ? message : "");

    return updated;
  }

  /** Add an error to the analysis status. */
  public void addError(UUID presentationId, AnalysisType type, String error) {
    String key = createKey(presentationId, type);
    AnalysisStatusDto current = statusMap.get(key);

    if (current == null) {
      log.warn("No analysis found for {} - {}", presentationId, type);
      return;
    }

    List<String> errors = new ArrayList<>(current.errors());
    errors.add(error);

    AnalysisStatusDto updated =
        new AnalysisStatusDto(
            current.presentationId(),
            current.analysisType(),
            current.state(),
            current.totalItems(),
            current.completedItems(),
            current.failedItems() + 1,
            current.startTime(),
            current.endTime(),
            errors,
            current.message());

    statusMap.put(key, updated);
    log.warn("Added error to {} analysis: {}", type, error);
  }

  /** Complete an analysis operation. */
  public AnalysisStatusDto completeAnalysis(
      UUID presentationId, AnalysisType type, AnalysisState finalState, String message) {
    String key = createKey(presentationId, type);
    AnalysisStatusDto current = statusMap.get(key);

    if (current == null) {
      log.warn("No analysis found for {} - {}", presentationId, type);
      return null;
    }

    AnalysisStatusDto completed =
        new AnalysisStatusDto(
            current.presentationId(),
            current.analysisType(),
            finalState,
            current.totalItems(),
            current.completedItems(),
            current.failedItems(),
            current.startTime(),
            LocalDateTime.now(),
            current.errors(),
            message);

    statusMap.put(key, completed);
    log.info("Completed {} for presentation {} with state: {}", type, presentationId, finalState);

    return completed;
  }

  /** Get the current status of an analysis. */
  public AnalysisStatusDto getStatus(UUID presentationId, AnalysisType type) {
    String key = createKey(presentationId, type);
    return statusMap.get(key);
  }

  /** Get all active analyses for a presentation. */
  public List<AnalysisStatusDto> getActiveAnalyses(UUID presentationId) {
    return statusMap.values().stream()
        .filter(status -> status.presentationId().equals(presentationId) && status.isRunning())
        .toList();
  }

  /** Clean up old completed analyses (older than 1 hour). */
  public void cleanupOldStatuses() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

    statusMap
        .entrySet()
        .removeIf(
            entry -> {
              AnalysisStatusDto status = entry.getValue();
              return !status.isRunning()
                  && status.endTime() != null
                  && status.endTime().isBefore(cutoff);
            });

    log.debug("Cleaned up old analysis statuses");
  }
}
