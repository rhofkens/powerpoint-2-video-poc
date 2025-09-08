package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarProviderType;
import ai.bluefields.ppt2video.entity.AvatarVideo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for managing AvatarVideo entities. Provides database access for avatar video
 * generation tracking.
 */
@Repository
public interface AvatarVideoRepository extends JpaRepository<AvatarVideo, UUID> {

  /**
   * Find avatar videos by slide ID.
   *
   * @param slideId the slide ID
   * @return list of avatar videos
   */
  @Query("SELECT av FROM AvatarVideo av WHERE av.slide.id = :slideId")
  List<AvatarVideo> findBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find avatar videos by presentation ID.
   *
   * @param presentationId the presentation ID
   * @return list of avatar videos
   */
  @Query("SELECT av FROM AvatarVideo av WHERE av.presentation.id = :presentationId")
  List<AvatarVideo> findByPresentationId(@Param("presentationId") UUID presentationId);

  /**
   * Find avatar videos by status.
   *
   * @param status the generation status
   * @return list of avatar videos
   */
  List<AvatarVideo> findByStatus(AvatarGenerationStatusType status);

  /**
   * Find avatar video by provider video ID.
   *
   * @param providerVideoId the provider's video ID
   * @return optional avatar video
   */
  Optional<AvatarVideo> findByProviderVideoId(String providerVideoId);

  /**
   * Find avatar videos by slide ID and status.
   *
   * @param slideId the slide ID
   * @param status the generation status
   * @return list of avatar videos
   */
  @Query("SELECT av FROM AvatarVideo av WHERE av.slide.id = :slideId AND av.status = :status")
  List<AvatarVideo> findBySlideIdAndStatus(
      @Param("slideId") UUID slideId, @Param("status") AvatarGenerationStatusType status);

  /**
   * Find the most recent avatar video for a slide.
   *
   * @param slideId the slide ID
   * @return optional avatar video
   */
  @Query(
      "SELECT av FROM AvatarVideo av WHERE av.slide.id = :slideId "
          + "ORDER BY av.createdAt DESC LIMIT 1")
  Optional<AvatarVideo> findMostRecentBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find avatar videos by provider type and status.
   *
   * @param providerType the provider type
   * @param status the generation status
   * @return list of avatar videos
   */
  List<AvatarVideo> findByProviderTypeAndStatus(
      AvatarProviderType providerType, AvatarGenerationStatusType status);

  /**
   * Check if a completed avatar video exists for a slide.
   *
   * @param slideId the slide ID
   * @return true if exists
   */
  @Query(
      "SELECT COUNT(av) > 0 FROM AvatarVideo av "
          + "WHERE av.slide.id = :slideId AND av.status = 'COMPLETED'")
  boolean existsCompletedBySlideId(@Param("slideId") UUID slideId);

  /**
   * Find all pending or processing videos older than specified minutes.
   *
   * @param minutes the age threshold in minutes
   * @return list of avatar videos
   */
  @Query(
      value =
          "SELECT * FROM avatar_videos "
              + "WHERE status IN ('PENDING', 'PROCESSING') "
              + "AND created_at < CURRENT_TIMESTAMP - CAST(:minutes || ' minutes' AS INTERVAL)",
      nativeQuery = true)
  List<AvatarVideo> findStuckVideos(@Param("minutes") int minutes);

  /**
   * Find completed avatar videos by slide ID.
   *
   * @param slideId the slide ID
   * @return list of completed avatar videos
   */
  default List<AvatarVideo> findBySlideIdAndStatusCompleted(UUID slideId) {
    return findBySlideIdAndStatus(slideId, AvatarGenerationStatusType.COMPLETED);
  }

  /**
   * Find all avatar videos for a presentation ordered by creation date. Used for preflight checks
   * to get the latest video per slide.
   *
   * @param presentationId the presentation ID
   * @return list of avatar videos ordered by creation date (newest first)
   */
  @Query(
      "SELECT av FROM AvatarVideo av WHERE av.presentation.id = :presentationId ORDER BY av.createdAt DESC")
  List<AvatarVideo> findByPresentationIdOrderByCreatedAtDesc(
      @Param("presentationId") UUID presentationId);
}
