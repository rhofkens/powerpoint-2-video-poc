package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.SlideAnalysis;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository interface for SlideAnalysis entities. */
@Repository
public interface SlideAnalysisRepository extends JpaRepository<SlideAnalysis, UUID> {

  /**
   * Find slide analysis by slide ID.
   *
   * @param slideId The slide ID
   * @return Optional containing the slide analysis if found
   */
  Optional<SlideAnalysis> findBySlideId(UUID slideId);
}
