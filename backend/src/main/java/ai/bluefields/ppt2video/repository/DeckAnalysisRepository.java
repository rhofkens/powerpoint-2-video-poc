package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.DeckAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository interface for DeckAnalysis entities. */
@Repository
public interface DeckAnalysisRepository extends JpaRepository<DeckAnalysis, UUID> {

  /**
   * Find deck analysis by presentation ID.
   *
   * @param presentationId The presentation ID
   * @return Optional containing the deck analysis if found
   */
  Optional<DeckAnalysis> findByPresentationId(UUID presentationId);
}
