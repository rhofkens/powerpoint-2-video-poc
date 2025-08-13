package ai.bluefields.ppt2video.service.avatar.providers;

import ai.bluefields.ppt2video.exception.ProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * HTTP client for interacting with HeyGen API. Handles API requests, authentication, and response
 * parsing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeyGenClient {

  private final HeyGenConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  /**
   * Create a new video generation request.
   *
   * @param request the video creation request
   * @return the API response
   */
  @Retryable(
      retryFor = {IOException.class, InterruptedException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 2000))
  public HeyGenVideoResponse createVideo(HeyGenVideoRequest request) {
    try {
      String url = configuration.getApiUrl("video/generate");
      String requestBody = objectMapper.writeValueAsString(request);

      log.info("Creating HeyGen video with URL: {}", url);
      log.debug("HeyGen request body: {}", requestBody);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("X-Api-Key", configuration.getApi().getKey())
              .header("Content-Type", "application/json")
              .timeout(
                  Duration.ofMillis(
                      configuration.getApi().getConnectTimeout()
                          + configuration.getApi().getReadTimeout()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.info("HeyGen API response status: {}, body: {}", response.statusCode(), response.body());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        HeyGenVideoResponse parsedResponse =
            objectMapper.readValue(response.body(), HeyGenVideoResponse.class);
        log.info(
            "Parsed HeyGen response - Success: {}, Code: {}, Message: {}, VideoId: {}",
            parsedResponse.isSuccess(),
            parsedResponse.getCode(),
            parsedResponse.getMessage(),
            parsedResponse.getVideoId());
        return parsedResponse;
      } else {
        // Try to parse error response
        try {
          HeyGenVideoResponse errorResponse =
              objectMapper.readValue(response.body(), HeyGenVideoResponse.class);
          String errorMessage =
              errorResponse.getError() != null
                  ? errorResponse.getError().getMessage()
                  : errorResponse.getMessage();
          throw new ProcessingException(
              "HeyGen API error: " + errorMessage + " (status: " + response.statusCode() + ")");
        } catch (Exception e) {
          throw new ProcessingException(
              "HeyGen API error: HTTP " + response.statusCode() + " - " + response.body());
        }
      }
    } catch (IOException | InterruptedException e) {
      log.error("Failed to create HeyGen video", e);
      throw new ProcessingException("Failed to create HeyGen video: " + e.getMessage());
    }
  }

  /**
   * Get the status of a video generation job.
   *
   * @param videoId the HeyGen video ID
   * @return the video status response
   */
  @Retryable(
      retryFor = {IOException.class, InterruptedException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000))
  public HeyGenVideoResponse getVideoStatus(String videoId) {
    try {
      String url = configuration.getApiUrl("video_status.get");
      // Build request with video_id as query parameter
      String fullUrl = url + "?video_id=" + videoId;

      log.debug("Checking HeyGen video status for: {}", videoId);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(fullUrl))
              .header("X-Api-Key", configuration.getApi().getKey())
              .timeout(
                  Duration.ofMillis(
                      configuration.getApi().getConnectTimeout()
                          + configuration.getApi().getReadTimeout()))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      log.debug(
          "HeyGen status response: status={}, body={}", response.statusCode(), response.body());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return objectMapper.readValue(response.body(), HeyGenVideoResponse.class);
      } else {
        log.warn("HeyGen status check failed: HTTP {}", response.statusCode());
        throw new ProcessingException("Failed to get video status: HTTP " + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      log.error("Failed to get HeyGen video status for: {}", videoId, e);
      throw new ProcessingException("Failed to get video status: " + e.getMessage());
    }
  }

  /**
   * Cancel a video generation job.
   *
   * @param videoId the HeyGen video ID
   * @return true if cancellation was successful
   */
  public boolean cancelVideo(String videoId) {
    try {
      String url = configuration.getApiUrl("video.delete");
      String requestBody = objectMapper.writeValueAsString(new CancelRequest(videoId));

      log.info("Cancelling HeyGen video: {}", videoId);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .header("X-Api-Key", configuration.getApi().getKey())
              .header("Content-Type", "application/json")
              .timeout(Duration.ofMillis(configuration.getApi().getConnectTimeout()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
      if (success) {
        log.info("Successfully cancelled HeyGen video: {}", videoId);
      } else {
        log.warn("Failed to cancel HeyGen video: {} - HTTP {}", videoId, response.statusCode());
      }
      return success;
    } catch (Exception e) {
      log.error("Error cancelling HeyGen video: {}", videoId, e);
      return false;
    }
  }

  /** Internal class for cancel request. */
  private record CancelRequest(String video_id) {}
}
