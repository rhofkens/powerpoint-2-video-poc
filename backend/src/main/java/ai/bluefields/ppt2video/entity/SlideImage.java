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
 * JPA entity representing images extracted from presentation slides. This entity stores image
 * metadata and file paths for images found within slides.
 */
@Entity
@Table(name = "slide_images")
@Data
@EqualsAndHashCode(exclude = "slide")
@ToString(exclude = "slide")
public class SlideImage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "slide_id", nullable = false)
  private Slide slide;

  @Column(name = "image_path", nullable = false)
  private String imagePath;

  @Column(name = "image_type", nullable = false)
  private String imageType;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @Column(name = "order_in_slide", nullable = false)
  private Integer orderInSlide;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
