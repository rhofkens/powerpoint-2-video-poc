package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "video_stories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoStory {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id")
  private StoryTemplate template;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "script_data", columnDefinition = "jsonb", nullable = false)
  private String scriptData;

  @Column(name = "duration_seconds", precision = 10, scale = 2)
  private BigDecimal durationSeconds;

  @Column(name = "status", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private VideoStoryStatus status = VideoStoryStatus.DRAFT;

  @Column(nullable = false)
  @Builder.Default
  private Integer version = 1;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "r2_video_url", columnDefinition = "TEXT")
  private String r2VideoUrl;

  @Column(name = "video_file_size")
  private Long videoFileSize;

  @Column(name = "video_duration_seconds", precision = 10, scale = 2)
  private BigDecimal videoDurationSeconds;

  @Column(name = "downloaded_at")
  private LocalDateTime downloadedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (version == null) {
      version = 1;
    }
    if (status == null) {
      status = VideoStoryStatus.DRAFT;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
