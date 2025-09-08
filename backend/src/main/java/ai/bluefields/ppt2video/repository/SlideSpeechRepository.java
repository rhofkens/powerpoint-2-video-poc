package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.SlideSpeech;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for SlideSpeech entities. */
@Repository
public interface SlideSpeechRepository extends JpaRepository<SlideSpeech, UUID> {

  /** Find speech by slide narrative ID. */
  Optional<SlideSpeech> findBySlideNarrativeId(UUID narrativeId);

  /** Find active speech by slide narrative ID. */
  @Query(
      "SELECT s FROM SlideSpeech s WHERE s.slideNarrative.id = :narrativeId AND s.isActive = true")
  Optional<SlideSpeech> findActiveBySlideNarrativeId(@Param("narrativeId") UUID narrativeId);

  /** Find all speeches for a narrative to deactivate them. */
  @Query("SELECT s FROM SlideSpeech s WHERE s.slideNarrative.id = :narrativeId")
  List<SlideSpeech> findAllBySlideNarrativeId(@Param("narrativeId") UUID narrativeId);

  /** Find all speeches for a presentation. */
  @Query(
      "SELECT s FROM SlideSpeech s WHERE s.presentation.id = :presentationId ORDER BY s.slide.slideNumber")
  List<SlideSpeech> findByPresentationIdOrderBySlide_SlideNumber(
      @Param("presentationId") UUID presentationId);

  /** Find speech by slide ID. */
  @Query("SELECT s FROM SlideSpeech s WHERE s.slide.id = :slideId")
  Optional<SlideSpeech> findBySlideId(@Param("slideId") UUID slideId);

  /** Find active speech by slide ID. */
  @Query("SELECT s FROM SlideSpeech s WHERE s.slide.id = :slideId AND s.isActive = true")
  Optional<SlideSpeech> findActiveBySlideId(@Param("slideId") UUID slideId);

  /** Find all active speeches by slide ID. */
  @Query(
      "SELECT s FROM SlideSpeech s WHERE s.slide.id = :slideId AND s.isActive = true ORDER BY s.createdAt DESC")
  List<SlideSpeech> findAllActiveBySlideIdOrderByCreatedAtDesc(@Param("slideId") UUID slideId);

  /** Check if speech exists for a narrative. */
  boolean existsBySlideNarrativeId(UUID narrativeId);

  /** Delete all speeches for a presentation. */
  @Modifying
  @Query("DELETE FROM SlideSpeech s WHERE s.presentation.id = :presentationId")
  void deleteByPresentationId(@Param("presentationId") UUID presentationId);

  /** Find all active speeches for a presentation. */
  @Query(
      "SELECT s FROM SlideSpeech s WHERE s.presentation.id = :presentationId AND s.isActive = true")
  List<SlideSpeech> findActiveSpeechesByPresentationId(
      @Param("presentationId") UUID presentationId);
}
