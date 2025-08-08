package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository interface for SlideNarrative entities. */
@Repository
public interface SlideNarrativeRepository extends JpaRepository<SlideNarrative, UUID> {

  /**
   * Find narrative for a slide.
   *
   * @param slideId The slide ID
   * @return Optional containing the narrative if found
   */
  Optional<SlideNarrative> findBySlideId(UUID slideId);

  /**
   * Find all narratives for a slide.
   *
   * @param slideId The slide ID
   * @return List of narratives for the slide
   */
  List<SlideNarrative> findBySlideIdOrderByVersionDesc(UUID slideId);

  /**
   * Find active narrative for a slide.
   *
   * @param slideId The slide ID
   * @return Optional containing the active narrative if found
   */
  @Query("SELECT n FROM SlideNarrative n WHERE n.slide.id = :slideId AND n.isActive = true")
  Optional<SlideNarrative> findActiveNarrativeBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find narratives by slide ID and version.
   *
   * @param slideId The slide ID
   * @param version The narrative version
   * @return Optional containing the narrative if found
   */
  Optional<SlideNarrative> findBySlideIdAndVersion(UUID slideId, Integer version);

  /**
   * Find active narrative for a slide.
   *
   * @param slideId The slide ID
   * @return Optional containing the active narrative if found
   */
  Optional<SlideNarrative> findBySlideIdAndIsActiveTrue(UUID slideId);
}
