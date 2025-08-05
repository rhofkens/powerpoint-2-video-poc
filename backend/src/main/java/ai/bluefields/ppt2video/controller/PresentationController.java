package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.PresentationUploadResponseDto;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.exception.FileSizeExceededException;
import ai.bluefields.ppt2video.exception.InvalidFileFormatException;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import ai.bluefields.ppt2video.service.PresentationParsingService;
import ai.bluefields.ppt2video.service.PresentationUploadService;
import ai.bluefields.ppt2video.service.SlideRenderingService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** REST controller for handling presentation upload and management operations. */
@RestController
@RequestMapping("/api/presentations")
@RequiredArgsConstructor
@Slf4j
public class PresentationController {

  private final PresentationUploadService uploadService;
  private final PresentationParsingService parsingService;
  private final SlideRenderingService renderingService;
  private final PresentationRepository presentationRepository;

  /**
   * Gets all presentations.
   *
   * @return list of presentations
   */
  @GetMapping
  public ResponseEntity<?> getPresentations() {
    log.debug("Getting all presentations");

    java.util.List<Presentation> presentations = presentationRepository.findAll();

    // Convert to DTOs to avoid lazy loading issues
    var presentationDtos =
        presentations.stream()
            .map(
                p ->
                    java.util.Map.of(
                        "id", p.getId().toString(),
                        "title", p.getTitle(),
                        "originalFilename", p.getOriginalFilename(),
                        "fileSize", p.getFileSize(),
                        "status", p.getStatus().name(),
                        "processingStatus", p.getProcessingStatus().name(),
                        "createdAt", p.getCreatedAt(),
                        "updatedAt", p.getUpdatedAt()))
            .toList();

    return ResponseEntity.ok(presentationDtos);
  }

  /**
   * Handles PowerPoint presentation file uploads.
   *
   * @param file the uploaded PPTX file
   * @return presentation metadata and upload confirmation
   */
  @PostMapping("/upload")
  public ResponseEntity<?> uploadPresentation(@RequestParam("file") @NotNull MultipartFile file) {
    log.info("Received upload request for file: {}", file.getOriginalFilename());

    try {
      // Use the new upload service which handles everything
      PresentationUploadResponseDto response = uploadService.uploadPresentation(file);

      log.info(
          "Successfully uploaded presentation with ID: {} for file: {}",
          response.getId(),
          file.getOriginalFilename());

      return ResponseEntity.ok(response);

    } catch (FileSizeExceededException e) {
      log.warn("File size exceeded for upload: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
          .body(createErrorResponse("FILE_SIZE_EXCEEDED", e.getMessage()));

    } catch (InvalidFileFormatException e) {
      log.warn("Invalid file format for upload: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
          .body(createErrorResponse("INVALID_FILE_FORMAT", e.getMessage()));

    } catch (Exception e) {
      log.error("Unexpected error during file upload", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("UPLOAD_FAILED", "An unexpected error occurred during upload"));
    }
  }

  /**
   * Gets the processing status of a presentation.
   *
   * @param id the presentation ID
   * @return status information
   */
  @GetMapping("/{id}/status")
  public ResponseEntity<?> getPresentationStatus(@PathVariable String id) {
    try {
      UUID presentationId = UUID.fromString(id);

      // Get parsing progress
      PresentationParsingService.ParsingProgress parsingProgress =
          parsingService.getParsingProgress(presentationId);

      // Get rendering progress
      SlideRenderingService.RenderingProgress renderingProgress =
          renderingService.getRenderingProgress(presentationId);

      // Build response - use HashMap to handle null values
      var parsingMap = new java.util.HashMap<String, Object>();
      parsingMap.put("status", parsingProgress.status());
      parsingMap.put("slideCount", parsingProgress.slideCount());
      parsingMap.put("startedAt", parsingProgress.startedAt());
      parsingMap.put("completedAt", parsingProgress.completedAt());

      var renderingMap = new java.util.HashMap<String, Object>();
      renderingMap.put("status", renderingProgress.status());
      renderingMap.put("completedSlides", renderingProgress.completedSlides());
      renderingMap.put("totalSlides", renderingProgress.totalSlides());
      renderingMap.put("renderer", renderingProgress.renderer());
      renderingMap.put("startedAt", renderingProgress.startedAt());
      renderingMap.put("completedAt", renderingProgress.completedAt());

      var response =
          java.util.Map.of(
              "id", id,
              "parsingProgress", parsingMap,
              "renderingProgress", renderingMap);

      return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse("INVALID_ID", "Invalid presentation ID format"));
    } catch (Exception e) {
      log.error("Error getting presentation status", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("STATUS_ERROR", "Failed to get presentation status"));
    }
  }

  /**
   * Triggers re-rendering of a presentation.
   *
   * @param id the presentation ID
   * @param request the render request containing renderer name and force flag
   * @return confirmation response
   */
  @PostMapping("/{id}/render")
  public ResponseEntity<?> reRenderPresentation(
      @PathVariable String id, @RequestBody java.util.Map<String, Object> request) {
    try {
      UUID presentationId = UUID.fromString(id);
      String renderer = (String) request.get("renderer");
      boolean force = Boolean.parseBoolean(String.valueOf(request.getOrDefault("force", false)));

      renderingService.reRenderPresentation(presentationId, renderer, force);

      return ResponseEntity.ok(
          java.util.Map.of(
              "message", "Re-rendering initiated",
              "presentationId", id,
              "renderer", renderer));

    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse("INVALID_REQUEST", e.getMessage()));
    } catch (Exception e) {
      log.error("Error triggering re-render", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("RENDER_ERROR", "Failed to initiate re-rendering"));
    }
  }

  /**
   * Creates a standardized error response.
   *
   * @param errorCode the error code
   * @param message the error message
   * @return error response map
   */
  private java.util.Map<String, Object> createErrorResponse(String errorCode, String message) {
    return java.util.Map.of(
        "error", errorCode,
        "message", message,
        "timestamp", LocalDateTime.now());
  }
}
