package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.veo.VeoGenerationResponse;
import ai.bluefields.ppt2video.dto.veo.VeoPrompt;
import ai.bluefields.ppt2video.dto.veo.VeoVideoStatus;
import ai.bluefields.ppt2video.dto.veo.api.VeoApiGenerationResponse;
import ai.bluefields.ppt2video.dto.veo.api.VeoApiStatusResponse;
import ai.bluefields.ppt2video.exception.ProcessingException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

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

  @Value("${veo.video.audio.fade-out-duration:1.5}")
  private double fadeOutDuration;

  @Value("${veo.video.audio.fade-out-enabled:true}")
  private boolean fadeOutEnabled;

  @Value("${veo.video.ffmpeg.path:/usr/local/bin/ffmpeg}")
  private String ffmpegPath;

  @Value("${veo.video.ffprobe.path:/usr/local/bin/ffprobe}")
  private String ffprobePath;

  @Value("${veo.video.ffmpeg.timeout-seconds:60}")
  private int ffmpegTimeoutSeconds;

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

  /** Check if FFmpeg and FFprobe are available on system startup. */
  @PostConstruct
  public void checkFfmpegAvailability() {
    if (!fadeOutEnabled) {
      log.info("Audio fade-out is disabled in configuration");
      return;
    }

    try {
      // Check ffmpeg
      ProcessResult ffmpegResult =
          new ProcessExecutor()
              .command(ffmpegPath, "-version")
              .readOutput(true)
              .timeout(5, TimeUnit.SECONDS)
              .execute();

      log.info("FFmpeg available at: {}", ffmpegPath);
      log.debug("FFmpeg version: {}", ffmpegResult.outputUTF8().split("\n")[0]);

      // Check ffprobe
      ProcessResult ffprobeResult =
          new ProcessExecutor()
              .command(ffprobePath, "-version")
              .readOutput(true)
              .timeout(5, TimeUnit.SECONDS)
              .execute();

      log.info("FFprobe available at: {}", ffprobePath);
      log.debug("FFprobe version: {}", ffprobeResult.outputUTF8().split("\n")[0]);

    } catch (Exception e) {
      log.error("FFmpeg tools not available, disabling audio fade-out feature", e);
      fadeOutEnabled = false;
    }
  }

  /**
   * Get the duration of a video file using ffprobe.
   *
   * @param videoPath path to the video file
   * @return duration in seconds
   * @throws IOException if ffprobe execution fails
   */
  private double getVideoDuration(Path videoPath) throws IOException {
    try {
      ProcessResult result =
          new ProcessExecutor()
              .command(
                  ffprobePath,
                  "-v",
                  "error",
                  "-show_entries",
                  "format=duration",
                  "-of",
                  "default=nk=1:nw=1",
                  videoPath.toString())
              .readOutput(true)
              .timeout(10, TimeUnit.SECONDS)
              .exitValues(0)
              .execute();

      String output = result.outputUTF8().trim();
      double duration = Double.parseDouble(output);
      log.debug("Video duration for {}: {} seconds", videoPath.getFileName(), duration);
      return duration;

    } catch (InvalidExitValueException e) {
      throw new IOException("FFprobe failed with exit code: " + e.getExitValue(), e);
    } catch (TimeoutException e) {
      throw new IOException("FFprobe timed out", e);
    } catch (Exception e) {
      throw new IOException("Failed to get video duration", e);
    }
  }

  /**
   * Apply audio fade-out to a video file if enabled in configuration.
   *
   * @param videoPath path to the video file
   * @return path to the processed video (same as input if fade-out applied)
   */
  public Path applyAudioFadeOutIfEnabled(Path videoPath) {
    if (!fadeOutEnabled) {
      log.debug("Audio fade-out is disabled, skipping processing");
      return videoPath;
    }

    try {
      return applyAudioFadeOut(videoPath, fadeOutDuration);
    } catch (IOException e) {
      log.error("Failed to apply audio fade-out, using original video", e);
      return videoPath;
    }
  }

  /**
   * Apply audio fade-out to a video file using ffmpeg.
   *
   * @param inputPath path to the input video
   * @param fadeOutDuration duration of fade-out in seconds
   * @return path to the processed video (same as input, replaced in-place)
   * @throws IOException if ffmpeg execution fails
   */
  private Path applyAudioFadeOut(Path inputPath, double fadeOutDuration) throws IOException {
    // Create temp output file
    Path outputPath = inputPath.getParent().resolve(inputPath.getFileName() + ".fadeout.mp4");

    try {
      // Get video duration
      double duration = getVideoDuration(inputPath);

      // Calculate fade start time
      double fadeStart = Math.max(0, duration - fadeOutDuration);

      log.info(
          "Applying {}-second audio fade-out to video, starting at {} seconds",
          fadeOutDuration,
          fadeStart);

      // Execute ffmpeg with zt-exec
      ProcessResult result =
          new ProcessExecutor()
              .command(
                  ffmpegPath,
                  "-i",
                  inputPath.toString(),
                  "-c:v",
                  "copy", // Copy video stream without re-encoding
                  "-af",
                  String.format("afade=t=out:st=%.2f:d=%.2f", fadeStart, fadeOutDuration),
                  "-y", // Overwrite output file
                  outputPath.toString())
              .timeout(ffmpegTimeoutSeconds, TimeUnit.SECONDS)
              .redirectError(
                  new LogOutputStream() {
                    @Override
                    protected void processLine(String line) {
                      log.debug("FFmpeg: {}", line);
                    }
                  })
              .exitValues(0)
              .execute();

      // Verify output file exists and has reasonable size
      if (!Files.exists(outputPath)) {
        throw new IOException("FFmpeg did not create output file");
      }

      long outputSize = Files.size(outputPath);
      long inputSize = Files.size(inputPath);
      if (outputSize < inputSize * 0.5) {
        throw new IOException("Output file seems corrupted (too small)");
      }

      // Replace input with output
      Files.move(outputPath, inputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      log.info("Successfully applied audio fade-out to video");

      return inputPath;

    } catch (InvalidExitValueException e) {
      Files.deleteIfExists(outputPath);
      throw new IOException("FFmpeg failed with exit code: " + e.getExitValue(), e);
    } catch (TimeoutException e) {
      Files.deleteIfExists(outputPath);
      throw new IOException("FFmpeg timed out after " + ffmpegTimeoutSeconds + " seconds", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Files.deleteIfExists(outputPath);
      throw new IOException("FFmpeg execution interrupted", e);
    } catch (Exception e) {
      Files.deleteIfExists(outputPath);
      throw new IOException("Failed to apply audio fade-out", e);
    }
  }
}
