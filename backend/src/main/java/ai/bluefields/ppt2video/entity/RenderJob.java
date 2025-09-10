package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "render_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenderJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_story_id")
  private VideoStory videoStory;

  @Column(name = "type", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private RenderJobType type = RenderJobType.INTRO_ONLY;

  @Column(name = "provider", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private VideoProviderType provider = VideoProviderType.SHOTSTACK;

  @Column(name = "provider_job_id", unique = true)
  private String providerJobId;

  @Column(name = "status", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private RenderJobStatus status = RenderJobStatus.QUEUED;

  @Column @Builder.Default private Integer progress = 0;

  @Column(name = "output_url", columnDefinition = "TEXT")
  private String outputUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "output_metadata", columnDefinition = "jsonb")
  private String outputMetadata;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "composition_data", columnDefinition = "jsonb")
  private String compositionData;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "webhook_url", columnDefinition = "TEXT")
  private String webhookUrl;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (status == null) {
      status = RenderJobStatus.QUEUED;
    }
    if (type == null) {
      type = RenderJobType.INTRO_ONLY;
    }
    if (provider == null) {
      provider = VideoProviderType.SHOTSTACK;
    }
    if (progress == null) {
      progress = 0;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public boolean isTerminal() {
    return status != null && status.isTerminal();
  }
}
