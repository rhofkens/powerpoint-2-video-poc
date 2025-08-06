package ai.bluefields.ppt2video.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
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

  @Column(name = "upload_timestamp")
  private LocalDateTime uploadTimestamp;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.UPLOADED;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  private ProcessingStatus processingStatus = ProcessingStatus.UPLOADED;

  @Column(name = "parsing_started_at")
  private LocalDateTime parsingStartedAt;

  @Column(name = "parsing_completed_at")
  private LocalDateTime parsingCompletedAt;

  @Column(name = "rendering_started_at")
  private LocalDateTime renderingStartedAt;

  @Column(name = "rendering_completed_at")
  private LocalDateTime renderingCompletedAt;

  @Column(name = "renderer_used")
  private String rendererUsed;

  @Column(name = "rendering_error_message", columnDefinition = "TEXT")
  private String renderingErrorMessage;

  @Column(name = "narrative_style")
  private String narrativeStyle;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "presentation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JsonManagedReference
  private List<Slide> slides = new ArrayList<>();

  @OneToOne(mappedBy = "presentation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  private DeckAnalysis deckAnalysis;

  /** Enumeration of possible upload and parsing states for a presentation. */
  public enum Status {
    /** Initial state when the presentation is uploaded */
    UPLOADED,
    /** Presentation is being parsed to extract content */
    PARSING,
    /** Presentation parsing completed successfully */
    PARSED,
    /** Presentation is being rendered to images */
    RENDERING,
    /** All processing successfully completed */
    COMPLETED,
    /** Processing failed due to errors */
    FAILED
  }

  /**
   * Enumeration of possible processing states for a presentation. Tracks the presentation's
   * progress through the conversion pipeline.
   */
  public enum ProcessingStatus {
    /** Initial state when the presentation is uploaded */
    UPLOADED,
    /** Presentation is being parsed to extract slides */
    PARSING,
    /** Slides have been rendered and ready for analysis */
    RENDERING_READY,
    /** AI is analyzing the presentation content */
    ANALYZING,
    /** Intent analysis for deck and slides is complete */
    INTENT_ANALYSIS_COMPLETE,
    /** Narrative generation is complete */
    NARRATIVE_COMPLETE,
    /** Generating narratives and speech content */
    GENERATING_CONTENT,
    /** All processing successfully completed */
    COMPLETED,
    /** Processing failed at some stage */
    FAILED
  }
}
