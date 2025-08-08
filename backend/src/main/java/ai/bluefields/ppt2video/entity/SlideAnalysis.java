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
 * JPA entity representing the AI analysis of an individual slide. This entity stores the multimodal
 * analysis results including visual concepts and key messages.
 */
@Entity
@Table(name = "slide_analysis")
@Data
@EqualsAndHashCode(exclude = "slide")
@ToString(exclude = "slide")
public class SlideAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne
  @JoinColumn(name = "slide_id", nullable = false)
  @JsonBackReference
  private Slide slide;

  @Column(name = "general_message", columnDefinition = "TEXT")
  private String generalMessage;

  @Column(name = "visual_concepts", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String visualConcepts;

  @Column(name = "key_points", columnDefinition = "JSONB")
  @JdbcTypeCode(SqlTypes.JSON)
  private String keyPoints;

  @Column(name = "data_insights", columnDefinition = "TEXT")
  private String dataInsights;

  @Column(name = "transition_context", columnDefinition = "TEXT")
  private String transitionContext;

  @Column(name = "emphasis_level")
  private String emphasisLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "slide_type")
  private SlideType slideType;

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
