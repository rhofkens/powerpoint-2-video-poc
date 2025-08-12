package ai.bluefields.ppt2video.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity representing metadata for assets stored in R2. Tracks asset location, status, and
 * associated presentation/slide relationships.
 */
@Entity
@Table(
    name = "asset_metadata",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"bucket_name", "object_key"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"presentation", "slide", "presignedUrls"})
@ToString(exclude = {"presentation", "slide", "presignedUrls"})
public class AssetMetadata {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  @JsonBackReference
  private Presentation presentation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_id")
  @JsonBackReference
  private Slide slide;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", nullable = false, length = 50)
  private AssetType assetType;

  @Column(name = "bucket_name", nullable = false, length = 100)
  private String bucketName;

  @Column(name = "object_key", nullable = false, length = 500)
  private String objectKey;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "file_size")
  private Long fileSize;

  @Column(name = "content_type", length = 100)
  private String contentType;

  @Column(name = "checksum")
  private String checksum;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private String metadata;

  @Enumerated(EnumType.STRING)
  @Column(name = "upload_status", nullable = false, length = 50)
  @Builder.Default
  private UploadStatus uploadStatus = UploadStatus.PENDING;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "assetMetadata", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @Builder.Default
  private List<PresignedUrl> presignedUrls = new ArrayList<>();
}
