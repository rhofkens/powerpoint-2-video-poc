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
 * JPA entity representing the AI analysis of an entire presentation deck. This entity stores the
 * overall story arc and communication intent derived from AI analysis.
 */
@Entity
@Table(name = "deck_analysis")
@Data
@EqualsAndHashCode(exclude = "presentation")
@ToString(exclude = "presentation")
public class DeckAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne
  @JoinColumn(name = "presentation_id", nullable = false)
  @JsonBackReference
  private Presentation presentation;

  @Column(name = "overall_story", columnDefinition = "TEXT")
  private String overallStory;

  @Column(name = "communication_intent", columnDefinition = "TEXT")
  private String communicationIntent;

  @Column(name = "key_themes", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String keyThemes;

  @Column(name = "target_audience")
  private String targetAudience;

  @Column(name = "tone")
  private String tone;

  @Column(name = "presentation_title", length = 500)
  private String presentationTitle;

  @Column(name = "presentation_author", length = 500)
  private String presentationAuthor;

  @Column(name = "analysis_metadata", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String analysisMetadata;

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
}
