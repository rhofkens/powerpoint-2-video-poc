package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA entity representing a PowerPoint presentation. This entity stores metadata about uploaded
 * presentations and tracks their processing status through the conversion pipeline.
 */
@Entity
@Table(name = "presentations")
@Data
@EqualsAndHashCode(exclude = "slides")
@ToString(exclude = "slides")
public class Presentation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String title;

  @Column(name = "original_filename")
  private String originalFilename;

  @Column(name = "file_path")
  private String filePath;

  @Column(name = "file_size")
  private Long fileSize;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

  @Column(name = "narrative_style")
  private String narrativeStyle;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "presentation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private List<Slide> slides = new ArrayList<>();

  /**
   * Enumeration of possible processing states for a presentation. Tracks the presentation's
   * progress through the conversion pipeline.
   */
  public enum ProcessingStatus {
    /** Initial state when the presentation is uploaded */
    UPLOADED,
    /** Presentation is being parsed to extract slides */
    PARSING,
    /** AI is analyzing the presentation content */
    ANALYZING,
    /** Generating narratives and speech content */
    GENERATING_CONTENT,
    /** All processing successfully completed */
    COMPLETED,
    /** Processing failed at some stage */
    FAILED
  }
}
