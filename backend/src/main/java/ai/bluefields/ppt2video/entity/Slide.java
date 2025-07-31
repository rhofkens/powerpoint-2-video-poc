package ai.bluefields.ppt2video.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * JPA entity representing an individual slide within a presentation. This entity stores slide
 * content, generated narratives, and paths to associated media files (images, audio, video).
 */
@Entity
@Table(name = "slides")
@Data
@EqualsAndHashCode(exclude = "presentation")
@ToString(exclude = "presentation")
public class Slide {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  private Presentation presentation;

  @Column(name = "slide_number", nullable = false)
  private Integer slideNumber;

  @Column(name = "title")
  private String title;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "image_path")
  private String imagePath;

  @Column(name = "audio_path")
  private String audioPath;

  @Column(name = "video_path")
  private String videoPath;

  @Column(name = "generated_narrative", columnDefinition = "TEXT")
  private String generatedNarrative;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  private ProcessingStatus processingStatus = ProcessingStatus.EXTRACTED;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Enumeration of possible processing states for an individual slide. Tracks the slide's progress
   * through content generation and media creation.
   */
  public enum ProcessingStatus {
    /** Initial state after slide extraction from presentation */
    EXTRACTED,
    /** AI is analyzing the slide content */
    ANALYZING,
    /** Generating speech audio from narrative */
    SPEECH_GENERATING,
    /** Creating video from slide image and audio */
    VIDEO_GENERATING,
    /** All processing successfully completed */
    COMPLETED,
    /** Processing failed at some stage */
    FAILED
  }
}
