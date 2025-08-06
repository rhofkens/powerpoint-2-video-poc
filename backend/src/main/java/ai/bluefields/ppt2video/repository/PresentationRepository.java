package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.Presentation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Presentation} entities. Provides CRUD operations and custom
 * query methods for managing presentations in the database.
 */
@Repository
public interface PresentationRepository extends JpaRepository<Presentation, UUID> {

  /**
   * Find all presentations with their deck analysis eagerly loaded. This avoids the N+1 query
   * problem when fetching presentations.
   */
  @Query(
      "SELECT DISTINCT p FROM Presentation p LEFT JOIN FETCH p.deckAnalysis ORDER BY p.createdAt DESC")
  List<Presentation> findAllWithDeckAnalysis();
}
