package ai.bluefields.ppt2video.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity representing generated speech data for a slide narrative. Stores the audio file path,
 * timing information, and generation metadata.
 */
@Entity
@Table(name = "slide_speeches")
@Data
@EqualsAndHashCode(exclude = {"slide", "presentation", "slideNarrative"})
@ToString(exclude = {"slide", "presentation", "slideNarrative"})
public class SlideSpeech {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "presentation_id", nullable = false)
  @JsonBackReference
  private Presentation presentation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_id", nullable = false)
  @JsonBackReference
  private Slide slide;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_narrative_id", nullable = false)
  @JsonBackReference
  private SlideNarrative slideNarrative;

  @Column(name = "audio_file_path", nullable = false)
  private String audioFilePath;

  @Column(name = "voice_id", nullable = false)
  private String voiceId;

  @Column(name = "voice_style")
  private String voiceStyle;

  @Column(name = "model_used", nullable = false)
  private String modelUsed;

  @Column(name = "duration_seconds")
  private Double durationSeconds;

  @Column(name = "timing_data", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String timingData;

  @Column(name = "request_id")
  private String requestId;

  @Column(name = "output_format")
  private String outputFormat;

  @Column(name = "generation_metadata", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String generationMetadata;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "transition_included")
  private Boolean transitionIncluded = false;

  @Column(name = "transition_skipped_reason")
  private String transitionSkippedReason;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
