package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.Slide;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
