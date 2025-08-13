package ai.bluefields.ppt2video.service.ai;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.entity.SlideSpeech;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.repository.SlideSpeechRepository;
import ai.bluefields.ppt2video.service.FileStorageService;
import ai.bluefields.ppt2video.service.ai.narrative.TransitionRedundancyChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Service for converting text to speech using ElevenLabs API. Handles TTS generation with
 * timestamps and request stitching for better quality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TextToSpeechService {

  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper;
  private final FileStorageService fileStorageService;
  private final SlideSpeechRepository slideSpeechRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final SlideRepository slideRepository;
  private final TransitionRedundancyChecker redundancyChecker;

  @Value("${app.elevenlabs.api-key}")
  private String apiKey;

  @Value("${app.elevenlabs.api-url:https://api.elevenlabs.io/v1}")
  private String apiUrl;

  @Value("${app.elevenlabs.model:eleven_multilingual_v2}")
  private String defaultModel;

  @Value("${app.elevenlabs.voice.business:NNl6r8mD7vthiJatiJt1}")
  private String businessVoiceId;

  @Value("${app.elevenlabs.voice.funny:1SM7GgM6IMuvQlz2BwM3}")
  private String funnyVoiceId;

  @Value("${app.elevenlabs.voice.cynical:exsUS4vynmxd379XN4yO}")
  private String cynicalVoiceId;

  @Value("${app.elevenlabs.voice-settings.stability:0.5}")
  private double stability;

  @Value("${app.elevenlabs.voice-settings.similarity-boost:0.75}")
  private double similarityBoost;

  @Value("${app.elevenlabs.voice-settings.style:0.5}")
  private double style;

  @Value("${app.elevenlabs.voice-settings.use-speaker-boost:true}")
  private boolean useSpeakerBoost;

  @Value("${app.elevenlabs.enable-request-stitching:true}")
  private boolean enableRequestStitching;

  @Value("${app.elevenlabs.output-format:mp3_44100_128}")
  private String outputFormat;

  /**
   * Generates speech for a slide narrative with timestamps.
   *
   * @param narrativeId the ID of the narrative to convert
   * @param narrativeStyle the style of narrative (business, funny, cynical)
   * @param forceRegenerate whether to force regeneration even if speech exists
   * @return the generated SlideSpeech entity
   */
  @Transactional
  public SlideSpeech generateSpeech(
      UUID narrativeId, String narrativeStyle, boolean forceRegenerate) {
    log.info(
        "Starting TTS generation for narrative: {} with style: {}", narrativeId, narrativeStyle);

    // Get the narrative
    SlideNarrative narrative =
        slideNarrativeRepository
            .findById(narrativeId)
            .orElseThrow(() -> new IllegalArgumentException("Narrative not found: " + narrativeId));

    // Check if active speech already exists for this narrative
    Optional<SlideSpeech> activeSpeechForNarrative =
        slideSpeechRepository.findActiveBySlideNarrativeId(narrativeId);

    if (!forceRegenerate && activeSpeechForNarrative.isPresent()) {
      log.info("Active speech already exists for narrative: {}", narrativeId);
      return activeSpeechForNarrative.get();
    }

    // IMPORTANT: Deactivate ALL active speeches for this SLIDE (not just this narrative)
    // This handles the case where a new narrative was generated and we're creating speech for it
    List<SlideSpeech> allActiveSpeeches =
        slideSpeechRepository.findAllActiveBySlideIdOrderByCreatedAtDesc(
            narrative.getSlide().getId());

    if (!allActiveSpeeches.isEmpty()) {
      log.info(
          "Deactivating {} existing active speech(es) for slide: {}",
          allActiveSpeeches.size(),
          narrative.getSlide().getId());

      for (SlideSpeech oldSpeech : allActiveSpeeches) {
        oldSpeech.setIsActive(false);
        slideSpeechRepository.saveAndFlush(
            oldSpeech); // Use saveAndFlush to ensure immediate persistence
        log.info(
            "Deactivated speech with ID: {} (was for narrative: {})",
            oldSpeech.getId(),
            oldSpeech.getSlideNarrative() != null ? oldSpeech.getSlideNarrative().getId() : "null");
      }
    }

    try {
      // Select voice based on style
      String voiceId = selectVoiceForStyle(narrativeStyle);

      // Prepare the text with transition phrase if present
      TransitionResult transitionResult = prepareTextWithTransitionAndTrack(narrative);
      String textForTTS = transitionResult.text;

      // Prepare the request
      String requestId = enableRequestStitching ? UUID.randomUUID().toString() : null;
      TTSResponse response = callElevenLabsAPI(textForTTS, voiceId, requestId);

      // Store the audio file
      String audioPath =
          storeAudioFile(
              narrative.getSlide().getPresentation().getId(),
              narrative.getSlide().getId(),
              response.getAudioData());

      // Create and save SlideSpeech entity
      SlideSpeech slideSpeech = new SlideSpeech();
      slideSpeech.setSlideNarrative(narrative);
      slideSpeech.setSlide(narrative.getSlide());
      slideSpeech.setPresentation(narrative.getSlide().getPresentation());
      slideSpeech.setAudioFilePath(audioPath);
      slideSpeech.setVoiceId(voiceId);
      slideSpeech.setVoiceStyle(narrativeStyle);
      slideSpeech.setModelUsed(defaultModel);
      slideSpeech.setDurationSeconds(response.getDurationSeconds());
      slideSpeech.setTimingData(objectMapper.writeValueAsString(response.getTimestamps()));
      slideSpeech.setRequestId(requestId);
      slideSpeech.setOutputFormat(outputFormat);
      slideSpeech.setIsActive(true); // Explicitly set as active
      slideSpeech.setTransitionIncluded(transitionResult.included);
      slideSpeech.setTransitionSkippedReason(transitionResult.skipReason);

      // Update the narrative's duration with the actual TTS duration
      narrative.setDurationSeconds((int) Math.ceil(response.getDurationSeconds()));
      slideNarrativeRepository.save(narrative);

      // Add generation metadata
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("stability", stability);
      metadata.put("similarity_boost", similarityBoost);
      metadata.put("style", style);
      metadata.put("use_speaker_boost", useSpeakerBoost);
      metadata.put("characters_processed", response.getCharactersProcessed());
      slideSpeech.setGenerationMetadata(objectMapper.writeValueAsString(metadata));

      slideSpeech = slideSpeechRepository.save(slideSpeech);

      // Update the slide's audio_path to point to the new speech
      Slide slide = narrative.getSlide();
      slide.setAudioPath(audioPath);
      slideRepository.save(slide);
      log.info("Updated slide {} audio_path to: {}", slide.getId(), audioPath);

      log.info(
          "Successfully generated speech for narrative: {}, duration: {} seconds",
          narrativeId,
          response.getDurationSeconds());

      return slideSpeech;

    } catch (Exception e) {
      log.error("Failed to generate speech for narrative: {}", narrativeId, e);
      throw new RuntimeException("Failed to generate speech: " + e.getMessage(), e);
    }
  }

  /** Result of transition preparation containing the text and metadata. */
  private static class TransitionResult {
    final String text;
    final boolean included;
    final String skipReason;

    TransitionResult(String text, boolean included, String skipReason) {
      this.text = text;
      this.included = included;
      this.skipReason = skipReason;
    }
  }

  /**
   * Prepares the text for TTS by including the narrative and transition phrase. Checks for
   * redundancy to avoid repeating concepts.
   *
   * @param narrative the narrative entity
   * @return TransitionResult with the combined text and metadata
   */
  private TransitionResult prepareTextWithTransitionAndTrack(SlideNarrative narrative) {
    String narrativeText = narrative.getNarrativeText();
    String transitionPhrase = narrative.getTransitionPhrase();

    // If there's no transition phrase, return just the narrative
    if (transitionPhrase == null || transitionPhrase.trim().isEmpty()) {
      return new TransitionResult(narrativeText, false, "No transition phrase");
    }

    // Get the next slide's narrative to check for redundancy
    SlideNarrative nextNarrative = getNextSlideNarrative(narrative);

    if (nextNarrative == null) {
      // This is the last slide, include transition if present
      log.info(
          "Including transition for last slide {}: \"{}\"",
          narrative.getSlide().getSlideNumber(),
          transitionPhrase.substring(0, Math.min(50, transitionPhrase.length())) + "...");
      return new TransitionResult(narrativeText + " ... " + transitionPhrase, true, null);
    }

    // Check for redundancy
    boolean isRedundant =
        redundancyChecker.isTransitionRedundant(transitionPhrase, nextNarrative.getNarrativeText());

    if (isRedundant) {
      log.info(
          "Skipping redundant transition for slide {}: \"{}\"",
          narrative.getSlide().getSlideNumber(),
          transitionPhrase.substring(0, Math.min(50, transitionPhrase.length())) + "...");
      return new TransitionResult(narrativeText, false, "Redundant with next slide opening");
    }

    // Include the transition
    log.info(
        "Including transition for slide {}: \"{}\"",
        narrative.getSlide().getSlideNumber(),
        transitionPhrase.substring(0, Math.min(50, transitionPhrase.length())) + "...");
    return new TransitionResult(narrativeText + " ... " + transitionPhrase, true, null);
  }

  /**
   * Gets the narrative for the next slide in the presentation.
   *
   * @param currentNarrative the current slide's narrative
   * @return the next slide's narrative, or null if this is the last slide
   */
  private SlideNarrative getNextSlideNarrative(SlideNarrative currentNarrative) {
    int currentSlideNumber = currentNarrative.getSlide().getSlideNumber();
    UUID presentationId = currentNarrative.getSlide().getPresentation().getId();

    // Find the narrative for the next slide number
    return slideNarrativeRepository
        .findByPresentationIdAndSlideNumber(presentationId, currentSlideNumber + 1)
        .orElse(null);
  }

  private TTSResponse callElevenLabsAPI(String text, String voiceId, String requestId)
      throws IOException {
    String url = apiUrl + "/text-to-speech/" + voiceId + "/with-timestamps";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("xi-api-key", apiKey);
    if (requestId != null) {
      headers.set("xi-request-id", requestId);
    }

    // Build request body
    ObjectNode requestBody = objectMapper.createObjectNode();
    requestBody.put("text", text);
    requestBody.put("model_id", defaultModel);

    // Voice settings
    ObjectNode voiceSettings = objectMapper.createObjectNode();
    voiceSettings.put("stability", stability);
    voiceSettings.put("similarity_boost", similarityBoost);
    voiceSettings.put("style", style);
    voiceSettings.put("use_speaker_boost", useSpeakerBoost);
    requestBody.set("voice_settings", voiceSettings);

    requestBody.put("output_format", outputFormat);

    HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);

    try {
      @SuppressWarnings("rawtypes")
      ResponseEntity<Map> response =
          restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();

        // Log the response structure for debugging
        log.info("ElevenLabs API response keys: {}", responseBody.keySet());
        if (responseBody.containsKey("alignment")) {
          @SuppressWarnings("unchecked")
          Map<String, Object> alignment = (Map<String, Object>) responseBody.get("alignment");
          log.info("Alignment keys: {}", alignment != null ? alignment.keySet() : "null");
        }
        if (responseBody.containsKey("normalized_alignment")) {
          @SuppressWarnings("unchecked")
          Map<String, Object> normalizedAlignment =
              (Map<String, Object>) responseBody.get("normalized_alignment");
          log.info(
              "Normalized alignment keys: {}",
              normalizedAlignment != null ? normalizedAlignment.keySet() : "null");
        }

        // Extract audio data (base64 encoded)
        String audioBase64 = (String) responseBody.get("audio_base64");
        byte[] audioData = Base64.getDecoder().decode(audioBase64);

        // Extract timestamps from alignment data
        List<Map<String, Object>> timestamps = new ArrayList<>();
        double duration = 0;

        @SuppressWarnings("unchecked")
        Map<String, Object> alignment =
            (Map<String, Object>) responseBody.get("normalized_alignment");
        if (alignment == null) {
          @SuppressWarnings("unchecked")
          Map<String, Object> fallbackAlignment =
              (Map<String, Object>) responseBody.get("alignment");
          alignment = fallbackAlignment;
        }

        if (alignment != null) {
          @SuppressWarnings("unchecked")
          List<String> characters = (List<String>) alignment.get("characters");
          @SuppressWarnings("unchecked")
          List<Number> startTimes = (List<Number>) alignment.get("character_start_times_seconds");
          @SuppressWarnings("unchecked")
          List<Number> endTimes = (List<Number>) alignment.get("character_end_times_seconds");

          if (characters != null && startTimes != null && endTimes != null) {
            int size = Math.min(characters.size(), Math.min(startTimes.size(), endTimes.size()));
            for (int i = 0; i < size; i++) {
              Map<String, Object> timestamp = new HashMap<>();
              timestamp.put("character", characters.get(i));
              timestamp.put("start_time", startTimes.get(i).doubleValue());
              timestamp.put("end_time", endTimes.get(i).doubleValue());
              timestamps.add(timestamp);
            }

            // Calculate duration from the last character's end time
            if (!endTimes.isEmpty()) {
              duration = endTimes.get(endTimes.size() - 1).doubleValue();
              log.info(
                  "Calculated TTS duration: {} seconds from {} timestamps",
                  duration,
                  endTimes.size());
            }
          } else {
            log.warn(
                "Missing alignment data - characters: {}, startTimes: {}, endTimes: {}",
                characters != null,
                startTimes != null,
                endTimes != null);
          }
        } else {
          log.warn("No alignment data found in ElevenLabs response");
        }

        return new TTSResponse(audioData, timestamps, duration, text.length());
      } else {
        throw new RuntimeException(
            "Unexpected response from ElevenLabs API: " + response.getStatusCode());
      }

    } catch (HttpClientErrorException e) {
      log.error("ElevenLabs API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new RuntimeException("ElevenLabs API error: " + e.getMessage(), e);
    }
  }

  /** Stores the audio file on disk. */
  private String storeAudioFile(UUID presentationId, UUID slideId, byte[] audioData)
      throws IOException {
    Path presentationDir =
        Paths.get(fileStorageService.createPresentationDirectory(presentationId).toString());
    Path audioDir = presentationDir.resolve("audio");
    Files.createDirectories(audioDir);

    String filename =
        String.format("slide_%s_%s.mp3", slideId.toString(), System.currentTimeMillis());
    Path audioPath = audioDir.resolve(filename);

    Files.write(
        audioPath, audioData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    log.debug("Stored audio file at: {}", audioPath);
    return audioPath.toAbsolutePath().toString();
  }

  /** Selects the appropriate voice ID based on narrative style. */
  private String selectVoiceForStyle(String style) {
    if (style == null) {
      return businessVoiceId;
    }

    return switch (style.toLowerCase()) {
      case "funny" -> funnyVoiceId;
      case "cynical" -> cynicalVoiceId;
      case "business" -> businessVoiceId;
      default -> businessVoiceId;
    };
  }

  /** Inner class to hold TTS response data. */
  private static class TTSResponse {
    private final byte[] audioData;
    private final List<Map<String, Object>> timestamps;
    private final double durationSeconds;
    private final int charactersProcessed;

    public TTSResponse(
        byte[] audioData,
        List<Map<String, Object>> timestamps,
        double durationSeconds,
        int charactersProcessed) {
      this.audioData = audioData;
      this.timestamps = timestamps;
      this.durationSeconds = durationSeconds;
      this.charactersProcessed = charactersProcessed;
    }

    public byte[] getAudioData() {
      return audioData;
    }

    public List<Map<String, Object>> getTimestamps() {
      return timestamps;
    }

    public double getDurationSeconds() {
      return durationSeconds;
    }

    public int getCharactersProcessed() {
      return charactersProcessed;
    }
  }
}
