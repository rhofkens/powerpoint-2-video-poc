package ai.bluefields.ppt2video.dto.avatar;

import ai.bluefields.ppt2video.service.avatar.BatchAvatarVideoOrchestrator.AvatarVideoResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for batch avatar video generation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchAvatarVideoResponse {

  /** The presentation ID */
  private UUID presentationId;

  /** Total number of slides processed */
  private int totalSlides;

  /** Number of successfully generated videos */
  private int successCount;

  /** Number of failed generations */
  private int failedCount;

  /** Number of skipped slides (already had videos) */
  private int skippedCount;

  /** When the batch processing started */
  private LocalDateTime startTime;

  /** When the batch processing ended */
  private LocalDateTime endTime;

  /** Total duration in seconds */
  private long durationSeconds;

  /** Individual results for each slide */
  private List<AvatarVideoResult> results;

  /** Optional error message if batch failed */
  private String errorMessage;
}
