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

  /**
   * Find all active narratives for a presentation ordered by slide number. Used for optimization
   * processes.
   *
   * @param presentationId The presentation ID
   * @param isActive Whether to filter for active narratives only
   * @return List of narratives ordered by slide number
   */
  @Query(
      "SELECT DISTINCT n FROM SlideNarrative n "
          + "JOIN FETCH n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND n.isActive = :isActive "
          + "ORDER BY s.slideNumber")
  List<SlideNarrative> findBySlide_Presentation_IdAndIsActiveOrderBySlide_SlideNumber(
      @Param("presentationId") UUID presentationId, @Param("isActive") Boolean isActive);

  /**
   * Find active narrative by presentation ID and slide number. Used to get the next slide's
   * narrative for transition redundancy checking.
   *
   * @param presentationId The presentation ID
   * @param slideNumber The slide number
   * @return Optional containing the active narrative for that slide number
   */
  @Query(
      "SELECT n FROM SlideNarrative n "
          + "JOIN n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND s.slideNumber = :slideNumber "
          + "AND n.isActive = true")
  Optional<SlideNarrative> findByPresentationIdAndSlideNumber(
      @Param("presentationId") UUID presentationId, @Param("slideNumber") int slideNumber);

  /**
   * Find all enhanced narratives for a presentation. Returns narratives where
   * enhanced_narrative_text is not null.
   *
   * @param presentationId The presentation ID
   * @return List of enhanced narratives
   */
  @Query(
      "SELECT n FROM SlideNarrative n "
          + "JOIN n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND n.isActive = true "
          + "AND n.enhancedNarrativeText IS NOT NULL "
          + "ORDER BY s.slideNumber")
  List<SlideNarrative> findEnhancedNarrativesByPresentationId(
      @Param("presentationId") UUID presentationId);

  /**
   * Find all narratives that have not been enhanced yet for a presentation.
   *
   * @param presentationId The presentation ID
   * @return List of unenhanced narratives
   */
  @Query(
      "SELECT n FROM SlideNarrative n "
          + "JOIN n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND n.isActive = true "
          + "AND n.enhancedNarrativeText IS NULL "
          + "ORDER BY s.slideNumber")
  List<SlideNarrative> findUnenhancedNarrativesByPresentationId(
      @Param("presentationId") UUID presentationId);

  /**
   * Count enhanced narratives for a presentation.
   *
   * @param presentationId The presentation ID
   * @return Count of enhanced narratives
   */
  @Query(
      "SELECT COUNT(n) FROM SlideNarrative n "
          + "JOIN n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND n.isActive = true "
          + "AND n.enhancedNarrativeText IS NOT NULL")
  long countEnhancedNarrativesByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Find all active narratives for a presentation. Used for preflight checks to validate narrative
   * presence.
   *
   * @param presentationId The presentation ID
   * @return List of active narratives
   */
  @Query(
      "SELECT n FROM SlideNarrative n "
          + "JOIN n.slide s "
          + "WHERE s.presentation.id = :presentationId "
          + "AND n.isActive = true")
  List<SlideNarrative> findActiveNarrativesByPresentationId(
      @Param("presentationId") UUID presentationId);
}
