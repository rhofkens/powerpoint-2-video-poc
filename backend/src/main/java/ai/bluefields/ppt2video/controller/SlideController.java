package ai.bluefields.ppt2video.controller;

import ai.bluefields.ppt2video.dto.SlideAnalysisDto;
import ai.bluefields.ppt2video.dto.SlideDto;
import ai.bluefields.ppt2video.dto.SlideNarrativeDto;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST controller for handling slide-related operations. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SlideController {

  private final SlideRepository slideRepository;

  /**
   * Retrieves all slides for a specific presentation.
   *
   * @param presentationId the presentation ID
   * @return list of slides
   */
  @GetMapping("/presentations/{presentationId}/slides")
  public ResponseEntity<?> getSlidesByPresentation(@PathVariable String presentationId) {
    try {
      UUID presId = UUID.fromString(presentationId);
      List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presId);

      // Convert to DTOs to avoid circular references
      List<SlideDto> slideDtos = slides.stream().map(this::convertToDto).toList();

      log.debug("Found {} slides for presentation {}", slideDtos.size(), presentationId);
      return ResponseEntity.ok(slideDtos);

    } catch (IllegalArgumentException e) {
      log.warn("Invalid presentation ID format: {}", presentationId);
      return ResponseEntity.badRequest()
          .body(java.util.Map.of("error", "Invalid presentation ID format"));
    }
  }

  /**
   * Retrieves a specific slide by ID.
   *
   * @param slideId the slide ID
   * @return the slide data
   */
  @GetMapping("/slides/{slideId}")
  public ResponseEntity<?> getSlide(@PathVariable String slideId) {
    try {
      UUID id = UUID.fromString(slideId);
      return slideRepository
          .findById(id)
          .map(slide -> ResponseEntity.ok(convertToDto(slide)))
          .orElse(ResponseEntity.notFound().build());

    } catch (IllegalArgumentException e) {
      log.warn("Invalid slide ID format: {}", slideId);
      return ResponseEntity.badRequest().body(java.util.Map.of("error", "Invalid slide ID format"));
    }
  }

  /**
   * Serves the image file for a specific slide.
   *
   * @param slideId the slide ID
   * @return the image file
   */
  @GetMapping("/slides/{slideId}/image")
  public ResponseEntity<Resource> getSlideImage(@PathVariable String slideId) {
    try {
      UUID id = UUID.fromString(slideId);

      return slideRepository
          .findById(id)
          .map(
              slide -> {
                if (slide.getImagePath() == null) {
                  return ResponseEntity.notFound().<Resource>build();
                }

                try {
                  Path imagePath = Paths.get(slide.getImagePath());

                  // Check if it's a placeholder
                  if (slide.getImagePath().contains("placeholder")) {
                    // For placeholders, return a different content type
                    Resource resource = new FileSystemResource(imagePath);
                    return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .header("X-Image-Type", "placeholder")
                        .body(resource);
                  }

                  if (!Files.exists(imagePath)) {
                    log.warn(
                        "Image file not found for slide {}: {}", slideId, slide.getImagePath());
                    return ResponseEntity.notFound().<Resource>build();
                  }

                  Resource resource = new FileSystemResource(imagePath);
                  String contentType = Files.probeContentType(imagePath);

                  if (contentType == null) {
                    contentType = "image/png"; // Default to PNG
                  }

                  return ResponseEntity.ok()
                      .contentType(MediaType.parseMediaType(contentType))
                      .header("Cache-Control", "max-age=3600")
                      .body(resource);

                } catch (IOException e) {
                  log.error("Error serving image for slide {}", slideId, e);
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Resource>build();
                }
              })
          .orElse(ResponseEntity.notFound().build());

    } catch (IllegalArgumentException e) {
      log.warn("Invalid slide ID format: {}", slideId);
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Converts a Slide entity to SlideDto.
   *
   * @param slide the slide entity
   * @return the slide DTO
   */
  private SlideDto convertToDto(Slide slide) {
    var dto =
        SlideDto.builder()
            .id(slide.getId().toString())
            .presentationId(slide.getPresentation().getId().toString())
            .slideNumber(slide.getSlideNumber())
            .title(slide.getTitle())
            .content(slide.getContent())
            .contentText(slide.getContentText())
            .speakerNotes(slide.getSpeakerNotes())
            .layoutType(slide.getLayoutType())
            .imagePath(slide.getImagePath())
            .imageGeneratedAt(slide.getImageGeneratedAt())
            .imageWidth(slide.getImageWidth())
            .imageHeight(slide.getImageHeight())
            .renderingStatus(
                slide.getRenderingStatus() != null ? slide.getRenderingStatus().name() : null)
            .renderingErrorMessage(slide.getRenderingErrorMessage())
            .audioPath(slide.getAudioPath())
            .videoPath(slide.getVideoPath())
            .generatedNarrative(slide.getGeneratedNarrative())
            .contentWordCount(slide.getContentWordCount())
            .processingStatus(
                slide.getProcessingStatus() != null ? slide.getProcessingStatus().name() : null)
            .createdAt(slide.getCreatedAt())
            .updatedAt(slide.getUpdatedAt());

    // Add slide analysis if available
    if (slide.getSlideAnalysis() != null) {
      dto.slideAnalysis(convertAnalysisToDto(slide.getSlideAnalysis()));
    }

    // Add active narrative if available
    if (slide.getSlideNarratives() != null && !slide.getSlideNarratives().isEmpty()) {
      slide.getSlideNarratives().stream()
          .filter(n -> Boolean.TRUE.equals(n.getIsActive()))
          .findFirst()
          .ifPresent(narrative -> dto.slideNarrative(convertNarrativeToDto(narrative)));
    }

    return dto.build();
  }

  /**
   * Converts a SlideAnalysis entity to SlideAnalysisDto.
   *
   * @param analysis the slide analysis entity
   * @return the slide analysis DTO
   */
  private SlideAnalysisDto convertAnalysisToDto(SlideAnalysis analysis) {
    return SlideAnalysisDto.builder()
        .id(analysis.getId().toString())
        .generalMessage(analysis.getGeneralMessage())
        .visualConcepts(analysis.getVisualConcepts())
        .keyPoints(analysis.getKeyPoints())
        .dataInsights(analysis.getDataInsights())
        .transitionContext(analysis.getTransitionContext())
        .emphasisLevel(analysis.getEmphasisLevel())
        .slideType(analysis.getSlideType() != null ? analysis.getSlideType().name() : null)
        .analysisMetadata(analysis.getAnalysisMetadata())
        .modelUsed(analysis.getModelUsed())
        .promptVersion(analysis.getPromptVersion())
        .createdAt(analysis.getCreatedAt())
        .updatedAt(analysis.getUpdatedAt())
        .build();
  }

  /**
   * Converts a SlideNarrative entity to SlideNarrativeDto.
   *
   * @param narrative the slide narrative entity
   * @return the slide narrative DTO
   */
  private SlideNarrativeDto convertNarrativeToDto(SlideNarrative narrative) {
    return SlideNarrativeDto.builder()
        .id(narrative.getId().toString())
        .narrativeText(narrative.getNarrativeText())
        .emotionIndicators(narrative.getEmotionIndicators())
        .avatarInstructions(narrative.getAvatarInstructions())
        .speechMarkers(narrative.getSpeechMarkers())
        .durationSeconds(narrative.getDurationSeconds())
        .targetDurationSeconds(narrative.getTargetDurationSeconds())
        .transitionPhrase(narrative.getTransitionPhrase())
        .emphasisWords(narrative.getEmphasisWords())
        .version(narrative.getVersion())
        .isActive(narrative.getIsActive())
        .generationMetadata(narrative.getGenerationMetadata())
        .modelUsed(narrative.getModelUsed())
        .promptVersion(narrative.getPromptVersion())
        .createdAt(narrative.getCreatedAt())
        .updatedAt(narrative.getUpdatedAt())
        .build();
  }
}
