package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.ApiResponse;
import ai.bluefields.ppt2video.dto.PreflightCheckRequestDto;
import ai.bluefields.ppt2video.dto.PreflightCheckResponseDto;
import ai.bluefields.ppt2video.service.PreflightCheckService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for preflight check operations. Provides endpoints to validate presentation
 * readiness for video generation.
 */
@RestController
@RequestMapping("/api/presentations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PreflightCheckController {

  private final PreflightCheckService preflightCheckService;

  /**
   * Runs a comprehensive preflight check on a presentation. Validates that all slides have required
   * components (narrative, audio, video).
   *
   * @param id the ID of the presentation to check
   * @param request optional request parameters for the check
   * @return detailed check results including status for each slide
   */
  @PostMapping("/{id}/preflight-check")
  public ResponseEntity<ApiResponse<PreflightCheckResponseDto>> runPreflightCheck(
      @PathVariable UUID id, @RequestBody(required = false) PreflightCheckRequestDto request) {

    log.info("Received preflight check request for presentation: {}", id);

    try {
      // Set default request if not provided
      if (request == null) {
        request =
            PreflightCheckRequestDto.builder()
                .checkEnhancedNarrative(false)
                .forceRefresh(false)
                .build();
      }

      PreflightCheckResponseDto response = preflightCheckService.runPreflightCheck(id, request);

      return ResponseEntity.ok(
          ApiResponse.<PreflightCheckResponseDto>builder()
              .success(true)
              .data(response)
              .message("Preflight check completed successfully")
              .build());
    } catch (Exception e) {
      log.error("Error running preflight check for presentation: {}", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<PreflightCheckResponseDto>builder()
                  .success(false)
                  .message("Failed to run preflight check")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("PREFLIGHT_CHECK_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }

  /**
   * Gets the latest preflight check status for a presentation. Returns cached results if available
   * and still valid.
   *
   * @param id the ID of the presentation
   * @return the latest check results if available
   */
  @GetMapping("/{id}/preflight-status")
  public ResponseEntity<ApiResponse<PreflightCheckResponseDto>> getPreflightStatus(
      @PathVariable UUID id) {

    log.info("Fetching preflight status for presentation: {}", id);

    try {
      PreflightCheckResponseDto status = preflightCheckService.getLatestStatus(id);

      if (status != null) {
        return ResponseEntity.ok(
            ApiResponse.<PreflightCheckResponseDto>builder()
                .success(true)
                .data(status)
                .message("Preflight status retrieved successfully")
                .build());
      } else {
        // No cached status available, need to run a new check
        return ResponseEntity.ok(
            ApiResponse.<PreflightCheckResponseDto>builder()
                .success(false)
                .message("No recent preflight check available. Please run a new check.")
                .build());
      }
    } catch (Exception e) {
      log.error("Error fetching preflight status for presentation: {}", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              ApiResponse.<PreflightCheckResponseDto>builder()
                  .success(false)
                  .message("Failed to fetch preflight status")
                  .error(
                      ApiResponse.ErrorDetails.builder()
                          .code("PREFLIGHT_STATUS_ERROR")
                          .description(e.getMessage())
                          .build())
                  .build());
    }
  }
}
