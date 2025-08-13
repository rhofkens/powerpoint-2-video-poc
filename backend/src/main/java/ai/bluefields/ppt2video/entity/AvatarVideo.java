package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing an avatar video generation job. Tracks the creation and status of avatar
 * videos for slides.
 */
@Entity
@Table(name = "avatar_videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarVideo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_id", nullable = false)
  private Slide slide;

  @Column(name = "provider_type", nullable = false)
  @Enumerated(EnumType.STRING)
  private AvatarProviderType providerType;

  @Column(name = "provider_video_id")
  private String providerVideoId;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private AvatarGenerationStatusType status;

  @Column(name = "avatar_id")
  private String avatarId;

  @Column(name = "background_color")
  private String backgroundColor;

  @Column(name = "audio_url", columnDefinition = "TEXT")
  private String audioUrl;

  @Column(name = "video_url", columnDefinition = "TEXT")
  private String videoUrl;

  @Column(name = "r2_asset_id")
  private UUID r2AssetId;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(name = "progress_percentage")
  private Integer progressPercentage;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "request_payload", columnDefinition = "TEXT")
  private String requestPayload;

  @Column(name = "response_payload", columnDefinition = "TEXT")
  private String responsePayload;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "published_url", length = 2048)
  private String publishedUrl;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "created_by")
  private String createdBy;
}
