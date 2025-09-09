package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.IntroVideo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for managing IntroVideo entities. Provides data access methods for intro videos
 * generated using Google Veo API.
 */
@Repository
public interface IntroVideoRepository extends JpaRepository<IntroVideo, UUID> {

  /**
   * Find the most recent intro video for a presentation.
   *
   * @param presentationId the presentation ID
   * @return the most recent intro video if exists
   */
  @Query(
      "SELECT iv FROM IntroVideo iv WHERE iv.presentation.id = :presentationId ORDER BY iv.createdAt DESC LIMIT 1")
  Optional<IntroVideo> findLatestByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Find all intro videos for a presentation.
   *
   * @param presentationId the presentation ID
   * @return list of intro videos for the presentation
   */
  List<IntroVideo> findByPresentation_IdOrderByCreatedAtDesc(UUID presentationId);

  /**
   * Find intro video by Veo generation ID.
   *
   * @param veoGenerationId the Veo API generation ID
   * @return the intro video if exists
   */
  Optional<IntroVideo> findByVeoGenerationId(String veoGenerationId);

  /**
   * Find all intro videos with a specific status.
   *
   * @param status the generation status
   * @return list of intro videos with the given status
   */
  List<IntroVideo> findByStatus(AvatarGenerationStatusType status);

  /**
   * Find all pending or processing intro videos.
   *
   * @return list of intro videos that are still being generated
   */
  @Query("SELECT iv FROM IntroVideo iv WHERE iv.status IN ('PENDING', 'PROCESSING')")
  List<IntroVideo> findActiveGenerations();

  /**
   * Check if a presentation has a completed intro video.
   *
   * @param presentationId the presentation ID
   * @return true if a completed intro video exists
   */
  @Query(
      "SELECT CASE WHEN COUNT(iv) > 0 THEN true ELSE false END FROM IntroVideo iv WHERE iv.presentation.id = :presentationId AND iv.status = 'COMPLETED'")
  boolean hasCompletedIntroVideo(@Param("presentationId") UUID presentationId);

  /**
   * Delete all intro videos for a presentation.
   *
   * @param presentationId the presentation ID
   */
  void deleteByPresentation_Id(UUID presentationId);
}
