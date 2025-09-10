package ai.bluefields.ppt2video.service.video.provider.shotstack;

import ai.bluefields.ppt2video.config.ShotstackConfig;
import ai.bluefields.ppt2video.dto.video.*;
import ai.bluefields.ppt2video.entity.VideoProviderType;
import ai.bluefields.ppt2video.exception.VideoProviderException;
import ai.bluefields.ppt2video.service.video.provider.VideoProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Shotstack implementation of the VideoProvider interface. Handles all Shotstack-specific API
 * operations for video rendering.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShotstackVideoProvider implements VideoProvider {

  private final ShotstackConfig shotstackConfig;
  private final ObjectMapper objectMapper;
  private final RestClient.Builder restClientBuilder;

  @Value("${shotstack.api.mock:false}")
  private boolean mockMode;

  private RestClient restClient;
  private RestClient ingestClient;

  @Override
  public VideoProviderType getProviderType() {
    return VideoProviderType.SHOTSTACK;
  }

  @Override
  public String getProviderName() {
    return "Shotstack";
  }

  @Override
  public boolean supportsAssetHosting() {
    return true;
  }

  @Override
  public AssetUploadResult uploadAsset(AssetUploadRequest request) {
    log.info("Uploading asset to Shotstack Ingest API: {}", request.getFilename());

    if (mockMode) {
      return mockAssetUpload(request);
    }

    try {
      RestClient client = getIngestClient();

      Map<String, Object> uploadRequest = Map.of("url", request.getSourceUrl());

      Map<String, Object> response =
          client
              .post()
              .uri("/sources")
              .contentType(MediaType.APPLICATION_JSON)
              .body(uploadRequest)
              .retrieve()
              .body(Map.class);

      // Parse queued response: { data: { type: "source", id: "source-id" } }
      Map<String, Object> responseData = (Map<String, Object>) response.get("data");
      String sourceId = (String) responseData.get("id");

      log.info("Source queued with ID: {}", sourceId);

      // Return with queued status - actual URL will be available after polling
      // For now, return a placeholder URL that includes the source ID
      String placeholderUrl = String.format("shotstack://source/%s", sourceId);

      return AssetUploadResult.builder()
          .providerAssetId(sourceId)
          .providerUrl(placeholderUrl)
          .status("queued")
          .uploadedAt(LocalDateTime.now())
          .expiresAt(LocalDateTime.now().plusDays(7))
          .build();

    } catch (RestClientException e) {
      log.error("Failed to upload asset to Shotstack", e);
      throw new VideoProviderException("Asset upload failed: " + e.getMessage(), e);
    }
  }

  @Override
  public void deleteAsset(String assetId) {
    log.info("Deleting asset from Shotstack: {}", assetId);

    if (mockMode) {
      log.info("Mock mode: Asset {} deleted", assetId);
      return;
    }

    try {
      RestClient client = getRestClient();

      client.delete().uri("/sources/{id}", assetId).retrieve().toBodilessEntity();

      log.info("Asset deleted successfully: {}", assetId);

    } catch (RestClientException e) {
      log.error("Failed to delete asset from Shotstack", e);
      throw new VideoProviderException("Asset deletion failed: " + e.getMessage(), e);
    }
  }

  @Override
  public VideoComposition createComposition(CompositionRequest request) {
    // This will be handled by ShotstackCompositionService
    // The provider just validates and returns the composition
    log.info("Creating composition for presentation: {}", request.getPresentationId());

    VideoComposition composition =
        VideoComposition.builder()
            .id(request.getPresentationId().toString())
            .output(
                request.getOutputSettings() != null
                    ? request.getOutputSettings()
                    : buildDefaultOutputSettings())
            .metadata(
                Map.of(
                    "presentationId", request.getPresentationId(),
                    "createdAt", LocalDateTime.now().toString()))
            .build();

    return composition;
  }

  @Override
  public VideoComposition validateComposition(VideoComposition composition) {
    if (composition == null) {
      throw new VideoProviderException("Composition cannot be null");
    }

    if (composition.getTimeline() == null) {
      throw new VideoProviderException("Composition timeline cannot be null");
    }

    if (composition.getOutput() == null) {
      composition.setOutput(buildDefaultOutputSettings());
    }

    return composition;
  }

  @Override
  public RenderResponse submitRender(VideoComposition composition) {
    log.info("Submitting render to Shotstack for composition: {}", composition.getId());

    if (mockMode) {
      return mockSubmitRender(composition);
    }

    try {
      RestClient client = getRestClient();

      // Convert composition to Shotstack Edit format
      Map<String, Object> edit =
          Map.of(
              "timeline", composition.getTimeline(),
              "output", convertOutputSettings(composition.getOutput()));

      Map<String, Object> response =
          client
              .post()
              .uri("/render")
              .contentType(MediaType.APPLICATION_JSON)
              .body(edit)
              .retrieve()
              .body(Map.class);

      Map<String, Object> responseData = (Map<String, Object>) response.get("response");
      String renderId = (String) responseData.get("id");
      String status = (String) responseData.get("status");
      String message = (String) responseData.get("message");

      return RenderResponse.builder()
          .renderJobId(renderId)
          .status(status)
          .message(message)
          .submittedAt(LocalDateTime.now())
          .estimatedDuration("30-60 seconds")
          .build();

    } catch (RestClientException e) {
      log.error("Failed to submit render to Shotstack", e);
      throw new VideoProviderException("Render submission failed: " + e.getMessage(), e);
    }
  }

  @Override
  public RenderStatus checkRenderStatus(String renderId) {
    log.debug("Checking render status for: {}", renderId);

    if (mockMode) {
      return mockCheckStatus(renderId);
    }

    try {
      RestClient client = getRestClient();

      Map<String, Object> response =
          client.get().uri("/render/{id}", renderId).retrieve().body(Map.class);

      Map<String, Object> responseData = (Map<String, Object>) response.get("response");
      String status = (String) responseData.get("status");
      String url = (String) responseData.get("url");
      String error = (String) responseData.get("error");
      Map<String, Object> data = (Map<String, Object>) responseData.get("data");

      Integer progressPercent = 0;
      if (data != null && data.get("progressPercent") != null) {
        progressPercent = ((Number) data.get("progressPercent")).intValue();
      }

      return RenderStatus.builder()
          .renderJobId(renderId)
          .status(mapShotstackStatus(status))
          .progress(progressPercent)
          .outputUrl(url)
          .errorMessage(error)
          .build();

    } catch (RestClientException e) {
      log.error("Failed to check render status", e);
      throw new VideoProviderException("Status check failed: " + e.getMessage(), e);
    }
  }

  @Override
  public void cancelRender(String renderId) {
    log.info("Cancelling render: {}", renderId);

    if (mockMode) {
      log.info("Mock mode: Render {} cancelled", renderId);
      return;
    }

    // Shotstack doesn't support cancellation in their API
    log.warn("Shotstack does not support render cancellation");
  }

  private RestClient getRestClient() {
    if (restClient == null) {
      // Build Edit API URL - Edit API is at /edit/v1 or /edit/stage
      String apiUrl = "https://api.shotstack.io";
      if ("production".equalsIgnoreCase(shotstackConfig.getApi().getEnvironment())) {
        apiUrl = apiUrl + "/edit/v1";
      } else {
        apiUrl = apiUrl + "/edit/stage";
      }

      String apiKey = shotstackConfig.getApi().getActiveApiKey();
      if (apiKey == null || apiKey.isEmpty()) {
        throw new IllegalStateException(
            "No Shotstack API key configured for environment: "
                + shotstackConfig.getApi().getEnvironment());
      }

      restClient =
          restClientBuilder
              .baseUrl(apiUrl)
              .defaultHeader("x-api-key", apiKey)
              .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .build();

      log.info(
          "Shotstack Edit API client initialized for {} environment at {}",
          shotstackConfig.getApi().getEnvironment(),
          apiUrl);
    }
    return restClient;
  }

  private RestClient getIngestClient() {
    if (ingestClient == null) {
      // Build Ingest API URL - separate from Edit API
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

      log.info(
          "Shotstack Ingest API client initialized for {} environment at {}",
          shotstackConfig.getApi().getEnvironment(),
          apiUrl);
    }
    return ingestClient;
  }

  private OutputSettings buildDefaultOutputSettings() {
    return OutputSettings.builder()
        .format("mp4")
        .resolution("hd")
        .fps(25)
        .quality("medium")
        .build();
  }

  private Map<String, Object> convertOutputSettings(OutputSettings settings) {
    return Map.of(
        "format", settings.getFormat(),
        "resolution", settings.getResolution(),
        "fps", settings.getFps(),
        "quality", settings.getQuality());
  }

  private ai.bluefields.ppt2video.entity.RenderJobStatus mapShotstackStatus(String status) {
    return switch (status.toLowerCase()) {
      case "queued" -> ai.bluefields.ppt2video.entity.RenderJobStatus.QUEUED;
      case "fetching", "rendering", "saving" ->
          ai.bluefields.ppt2video.entity.RenderJobStatus.PROCESSING;
      case "done" -> ai.bluefields.ppt2video.entity.RenderJobStatus.COMPLETED;
      case "failed" -> ai.bluefields.ppt2video.entity.RenderJobStatus.FAILED;
      default -> ai.bluefields.ppt2video.entity.RenderJobStatus.QUEUED;
    };
  }

  // Mock methods for testing without Shotstack API
  private AssetUploadResult mockAssetUpload(AssetUploadRequest request) {
    log.info("MOCK: Uploading asset {}", request.getFilename());
    return AssetUploadResult.builder()
        .providerAssetId("mock-asset-" + System.currentTimeMillis())
        .providerUrl("https://mock.shotstack.io/assets/" + request.getFilename())
        .status("uploaded")
        .uploadedAt(LocalDateTime.now())
        .expiresAt(LocalDateTime.now().plusDays(7))
        .build();
  }

  private RenderResponse mockSubmitRender(VideoComposition composition) {
    log.info("MOCK: Submitting render for composition {}", composition.getId());
    return RenderResponse.builder()
        .renderJobId("mock-render-" + System.currentTimeMillis())
        .status("queued")
        .message("Mock render queued successfully")
        .submittedAt(LocalDateTime.now())
        .estimatedDuration("10 seconds (mock)")
        .build();
  }

  private RenderStatus mockCheckStatus(String renderId) {
    log.info("MOCK: Checking status for render {}", renderId);

    // Simulate progress based on time
    long elapsed = System.currentTimeMillis() % 10000;
    boolean completed = elapsed > 8000;

    return RenderStatus.builder()
        .renderJobId(renderId)
        .status(
            completed
                ? ai.bluefields.ppt2video.entity.RenderJobStatus.COMPLETED
                : ai.bluefields.ppt2video.entity.RenderJobStatus.PROCESSING)
        .progress(completed ? 100 : (int) (elapsed / 100))
        .outputUrl(completed ? "https://mock.shotstack.io/output/video.mp4" : null)
        .build();
  }
}
