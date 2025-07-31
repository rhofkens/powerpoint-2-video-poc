package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.Presentation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Presentation} entities. Provides CRUD operations and custom
 * query methods for managing presentations in the database.
 */
@Repository
public interface PresentationRepository extends JpaRepository<Presentation, UUID> {
  // Custom query methods can be added here when needed
}
