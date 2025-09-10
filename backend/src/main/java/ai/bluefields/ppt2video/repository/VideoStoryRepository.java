package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.VideoStory;
import ai.bluefields.ppt2video.entity.VideoStoryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoStoryRepository extends JpaRepository<VideoStory, UUID> {

  List<VideoStory> findByPresentationIdOrderByCreatedAtDesc(UUID presentationId);

  List<VideoStory> findByPresentationIdAndStatus(UUID presentationId, VideoStoryStatus status);

  Optional<VideoStory> findByIdAndPresentationId(UUID id, UUID presentationId);

  @Query(
      "SELECT vs FROM VideoStory vs WHERE vs.presentation.id = :presentationId "
          + "AND vs.status = :status ORDER BY vs.version DESC")
  List<VideoStory> findLatestVersionsByPresentationAndStatus(
      @Param("presentationId") UUID presentationId, @Param("status") VideoStoryStatus status);

  boolean existsByPresentationIdAndName(UUID presentationId, String name);

  @Query("SELECT COUNT(vs) FROM VideoStory vs WHERE vs.presentation.id = :presentationId")
  long countByPresentationId(@Param("presentationId") UUID presentationId);
}
