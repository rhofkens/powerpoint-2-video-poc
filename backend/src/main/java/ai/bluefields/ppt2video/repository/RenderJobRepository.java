package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.RenderJob;
import ai.bluefields.ppt2video.entity.RenderJobStatus;
import ai.bluefields.ppt2video.entity.RenderJobType;
import ai.bluefields.ppt2video.entity.VideoStory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RenderJobRepository extends JpaRepository<RenderJob, UUID> {

  List<RenderJob> findByPresentationIdOrderByCreatedAtDesc(UUID presentationId);

  List<RenderJob> findByVideoStoryIdOrderByCreatedAtDesc(UUID videoStoryId);

  List<RenderJob> findByStatus(RenderJobStatus status);

  Optional<RenderJob> findByProviderJobId(String providerJobId);

  @Query(
      "SELECT rj FROM RenderJob rj WHERE rj.presentation.id = :presentationId "
          + "AND rj.type = :type ORDER BY rj.createdAt DESC")
  List<RenderJob> findByPresentationIdAndType(
      @Param("presentationId") UUID presentationId, @Param("type") RenderJobType type);

  @Query(
      "SELECT rj FROM RenderJob rj WHERE rj.status IN :statuses "
          + "AND rj.createdAt < :before ORDER BY rj.createdAt ASC")
  List<RenderJob> findStuckJobs(
      @Param("statuses") List<RenderJobStatus> statuses, @Param("before") LocalDateTime before);

  @Query(
      "SELECT rj FROM RenderJob rj WHERE rj.presentation.id = :presentationId "
          + "AND rj.status = :status AND rj.type = :type "
          + "ORDER BY rj.createdAt DESC LIMIT 1")
  Optional<RenderJob> findLatestByPresentationAndStatusAndType(
      @Param("presentationId") UUID presentationId,
      @Param("status") RenderJobStatus status,
      @Param("type") RenderJobType type);

  @Query("SELECT COUNT(rj) FROM RenderJob rj WHERE rj.status = :status")
  long countByStatus(@Param("status") RenderJobStatus status);

  @Query(
      "SELECT COUNT(rj) FROM RenderJob rj WHERE rj.presentation.id = :presentationId "
          + "AND rj.status IN ('QUEUED', 'PROCESSING')")
  long countActiveJobsByPresentation(@Param("presentationId") UUID presentationId);

  Optional<RenderJob> findTopByVideoStoryOrderByCreatedAtDesc(VideoStory videoStory);
}
