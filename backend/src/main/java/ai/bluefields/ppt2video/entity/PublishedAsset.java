package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "published_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedAsset {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_metadata_id", nullable = false)
  private AssetMetadata assetMetadata;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "video_story_id")
  private VideoStory videoStory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VideoProviderType provider;

  @Column(name = "provider_asset_id")
  private String providerAssetId;

  @Column(name = "provider_url", columnDefinition = "TEXT", nullable = false)
  private String providerUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "publish_status", nullable = false)
  @Builder.Default
  private PublishStatus publishStatus = PublishStatus.PENDING;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String metadata;

  @Column(name = "published_at")
  private LocalDateTime publishedAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "scheduled_deletion_at")
  private LocalDateTime scheduledDeletionAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    if (publishStatus == null) {
      publishStatus = PublishStatus.PENDING;
    }
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public boolean isPendingDeletion() {
    return scheduledDeletionAt != null
        && deletedAt == null
        && LocalDateTime.now().isAfter(scheduledDeletionAt);
  }
}
