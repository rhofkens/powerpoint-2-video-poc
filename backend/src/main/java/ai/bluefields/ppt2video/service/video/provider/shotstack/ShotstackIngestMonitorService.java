package ai.bluefields.ppt2video.service.video.provider.shotstack;

import ai.bluefields.ppt2video.config.ShotstackConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service for monitoring Shotstack Ingest API source status. Polls the Ingest API to track upload
 * progress and handles completion/failure scenarios using TaskScheduler pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShotstackIngestMonitorService {

  private final TaskScheduler taskScheduler;
  private final RestClient.Builder restClientBuilder;
  private final ShotstackConfig shotstackConfig;

  private final Map<String, MonitoringTask> monitoringTasks = new ConcurrentHashMap<>();
  private RestClient ingestClient;

  @Value("${shotstack.ingest.monitor.initial-delay:2000}")
  private long initialDelayMs;

  @Value("${shotstack.ingest.monitor.poll-interval:5000}")
  private long pollIntervalMs;

  @Value("${shotstack.ingest.monitor.max-duration:300000}")
  private long maxDurationMs;

  /**
   * Start monitoring a source upload.
   *
   * @param sourceId The Shotstack source ID
   * @param callback Callback for completion/failure (status, result/error)
   * @return CompletableFuture that completes when monitoring ends
   */
  @Async
  public CompletableFuture<String> startMonitoring(
      String sourceId, BiConsumer<SourceStatus, String> callback) {
    log.info("Starting monitoring for Shotstack source: {}", sourceId);

    CompletableFuture<String> future = new CompletableFuture<>();

    // Create monitoring task
    MonitoringTask task = new MonitoringTask(sourceId, callback, future);

    // Schedule polling task
    ScheduledFuture<?> scheduledFuture =
        taskScheduler.scheduleWithFixedDelay(
            () -> checkSourceStatus(task),
            Instant.now().plusMillis(initialDelayMs),
            Duration.ofMillis(pollIntervalMs));

    task.setScheduledFuture(scheduledFuture);
    monitoringTasks.put(sourceId, task);

    // Schedule timeout task
    taskScheduler.schedule(() -> handleTimeout(sourceId), Instant.now().plusMillis(maxDurationMs));

    return future;
  }

  /**
   * Stop monitoring a source.
   *
   * @param sourceId The source ID
   */
  public void stopMonitoring(String sourceId) {
    MonitoringTask task = monitoringTasks.remove(sourceId);
    if (task != null && task.getScheduledFuture() != null) {
      task.getScheduledFuture().cancel(false);
      log.info("Stopped monitoring for source: {}", sourceId);
    }
  }

  /** Check the status of a source from Shotstack Ingest API. */
  private void checkSourceStatus(MonitoringTask task) {
    try {
      RestClient client = getIngestClient();

      // Call GET /sources/{id}
      Map<String, Object> response =
          client.get().uri("/sources/{id}", task.getSourceId()).retrieve().body(Map.class);

      if (response == null) {
        log.warn("Null response for source: {}", task.getSourceId());
        return;
      }

      // Parse response structure: { data: { id, attributes: { status, src, ... } } }
      Map<String, Object> data = (Map<String, Object>) response.get("data");
      if (data == null) {
        log.warn("No data in response for source: {}", task.getSourceId());
        return;
      }

      Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
      if (attributes == null) {
        log.warn("No attributes in response for source: {}", task.getSourceId());
        return;
      }

      String status = (String) attributes.get("status");
      log.debug("Source {} status: {}", task.getSourceId(), status);

      // Handle status
      switch (status != null ? status.toLowerCase() : "") {
        case "ready":
        case "completed":
          handleCompletion(task, attributes);
          break;
        case "failed":
        case "error":
          handleFailure(task, attributes);
          break;
        case "processing":
        case "fetching":
        case "queued":
        case "importing":
          // Still processing, continue monitoring
          log.debug("Source {} still processing (status: {})", task.getSourceId(), status);
          break;
        default:
          log.warn("Unknown status '{}' for source: {}", status, task.getSourceId());
      }

    } catch (Exception e) {
      log.error("Error checking source status for {}: {}", task.getSourceId(), e.getMessage());
      // Don't stop monitoring on transient errors
    }
  }

  /** Handle successful source ingestion. */
  private void handleCompletion(MonitoringTask task, Map<String, Object> attributes) {
    log.info("Source ingestion completed: {}", task.getSourceId());

    // Get the asset URL from attributes - according to docs it's in "source" field
    String assetUrl = (String) attributes.get("source");
    if (assetUrl == null) {
      // Try alternative field names just in case
      assetUrl = (String) attributes.get("src");
      if (assetUrl == null) {
        assetUrl = (String) attributes.get("url");
      }
    }

    if (assetUrl == null) {
      log.error(
          "No asset URL in completed source response. Available attributes: {}",
          attributes.keySet());
      handleFailure(task, "No asset URL in response");
      return;
    }

    log.info("Source ingestion completed with URL: {}", assetUrl);

    // Notify callback
    if (task.getCallback() != null) {
      task.getCallback().accept(SourceStatus.COMPLETED, assetUrl);
    }

    // Complete future
    task.getFuture().complete(assetUrl);

    // Stop monitoring
    stopMonitoring(task.getSourceId());
  }

  /** Handle source ingestion failure. */
  private void handleFailure(MonitoringTask task, Map<String, Object> attributes) {
    String error = (String) attributes.get("error");
    if (error == null) {
      error = (String) attributes.get("message");
    }
    if (error == null) {
      error = "Unknown error";
    }

    handleFailure(task, error);
  }

  private void handleFailure(MonitoringTask task, String error) {
    log.error("Source ingestion failed for {}: {}", task.getSourceId(), error);

    // Notify callback
    if (task.getCallback() != null) {
      task.getCallback().accept(SourceStatus.FAILED, error);
    }

    // Complete future exceptionally
    task.getFuture().completeExceptionally(new RuntimeException("Ingest failed: " + error));

    // Stop monitoring
    stopMonitoring(task.getSourceId());
  }

  /** Handle monitoring timeout. */
  private void handleTimeout(String sourceId) {
    MonitoringTask task = monitoringTasks.get(sourceId);
    if (task != null) {
      log.warn("Monitoring timeout for source: {}", sourceId);
      handleFailure(task, "Monitoring timeout after " + (maxDurationMs / 1000) + " seconds");
    }
  }

  /** Get or create the Ingest API client. */
  private RestClient getIngestClient() {
    if (ingestClient == null) {
      String apiUrl = "https://api.shotstack.io";
      if ("production".equalsIgnoreCase(shotstackConfig.getApi().getEnvironment())) {
        apiUrl = apiUrl + "/ingest/v1";
      } else {
        apiUrl = apiUrl + "/ingest/stage";
      }

      String apiKey = shotstackConfig.getApi().getActiveApiKey();
      if (apiKey == null || apiKey.isEmpty()) {
        throw new IllegalStateException(
            "No Shotstack API key configured for environment: "
                + shotstackConfig.getApi().getEnvironment());
      }

      ingestClient =
          restClientBuilder
              .baseUrl(apiUrl)
              .defaultHeader("x-api-key", apiKey)
              .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .build();

      log.debug("Shotstack Ingest monitor client initialized at {}", apiUrl);
    }
    return ingestClient;
  }

  /** Source status enum. */
  public enum SourceStatus {
    COMPLETED,
    FAILED,
    TIMEOUT
  }

  /** Internal class to track monitoring tasks. */
  private static class MonitoringTask {
    private final String sourceId;
    private final BiConsumer<SourceStatus, String> callback;
    private final CompletableFuture<String> future;
    private ScheduledFuture<?> scheduledFuture;

    public MonitoringTask(
        String sourceId,
        BiConsumer<SourceStatus, String> callback,
        CompletableFuture<String> future) {
      this.sourceId = sourceId;
      this.callback = callback;
      this.future = future;
    }

    public String getSourceId() {
      return sourceId;
    }

    public BiConsumer<SourceStatus, String> getCallback() {
      return callback;
    }

    public CompletableFuture<String> getFuture() {
      return future;
    }

    public ScheduledFuture<?> getScheduledFuture() {
      return scheduledFuture;
    }

    public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
      this.scheduledFuture = scheduledFuture;
    }
  }
}
