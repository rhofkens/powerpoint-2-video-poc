package ai.bluefields.ppt2video.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * JPA entity representing pre-signed URLs for R2 asset access. Manages URL lifecycle, expiration,
 * and access tracking.
 */
@Entity
@Table(
    name = "presigned_urls",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"asset_metadata_id", "url_type", "expires_at"})
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "assetMetadata")
@ToString(exclude = "assetMetadata")
public class PresignedUrl {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "asset_metadata_id", nullable = false)
  @JsonBackReference
  private AssetMetadata assetMetadata;

  @Enumerated(EnumType.STRING)
  @Column(name = "url_type", nullable = false, length = 50)
  private UrlType urlType;

  @Column(name = "presigned_url", nullable = false, columnDefinition = "TEXT")
  private String presignedUrl;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private Boolean isActive = true;

  @Column(name = "access_count")
  @Builder.Default
  private Integer accessCount = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "created_by")
  private String createdBy;
}
