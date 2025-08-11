package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.Slide;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Slide} entities. Provides CRUD operations and custom query
 * methods for managing slides in the database.
 */
@Repository
public interface SlideRepository extends JpaRepository<Slide, UUID> {

  /**
   * Finds all slides belonging to a specific presentation, ordered by slide number.
   *
   * @param presentationId the UUID of the presentation
   * @return list of slides sorted by slide number in ascending order
   */
  List<Slide> findByPresentationIdOrderBySlideNumber(UUID presentationId);

  /**
   * Counts the number of slides for a specific presentation.
   *
   * @param presentationId the UUID of the presentation
   * @return the number of slides
   */
  int countByPresentationId(UUID presentationId);

  /**
   * Finds all slides belonging to a specific presentation with narratives eagerly loaded. This
   * method is used for parallel processing to avoid lazy loading issues.
   *
   * @param presentationId the UUID of the presentation
   * @return list of slides with narratives loaded, sorted by slide number
   */
  @Query(
      "SELECT DISTINCT s FROM Slide s "
          + "LEFT JOIN FETCH s.slideNarratives "
          + "WHERE s.presentation.id = :presentationId "
          + "ORDER BY s.slideNumber")
  List<Slide> findByPresentationIdWithNarratives(@Param("presentationId") UUID presentationId);

  /**
   * Finds all slides belonging to a specific presentation with slide analysis eagerly loaded. This
   * method is used for parallel processing to avoid lazy loading issues.
   *
   * @param presentationId the UUID of the presentation
   * @return list of slides with analysis loaded, sorted by slide number
   */
  @Query(
      "SELECT DISTINCT s FROM Slide s "
          + "LEFT JOIN FETCH s.slideAnalysis "
          + "WHERE s.presentation.id = :presentationId "
          + "ORDER BY s.slideNumber")
  List<Slide> findByPresentationIdWithAnalysis(@Param("presentationId") UUID presentationId);
}
