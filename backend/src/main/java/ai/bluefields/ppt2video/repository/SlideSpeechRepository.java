package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.SlideSpeech;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for SlideSpeech entities. */
@Repository
public interface SlideSpeechRepository extends JpaRepository<SlideSpeech, UUID> {

  /** Find speech by slide narrative ID. */
  Optional<SlideSpeech> findBySlideNarrativeId(UUID narrativeId);

  /** Find active speech by slide narrative ID. */
  @Query(
      "SELECT s FROM SlideSpeech s WHERE s.slideNarrative.id = :narrativeId AND s.isActive = true")
  Optional<SlideSpeech> findActiveBySlideNarrativeId(UUID narrativeId);

  /** Find all speeches for a narrative to deactivate them. */
  @Query("SELECT s FROM SlideSpeech s WHERE s.slideNarrative.id = :narrativeId")
  List<SlideSpeech> findAllBySlideNarrativeId(UUID narrativeId);

  /** Find all speeches for a presentation. */
  List<SlideSpeech> findByPresentationIdOrderBySlide_SlideNumber(UUID presentationId);

  /** Find speech by slide ID. */
  Optional<SlideSpeech> findBySlideId(UUID slideId);

  /** Find active speech by slide ID. */
  @Query("SELECT s FROM SlideSpeech s WHERE s.slide.id = :slideId AND s.isActive = true")
  Optional<SlideSpeech> findActiveBySlideId(UUID slideId);

  /** Check if speech exists for a narrative. */
  boolean existsBySlideNarrativeId(UUID narrativeId);

  /** Delete all speeches for a presentation. */
  void deleteByPresentationId(UUID presentationId);
}
