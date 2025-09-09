package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing an intro video generation job using Google Veo API. Tracks the creation and
 * status of AI-generated intro videos for presentations.
 */
@Entity
@Table(name = "intro_videos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntroVideo {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @Column(name = "veo_generation_id")
  private String veoGenerationId;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private AvatarGenerationStatusType status;

  @Column(name = "prompt_text", columnDefinition = "TEXT")
  private String promptText;

  @Column(name = "negative_prompt", columnDefinition = "TEXT")
  private String negativePrompt;

  @Column(name = "color_palette", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String colorPalette;

  @Column(name = "veo_model_version")
  private String veoModelVersion;

  @Column(name = "aspect_ratio")
  private String aspectRatio;

  @Column(name = "resolution")
  private String resolution;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(name = "google_video_url", columnDefinition = "TEXT")
  private String googleVideoUrl;

  @Column(name = "published_url", columnDefinition = "TEXT")
  private String publishedUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "r2_asset_id")
  private AssetMetadata r2Asset;

  @Column(name = "generation_metadata", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String generationMetadata;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "prompt_generation_method", length = 50)
  private String promptGenerationMethod;

  @Column(name = "prompt_generation_time_ms")
  private Long promptGenerationTimeMs;

  @Column(name = "prompt_text_full", columnDefinition = "TEXT")
  private String promptTextFull;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  // Convenience methods for accessing foreign key IDs
  public UUID getPresentationId() {
    return presentation != null ? presentation.getId() : null;
  }

  public UUID getR2AssetId() {
    return r2Asset != null ? r2Asset.getId() : null;
  }
}
