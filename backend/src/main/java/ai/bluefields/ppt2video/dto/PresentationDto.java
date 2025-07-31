package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.Presentation;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Presentation entity. Used for API communication to avoid exposing
 * internal entity structure.
 */
public record PresentationDto(
    UUID id,
    String title,
    String originalFilename,
    Long fileSize,
    Presentation.ProcessingStatus processingStatus,
    String narrativeStyle,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /**
   * Creates a DTO from a Presentation entity.
   *
   * @param presentation the presentation entity
   * @return the DTO representation
   */
  public static PresentationDto fromEntity(Presentation presentation) {
    return new PresentationDto(
        presentation.getId(),
        presentation.getTitle(),
        presentation.getOriginalFilename(),
        presentation.getFileSize(),
        presentation.getProcessingStatus(),
        presentation.getNarrativeStyle(),
        presentation.getCreatedAt(),
        presentation.getUpdatedAt());
  }
}
