package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "render_job_id")
  private RenderJob renderJob;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VideoProviderType provider;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", nullable = false)
  private String payload;

  @Column @Builder.Default private Boolean processed = false;

  @Column(name = "processed_at")
  private LocalDateTime processedAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "retry_count")
  @Builder.Default
  private Integer retryCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    if (processed == null) {
      processed = false;
    }
    if (retryCount == null) {
      retryCount = 0;
    }
  }

  public void markProcessed() {
    this.processed = true;
    this.processedAt = LocalDateTime.now();
  }

  public void incrementRetryCount() {
    if (this.retryCount == null) {
      this.retryCount = 0;
    }
    this.retryCount++;
  }
}
