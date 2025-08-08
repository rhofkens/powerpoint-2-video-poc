package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.GenerateSpeechRequest;
import ai.bluefields.ppt2video.dto.SlideSpeechDto;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.entity.SlideSpeech;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.repository.SlideSpeechRepository;
import ai.bluefields.ppt2video.service.ai.TextToSpeechService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST controller for Text-to-Speech operations. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class TextToSpeechController {

  private final TextToSpeechService textToSpeechService;
  private final SlideSpeechRepository slideSpeechRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final ObjectMapper objectMapper;

  /**
   * Generates speech for a slide narrative.
   *
   * @param request the speech generation request
   * @return the generated speech data
   */
  @PostMapping("/slides/{slideId}/generate-speech")
  public ResponseEntity<?> generateSpeech(
      @PathVariable String slideId, @Valid @RequestBody GenerateSpeechRequest request) {

    log.info("Received request to generate speech for slide: {}", slideId);

    try {
      UUID slideUuid = UUID.fromString(slideId);

      // Get the active narrative for the slide
      SlideNarrative narrative =
          slideNarrativeRepository
              .findBySlideIdAndIsActiveTrue(slideUuid)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No active narrative found for slide: " + slideId));

      // Generate speech
      SlideSpeech speech =
          textToSpeechService.generateSpeech(
              narrative.getId(),
              request.getNarrativeStyle() != null ? request.getNarrativeStyle() : "business",
              request.getForceRegenerate() != null ? request.getForceRegenerate() : false);

      // Convert to DTO
      SlideSpeechDto dto = convertToDto(speech);

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", dto);
      response.put("message", "Speech generated successfully");

      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      log.warn("Invalid request: {}", e.getMessage());
      Map<String, Object> error = new HashMap<>();
      error.put("success", false);
      error.put("error", e.getMessage());
      return ResponseEntity.badRequest().body(error);

    } catch (Exception e) {
      log.error("Failed to generate speech for slide: {}", slideId, e);
      Map<String, Object> error = new HashMap<>();
      error.put("success", false);
      error.put("error", "Failed to generate speech: " + e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
  }

  /**
   * Gets the speech data for a slide.
   *
   * @param slideId the slide ID
   * @return the speech data if available
   */
  @GetMapping("/slides/{slideId}/speech")
  public ResponseEntity<?> getSlideSpeech(@PathVariable String slideId) {
    try {
      UUID slideUuid = UUID.fromString(slideId);

      Optional<SlideSpeech> speech = slideSpeechRepository.findActiveBySlideId(slideUuid);

      if (speech.isEmpty()) {
        return ResponseEntity.notFound().build();
      }

      SlideSpeechDto dto = convertToDto(speech.get());

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", dto);

      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      log.warn("Invalid slide ID format: {}", slideId);
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid slide ID format"));
    }
  }

  /**
   * Serves the audio file for a specific slide speech.
   *
   * @param speechId the speech ID
   * @return the audio file
   */
  @GetMapping("/speeches/{speechId}/audio")
  public ResponseEntity<Resource> getSpeechAudio(@PathVariable String speechId) {
    try {
      UUID speechUuid = UUID.fromString(speechId);

      return slideSpeechRepository
          .findById(speechUuid)
          .map(
              speech -> {
                try {
                  Path audioPath = Paths.get(speech.getAudioFilePath());

                  if (!Files.exists(audioPath)) {
                    log.warn(
                        "Audio file not found for speech {}: {}",
                        speechId,
                        speech.getAudioFilePath());
                    return ResponseEntity.notFound().<Resource>build();
                  }

                  Resource resource = new FileSystemResource(audioPath);
                  String contentType = Files.probeContentType(audioPath);

                  if (contentType == null) {
                    contentType = "audio/mpeg"; // Default to MP3
                  }

                  return ResponseEntity.ok()
                      .contentType(MediaType.parseMediaType(contentType))
                      .header("Cache-Control", "max-age=3600")
                      .body(resource);

                } catch (IOException e) {
                  log.error("Error serving audio for speech {}", speechId, e);
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Resource>build();
                }
              })
          .orElse(ResponseEntity.notFound().build());

    } catch (IllegalArgumentException e) {
      log.warn("Invalid speech ID format: {}", speechId);
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Gets all speeches for a presentation.
   *
   * @param presentationId the presentation ID
   * @return list of speeches
   */
  @GetMapping("/presentations/{presentationId}/speeches")
  public ResponseEntity<?> getPresentationSpeeches(@PathVariable String presentationId) {
    try {
      UUID presId = UUID.fromString(presentationId);
      List<SlideSpeech> speeches =
          slideSpeechRepository.findByPresentationIdOrderBySlide_SlideNumber(presId);

      List<SlideSpeechDto> dtos = speeches.stream().map(this::convertToDto).toList();

      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("data", dtos);
      response.put("count", dtos.size());

      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      log.warn("Invalid presentation ID format: {}", presentationId);
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid presentation ID format"));
    }
  }

  /** Converts a SlideSpeech entity to DTO. */
  private SlideSpeechDto convertToDto(SlideSpeech speech) {
    SlideSpeechDto.SlideSpeechDtoBuilder builder =
        SlideSpeechDto.builder()
            .id(speech.getId().toString())
            .presentationId(speech.getPresentation().getId().toString())
            .slideId(speech.getSlide().getId().toString())
            .slideNarrativeId(speech.getSlideNarrative().getId().toString())
            .audioFilePath(speech.getAudioFilePath())
            .voiceId(speech.getVoiceId())
            .voiceStyle(speech.getVoiceStyle())
            .modelUsed(speech.getModelUsed())
            .durationSeconds(speech.getDurationSeconds())
            .requestId(speech.getRequestId())
            .outputFormat(speech.getOutputFormat())
            .isActive(speech.getIsActive())
            .createdAt(speech.getCreatedAt())
            .updatedAt(speech.getUpdatedAt());

    // Parse timing data
    if (speech.getTimingData() != null) {
      try {
        List<Map<String, Object>> timingMaps =
            objectMapper.readValue(
                speech.getTimingData(), new TypeReference<List<Map<String, Object>>>() {});

        List<SlideSpeechDto.TimestampDto> timestamps =
            timingMaps.stream()
                .map(
                    map ->
                        SlideSpeechDto.TimestampDto.builder()
                            .character((String) map.get("character"))
                            .startTime(((Number) map.get("start_time")).doubleValue())
                            .endTime(((Number) map.get("end_time")).doubleValue())
                            .build())
                .toList();

        builder.timestamps(timestamps);
      } catch (Exception e) {
        log.error("Failed to parse timing data for speech {}", speech.getId(), e);
      }
    }

    // Parse generation metadata
    if (speech.getGenerationMetadata() != null) {
      try {
        Map<String, Object> metadata =
            objectMapper.readValue(
                speech.getGenerationMetadata(), new TypeReference<Map<String, Object>>() {});
        builder.generationMetadata(metadata);
      } catch (Exception e) {
        log.error("Failed to parse generation metadata for speech {}", speech.getId(), e);
      }
    }

    return builder.build();
  }
}
