package ai.bluefields.ppt2video.service.avatar.providers;

import ai.bluefields.ppt2video.dto.AvatarVideoRequest;
import ai.bluefields.ppt2video.dto.AvatarVideoResponse;
import ai.bluefields.ppt2video.dto.AvatarVideoStatusDto;
import ai.bluefields.ppt2video.entity.AvatarGenerationStatusType;
import ai.bluefields.ppt2video.entity.AvatarProviderType;
import ai.bluefields.ppt2video.exception.ProcessingException;
import ai.bluefields.ppt2video.service.avatar.AvatarProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * HeyGen implementation of the AvatarProvider interface. Handles avatar video generation using
 * HeyGen's API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeyGenAvatarProvider implements AvatarProvider {

  private final HeyGenConfiguration configuration;
  private final HeyGenClient heyGenClient;
  private final ObjectMapper objectMapper;

  @Override
  public AvatarVideoResponse createAvatarVideo(AvatarVideoRequest request, String audioUrl) {
    try {
      log.info(
          "Creating HeyGen avatar video for slide: {} with audio: {}",
          request.getSlideId(),
          audioUrl);

      // Validate audio URL
      if (audioUrl == null || audioUrl.isEmpty()) {
        throw new ProcessingException("Audio URL is required for HeyGen video generation");
      }

      // Use provided values or defaults from configuration
      String avatarId =
          request.getAvatarId() != null
              ? request.getAvatarId()
              : configuration.getAvatar().getDefaultId();

      log.debug("Using avatar ID: {}", avatarId);

      String backgroundColor =
          request.getBackgroundColor() != null
              ? request.getBackgroundColor()
              : configuration.getAvatar().getBackgroundColor();

      // Create HeyGen request based on avatar type
      HeyGenVideoRequest heyGenRequest;

      // Check if this is a talking photo (custom avatar) by looking for TP- prefix
      if (avatarId != null && avatarId.startsWith("TP-")) {
        // Extract the actual talking photo ID by removing the TP- prefix
        String talkingPhotoId = avatarId.substring(3);
        log.info("Detected talking photo with ID: {}", talkingPhotoId);

        // Create talking photo request with specified settings
        heyGenRequest =
            HeyGenVideoRequest.withTalkingPhotoAndAudioUrl(
                talkingPhotoId, audioUrl, backgroundColor);
      } else {
        // Regular avatar request
        log.debug("Using regular avatar: {}", avatarId);
        heyGenRequest = HeyGenVideoRequest.withAudioUrl(avatarId, audioUrl, backgroundColor);
      }

      // Set test mode if configured
      heyGenRequest.setTest(configuration.getVideo().isTestMode());

      // Set webhook if configured
      if (configuration.getVideo().isCallbackEnabled()
          && configuration.getVideo().getWebhookUrl() != null) {
        heyGenRequest.setWebhookUrl(configuration.getVideo().getWebhookUrl());
      }

      // Store request payload for debugging
      String requestPayload = objectMapper.writeValueAsString(heyGenRequest);
      log.debug("HeyGen request payload: {}", requestPayload);

      // Call HeyGen API
      HeyGenVideoResponse heyGenResponse = heyGenClient.createVideo(heyGenRequest);

      if (!heyGenResponse.isSuccess()) {
        String errorMessage =
            heyGenResponse.getError() != null
                ? heyGenResponse.getError().getMessage()
                : heyGenResponse.getMessage();
        throw new ProcessingException("HeyGen video creation failed: " + errorMessage);
      }

      // After successful creation, video is in PENDING/PROCESSING state
      AvatarGenerationStatusType status = AvatarGenerationStatusType.PENDING;

      // Build response
      return AvatarVideoResponse.builder()
          .presentationId(request.getPresentationId())
          .slideId(request.getSlideId())
          .providerType(AvatarProviderType.HEYGEN)
          .providerVideoId(heyGenResponse.getVideoId())
          .status(status)
          .avatarId(avatarId)
          .backgroundColor(backgroundColor)
          .audioUrl(audioUrl)
          .startedAt(LocalDateTime.now())
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .metadata(requestPayload)
          .build();

    } catch (Exception e) {
      log.error("Failed to create HeyGen avatar video", e);
      throw new ProcessingException("Failed to create avatar video: " + e.getMessage());
    }
  }

  @Override
  public AvatarVideoStatusDto getVideoStatus(String providerVideoId) {
    try {
      log.debug("Getting HeyGen video status for: {}", providerVideoId);

      HeyGenVideoResponse response = heyGenClient.getVideoStatus(providerVideoId);

      if (!response.isSuccess()) {
        log.warn("Failed to get HeyGen video status: {}", response.getMessage());
        return AvatarVideoStatusDto.builder()
            .status(AvatarGenerationStatusType.FAILED)
            .errorMessage(response.getMessage())
            .build();
      }

      HeyGenVideoResponse.VideoData data = response.getData();
      AvatarGenerationStatusType status = mapHeyGenStatus(data.getStatus());

      // Calculate progress percentage based on status
      Integer progress = calculateProgress(data.getStatus(), data.getEta());

      // Convert timestamps if available
      LocalDateTime completedAt = null;
      if ("completed".equalsIgnoreCase(data.getStatus()) && data.getUpdatedAt() != null) {
        completedAt =
            LocalDateTime.ofInstant(
                Instant.ofEpochSecond(data.getUpdatedAt()), ZoneId.systemDefault());
      }

      return AvatarVideoStatusDto.builder()
          .status(status)
          .progressPercentage(progress)
          .videoUrl(data.getVideoUrl())
          .durationSeconds(data.getDuration())
          .completedAt(completedAt)
          .build();

    } catch (Exception e) {
      log.error("Failed to get HeyGen video status for: {}", providerVideoId, e);
      return AvatarVideoStatusDto.builder()
          .status(AvatarGenerationStatusType.FAILED)
          .errorMessage("Failed to get video status: " + e.getMessage())
          .build();
    }
  }

  @Override
  public AvatarProviderType getProviderType() {
    return AvatarProviderType.HEYGEN;
  }

  @Override
  public boolean isSupported() {
    boolean configured = configuration.isConfigured();
    if (!configured) {
      log.debug("HeyGen provider is not configured (missing API key)");
    }
    return configured;
  }

  @Override
  public boolean cancelVideo(String providerVideoId) {
    try {
      return heyGenClient.cancelVideo(providerVideoId);
    } catch (Exception e) {
      log.error("Failed to cancel HeyGen video: {}", providerVideoId, e);
      return false;
    }
  }

  @Override
  public String getVideoDownloadUrl(String providerVideoId) {
    try {
      HeyGenVideoResponse response = heyGenClient.getVideoStatus(providerVideoId);
      if (response.isSuccess() && response.getData() != null) {
        return response.getData().getVideoUrl();
      }
    } catch (Exception e) {
      log.error("Failed to get video download URL for: {}", providerVideoId, e);
    }
    return null;
  }

  /**
   * Map HeyGen status to our internal status.
   *
   * @param heyGenStatus the HeyGen status string
   * @return the mapped status
   */
  private AvatarGenerationStatusType mapHeyGenStatus(String heyGenStatus) {
    if (heyGenStatus == null) {
      return AvatarGenerationStatusType.PENDING;
    }

    return switch (heyGenStatus.toLowerCase()) {
      case "pending", "queued", "waiting" -> AvatarGenerationStatusType.PENDING;
      case "processing", "in_progress", "rendering" -> AvatarGenerationStatusType.PROCESSING;
      case "completed", "success", "done" -> AvatarGenerationStatusType.COMPLETED;
      case "failed", "error" -> AvatarGenerationStatusType.FAILED;
      case "cancelled", "canceled" -> AvatarGenerationStatusType.CANCELLED;
      default -> {
        log.warn("Unknown HeyGen status: {}, mapping to PROCESSING", heyGenStatus);
        yield AvatarGenerationStatusType.PROCESSING;
      }
    };
  }

  /**
   * Calculate progress percentage based on status and ETA.
   *
   * @param status the HeyGen status
   * @param eta estimated time to completion in seconds
   * @return progress percentage (0-100)
   */
  private Integer calculateProgress(String status, Integer eta) {
    if (status == null) {
      return 0;
    }

    return switch (status.toLowerCase()) {
      case "pending", "queued" -> 0;
      case "processing", "in_progress", "rendering" -> {
        // If we have an ETA, estimate progress (assume max 5 minutes)
        if (eta != null && eta > 0) {
          int maxTime = 300; // 5 minutes max
          int elapsed = maxTime - eta;
          yield Math.max(10, Math.min(90, (elapsed * 100) / maxTime));
        }
        yield 50; // Default to 50% if no ETA
      }
      case "completed", "success", "done" -> 100;
      case "failed", "error", "cancelled", "canceled" -> 0;
      default -> 0;
    };
  }
}
