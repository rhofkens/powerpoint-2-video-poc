package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.ApiResponse;
import ai.bluefields.ppt2video.dto.ColorPaletteDto;
import ai.bluefields.ppt2video.dto.IntroVideoRequest;
import ai.bluefields.ppt2video.dto.IntroVideoResponse;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.intro.ColorExtractionService;
import ai.bluefields.ppt2video.service.intro.IntroVideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for intro video generation operations. Provides endpoints for generating and
 * managing intro videos using Google Veo API.
 */
@RestController
@RequestMapping("/api/intro-videos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Intro Videos", description = "Intro video generation and management")
public class IntroVideoController {

  private final IntroVideoService introVideoService;
  private final ColorExtractionService colorExtractionService;
  private final SlideRepository slideRepository;

  /**
   * Generate an intro video for a presentation.
   *
   * @param request the intro video generation request
   * @return intro video response with generation details
   */
  @PostMapping("/generate")
  @Operation(
      summary = "Generate intro video",
      description = "Generate an AI-powered intro video for a presentation using Google Veo")
  public ResponseEntity<ApiResponse<IntroVideoResponse>> generateIntroVideo(
      @Valid @RequestBody IntroVideoRequest request) {

    log.info(
        "Received intro video generation request for presentation: {}",
        request.getPresentationId());

    try {
      IntroVideoResponse response = introVideoService.generateIntroVideo(request);

      return ResponseEntity.ok(
          ApiResponse.<IntroVideoResponse>builder()
              .success(true)
              .message("Intro video generation started successfully")
              .data(response)
              .build());

    } catch (Exception e) {
      log.error("Error generating intro video", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<IntroVideoResponse>builder()
                  .success(false)
                  .message("Failed to generate intro video: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Get the status of an intro video generation.
   *
   * @param id the intro video ID
   * @return intro video response with current status
   */
  @GetMapping("/{id}/status")
  @Operation(
      summary = "Get intro video status",
      description = "Get the current status of an intro video generation")
  public ResponseEntity<ApiResponse<IntroVideoResponse>> getIntroVideoStatus(
      @Parameter(description = "Intro video ID") @PathVariable UUID id) {

    try {
      IntroVideoResponse response = introVideoService.getIntroVideoStatus(id);

      return ResponseEntity.ok(
          ApiResponse.<IntroVideoResponse>builder().success(true).data(response).build());

    } catch (Exception e) {
      log.error("Error getting intro video status", e);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(
              ApiResponse.<IntroVideoResponse>builder()
                  .success(false)
                  .message("Intro video not found: " + id)
                  .build());
    }
  }

  /**
   * Get the latest intro video for a presentation.
   *
   * @param presentationId the presentation ID
   * @return intro video response if exists
   */
  @GetMapping("/presentation/{presentationId}")
  @Operation(
      summary = "Get presentation intro video",
      description = "Get the latest intro video for a presentation")
  public ResponseEntity<ApiResponse<IntroVideoResponse>> getPresentationIntroVideo(
      @Parameter(description = "Presentation ID") @PathVariable UUID presentationId) {

    try {
      Optional<IntroVideoResponse> response = introVideoService.getLatestIntroVideo(presentationId);

      if (response.isPresent()) {
        return ResponseEntity.ok(
            ApiResponse.<IntroVideoResponse>builder().success(true).data(response.get()).build());
      } else {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<IntroVideoResponse>builder()
                    .success(false)
                    .message("No intro video found for presentation: " + presentationId)
                    .build());
      }

    } catch (Exception e) {
      log.error("Error getting presentation intro video", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<IntroVideoResponse>builder()
                  .success(false)
                  .message("Failed to get intro video: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Cancel an intro video generation.
   *
   * @param id the intro video ID
   * @return success response
   */
  @PostMapping("/{id}/cancel")
  @Operation(
      summary = "Cancel intro video generation",
      description = "Cancel an ongoing intro video generation")
  public ResponseEntity<ApiResponse<Void>> cancelGeneration(
      @Parameter(description = "Intro video ID") @PathVariable UUID id) {

    try {
      introVideoService.cancelGeneration(id);

      return ResponseEntity.ok(
          ApiResponse.<Void>builder()
              .success(true)
              .message("Intro video generation cancelled")
              .build());

    } catch (Exception e) {
      log.error("Error cancelling intro video generation", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<Void>builder()
                  .success(false)
                  .message("Failed to cancel generation: " + e.getMessage())
                  .build());
    }
  }

  /**
   * Extract colors from the first slide of a presentation.
   *
   * @param presentationId the presentation ID
   * @return color palette extracted from the first slide
   */
  @GetMapping("/presentation/{presentationId}/colors")
  @Operation(
      summary = "Extract slide colors",
      description = "Extract dominant colors from the first slide of a presentation")
  public ResponseEntity<ApiResponse<ColorPaletteDto>> extractSlideColors(
      @Parameter(description = "Presentation ID") @PathVariable UUID presentationId) {

    try {
      // Get first slide
      Optional<Slide> firstSlide =
          slideRepository.findByPresentationIdOrderBySlideNumber(presentationId).stream()
              .findFirst();

      if (firstSlide.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.<ColorPaletteDto>builder()
                    .success(false)
                    .message("No slides found for presentation: " + presentationId)
                    .build());
      }

      ColorPaletteDto colorPalette = colorExtractionService.extractColors(firstSlide.get());

      return ResponseEntity.ok(
          ApiResponse.<ColorPaletteDto>builder().success(true).data(colorPalette).build());

    } catch (Exception e) {
      log.error("Error extracting colors", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<ColorPaletteDto>builder()
                  .success(false)
                  .message("Failed to extract colors: " + e.getMessage())
                  .build());
    }
  }
}
