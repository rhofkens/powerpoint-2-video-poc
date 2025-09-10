package ai.bluefields.ppt2video.repository;

import ai.bluefields.ppt2video.entity.VideoProviderType;
import ai.bluefields.ppt2video.entity.WebhookEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

  List<WebhookEvent> findByRenderJobId(UUID renderJobId);

  List<WebhookEvent> findByProcessedFalseOrderByCreatedAtAsc();

  List<WebhookEvent> findByProviderAndProcessedFalse(VideoProviderType provider);

  @Query(
      "SELECT we FROM WebhookEvent we WHERE we.processed = false "
          + "AND we.retryCount < :maxRetries ORDER BY we.createdAt ASC")
  List<WebhookEvent> findUnprocessedWithRetryLimit(@Param("maxRetries") int maxRetries);

  @Query(
      "SELECT we FROM WebhookEvent we WHERE we.renderJob.id = :renderJobId "
          + "AND we.eventType = :eventType ORDER BY we.createdAt DESC")
  List<WebhookEvent> findByRenderJobIdAndEventType(
      @Param("renderJobId") UUID renderJobId, @Param("eventType") String eventType);

  @Query("SELECT COUNT(we) FROM WebhookEvent we WHERE we.processed = false")
  long countUnprocessedEvents();

  @Query(
      "SELECT we FROM WebhookEvent we WHERE we.createdAt < :before " + "AND we.processed = false")
  List<WebhookEvent> findStuckEvents(@Param("before") LocalDateTime before);

  void deleteByCreatedAtBefore(LocalDateTime before);
}
