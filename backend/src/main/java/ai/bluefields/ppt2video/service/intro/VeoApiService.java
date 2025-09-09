package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.veo.VeoGenerationResponse;
import ai.bluefields.ppt2video.dto.veo.VeoPrompt;
import ai.bluefields.ppt2video.dto.veo.VeoVideoStatus;
import ai.bluefields.ppt2video.dto.veo.api.VeoApiGenerationResponse;
import ai.bluefields.ppt2video.dto.veo.api.VeoApiStatusResponse;
import ai.bluefields.ppt2video.exception.ProcessingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Service for interacting with Google Veo API for AI video generation. Handles video generation
 * requests and status polling using Spring Boot's RestClient.
 */
@Service
@Slf4j
public class VeoApiService {

  private final RestClient restClient;

  @Value("${google.gemini.api.key:}")
  private String apiKey;

  @Value("${google.veo.model:veo-3.0-fast-generate-001}")
  private String veoModel;

  public VeoApiService(
      RestClient.Builder restClientBuilder,
      @Value("${google.veo.api.url:https://generativelanguage.googleapis.com/v1beta}")
          String veoApiBaseUrl) {
    log.info("Initializing VeoApiService with base URL: {}", veoApiBaseUrl);
    this.restClient = restClientBuilder.baseUrl(veoApiBaseUrl).build();
  }

  /**
   * Generate a video using Google Veo API.
   *
   * @param prompt the video generation prompt
   * @return generation response with ID for tracking
   */
  public VeoGenerationResponse generateVideo(VeoPrompt prompt) {
    try {
      log.info("Initiating video generation with Veo API");
      log.info("Using model: {}", veoModel);
      log.info("API Key present: {}", apiKey != null && !apiKey.isEmpty());

      // Build endpoint path
      String endpoint = String.format("/models/%s:predictLongRunning", veoModel);
      log.info("Endpoint: {}", endpoint);

      // Prepare request body following Google's format
      Map<String, Object> requestBody = buildGenerationRequest(prompt);
      log.debug("Request body: {}", requestBody);

      VeoApiGenerationResponse response =
          restClient
              .post()
              .uri(endpoint)
              .headers(
                  headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (apiKey != null && !apiKey.isEmpty()) {
                      headers.set("x-goog-api-key", apiKey);
                      log.info("Setting API key header");
                    } else {
                      log.warn("No API key configured for Veo API");
                    }
                  })
              .body(requestBody)
              .retrieve()
              .body(VeoApiGenerationResponse.class);

      log.info(
          "Veo API generation response: name={}, status={}, message={}",
          response.getName(),
          response.getStatus(),
          response.getMessage());
      return mapToGenerationResponse(response);

    } catch (RestClientException e) {
      log.error("Failed to call Veo API for video generation", e);
      throw new ProcessingException("Video generation failed: " + e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error during video generation", e);
      throw new ProcessingException("Unexpected error: " + e.getMessage());
    }
  }

  /**
   * Check the status of a video generation job.
   *
   * @param generationId the generation job ID
   * @return current status of the video generation
   */
  public VeoVideoStatus getVideoStatus(String generationId) {
    try {
      log.debug("Checking status for generation ID: {}", generationId);

      // The generation ID might be a full path like
      // "models/veo-3.0-fast-generate-001/operations/xyz"
      // We need to use this full path as the endpoint
      String endpoint;
      if (generationId.startsWith("models/")) {
        // Use the full path returned by the API
        endpoint = "/" + generationId;
      } else {
        // Fallback to the old format
        endpoint = String.format("/operations/%s", generationId);
      }
      log.info("Status check endpoint: {}", endpoint);

      VeoApiStatusResponse response =
          restClient
              .get()
              .uri(endpoint)
              .headers(
                  headers -> {
                    if (apiKey != null && !apiKey.isEmpty()) {
                      headers.set("x-goog-api-key", apiKey);
                    }
                  })
              .retrieve()
              .body(VeoApiStatusResponse.class);

      log.info("Veo API status response: name={}, done={}", response.getName(), response.getDone());
      return mapToVideoStatus(response);

    } catch (RestClientException e) {
      log.error("Failed to check video status for ID: {}", generationId, e);
      throw new ProcessingException("Status check failed: " + e.getMessage());
    }
  }

  /**
   * Build the request body for video generation following Google's format. Format: { "instances": [
   * {"prompt": "..."} ], "parameters": { "fps": 24, "aspectRatio": "16:9", "negativePrompt": "...",
   * "motionLevel": "high" } }
   */
  private Map<String, Object> buildGenerationRequest(VeoPrompt prompt) {
    Map<String, Object> request = new HashMap<>();

    // Build instances array with prompt
    Map<String, Object> instance = new HashMap<>();
    instance.put("prompt", prompt.getPromptText());
    request.put("instances", List.of(instance));

    // Build parameters
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("aspectRatio", prompt.getAspectRatio());

    if (prompt.getNegativePrompt() != null && !prompt.getNegativePrompt().isEmpty()) {
      parameters.put("negativePrompt", prompt.getNegativePrompt());
    }

    // Add resolution if specified
    if (prompt.getResolution() != null) {
      parameters.put("resolution", prompt.getResolution());
    }

    request.put("parameters", parameters);

    return request;
  }

  /** Map API generation response to internal DTO. */
  private VeoGenerationResponse mapToGenerationResponse(VeoApiGenerationResponse apiResponse) {
    if (apiResponse == null) {
      throw new ProcessingException("Empty response from Veo API");
    }

    return VeoGenerationResponse.builder()
        .generationId(apiResponse.getName())
        .status(apiResponse.getStatus())
        .message(apiResponse.getMessage())
        .estimatedTimeSeconds(apiResponse.getEstimatedTimeSeconds())
        .build();
  }

  /** Map API status response to internal DTO. */
  private VeoVideoStatus mapToVideoStatus(VeoApiStatusResponse apiResponse) {
    if (apiResponse == null) {
      throw new ProcessingException("Empty status response from Veo API");
    }

    // Determine state based on 'done' field and presence of error
    VeoVideoStatus.VeoState state;
    if (Boolean.TRUE.equals(apiResponse.getDone())) {
      if (apiResponse.getError() != null) {
        state = VeoVideoStatus.VeoState.FAILED;
      } else {
        state = VeoVideoStatus.VeoState.COMPLETED;
      }
    } else {
      state = VeoVideoStatus.VeoState.PROCESSING;
    }

    VeoVideoStatus.VeoVideoStatusBuilder builder =
        VeoVideoStatus.builder().generationId(apiResponse.getName()).state(state);

    // Map metadata if present
    if (apiResponse.getMetadata() != null) {
      builder.progress(apiResponse.getMetadata().getProgressPercent());
    }

    // Map video URL if present
    if (apiResponse.getResponse() != null
        && apiResponse.getResponse().getGenerateVideoResponse() != null
        && apiResponse.getResponse().getGenerateVideoResponse().getGeneratedSamples() != null
        && !apiResponse.getResponse().getGenerateVideoResponse().getGeneratedSamples().isEmpty()) {

      VeoApiStatusResponse.GeneratedSample firstSample =
          apiResponse.getResponse().getGenerateVideoResponse().getGeneratedSamples().get(0);

      if (firstSample.getVideo() != null) {
        builder.videoUrl(firstSample.getVideo().getUri());
      }
    }

    // Map error if present
    if (apiResponse.getError() != null) {
      builder.errorMessage(apiResponse.getError().getMessage());
    }

    return builder.build();
  }
}
