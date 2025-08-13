package ai.bluefields.ppt2video.dto;

import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarProviderType;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for checking the status of an avatar video generation. Used to poll the provider for updates
 * on video generation progress.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvatarGenerationStatus {

  /** The video ID being tracked */
  private String videoId;

  /** Current status of the generation */
  private AvatarGenerationStatusType status;

  /** Progress percentage (0-100) if available */
  private Integer progress;

  /** URL of the completed video (available when status is COMPLETED) */
  private String videoUrl;

  /** Error message if the generation failed */
  private String errorMessage;

  /** Error code if the generation failed (provider-specific) */
  private String errorCode;

  /** The provider handling this generation */
  private AvatarProviderType providerType;

  /** Timestamp when the status was last updated */
  private LocalDateTime lastUpdatedAt;

  /** Estimated time remaining in seconds (if available) */
  private Integer estimatedSecondsRemaining;

  /** Current processing stage description (provider-specific) */
  private String currentStage;

  /** Duration of the generated video in seconds (available when completed) */
  private Double durationSeconds;

  /** File size in bytes (available when completed) */
  private Long fileSizeBytes;

  /** Thumbnail URL for the video (if available) */
  private String thumbnailUrl;

  /** Whether the video can be downloaded */
  private boolean downloadable;

  /** Expiration time for the video URL (if applicable) */
  private LocalDateTime urlExpiresAt;

  /** Additional status details from the provider */
  private Map<String, Object> metadata;

  /** Number of retry attempts if the generation encountered issues */
  private Integer retryCount;

  /** Whether the generation can be retried */
  private boolean retryable;
}
