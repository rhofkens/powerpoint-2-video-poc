package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.DeckAnalysis;
import java.time.LocalDateTime;
import java.util.UUID;

/** Data Transfer Object for DeckAnalysis entity. */
public record DeckAnalysisDto(
    UUID id,
    String overallStory,
    String communicationIntent,
    String keyThemes,
    String targetAudience,
    String tone,
    String analysisMetadata,
    String modelUsed,
    String promptVersion,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /**
   * Creates a DTO from a DeckAnalysis entity.
   *
   * @param deckAnalysis the deck analysis entity
   * @return the DTO representation
   */
  public static DeckAnalysisDto fromEntity(DeckAnalysis deckAnalysis) {
    return new DeckAnalysisDto(
        deckAnalysis.getId(),
        deckAnalysis.getOverallStory(),
        deckAnalysis.getCommunicationIntent(),
        deckAnalysis.getKeyThemes(),
        deckAnalysis.getTargetAudience(),
        deckAnalysis.getTone(),
        deckAnalysis.getAnalysisMetadata(),
        deckAnalysis.getModelUsed(),
        deckAnalysis.getPromptVersion(),
        deckAnalysis.getCreatedAt(),
        deckAnalysis.getUpdatedAt());
  }
}
