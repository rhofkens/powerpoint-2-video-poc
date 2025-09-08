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
 * JPA entity representing the AI-generated narrative for a slide. This entity stores the narrative
 * content along with emotional indicators and avatar rendering instructions.
 */
@Entity
@Table(name = "slide_narratives")
@Data
@EqualsAndHashCode(exclude = "slide")
@ToString(exclude = "slide")
public class SlideNarrative {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "slide_id", nullable = false)
  @JsonBackReference
  private Slide slide;

  @Column(name = "narrative_text", columnDefinition = "TEXT", nullable = false)
  private String narrativeText;

  @Column(name = "enhanced_narrative_text", columnDefinition = "TEXT")
  private String enhancedNarrativeText;

  @Column(name = "enhancement_metadata", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String enhancementMetadata;

  @Column(name = "enhancement_timestamp")
  private LocalDateTime enhancementTimestamp;

  @Column(name = "enhancement_model_used")
  private String enhancementModelUsed;

  @Column(name = "emotion_indicators", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String emotionIndicators;

  @Column(name = "avatar_instructions", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String avatarInstructions;

  @Column(name = "speech_markers", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String speechMarkers;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Column(name = "target_duration_seconds")
  private Integer targetDurationSeconds;

  @Column(name = "transition_phrase", columnDefinition = "TEXT")
  private String transitionPhrase;

  @Column(name = "emphasis_words", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String emphasisWords;

  @Column(name = "version")
  private Integer version = 1;

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "generation_metadata", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String generationMetadata;

  @Column(name = "model_used")
  private String modelUsed;

  @Column(name = "prompt_version")
  private String promptVersion;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Gets the effective narrative text for audio generation. Returns the enhanced narrative if
   * available, otherwise falls back to the original narrative text.
   *
   * @return The narrative text to use for TTS generation
   */
  @Transient
  public String getEffectiveNarrativeForAudio() {
    return enhancedNarrativeText != null && !enhancedNarrativeText.isEmpty()
        ? enhancedNarrativeText
        : narrativeText;
  }

  /**
   * Checks if this narrative has been emotionally enhanced.
   *
   * @return true if enhanced narrative text exists, false otherwise
   */
  @Transient
  public boolean hasEnhancement() {
    return enhancedNarrativeText != null && !enhancedNarrativeText.isEmpty();
  }

  /**
   * Gets the original narrative text for subtitles and translation. Always returns the original
   * narrative without emotional enhancements.
   *
   * @return The original narrative text
   */
  @Transient
  public String getNarrativeForSubtitles() {
    return narrativeText;
  }
}
