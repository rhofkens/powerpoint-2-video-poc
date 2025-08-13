package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.dto.AvatarVideoRequest;
import ai.bluefields.ppt2video.dto.AvatarVideoResponse;
import ai.bluefields.ppt2video.dto.AvatarVideoStatusDto;
import ai.bluefields.ppt2video.entity.AvatarProviderType;

/**
 * Interface for avatar video generation providers. Defines the contract that all avatar providers
 * must implement.
 */
public interface AvatarProvider {

  /**
   * Create an avatar video from the provided request.
   *
   * @param request the avatar video creation request
   * @param audioUrl the URL of the audio file to use
   * @return response containing the video ID and initial status
   */
  AvatarVideoResponse createAvatarVideo(AvatarVideoRequest request, String audioUrl);

  /**
   * Get the current status of a video generation job.
   *
   * @param providerVideoId the provider's video ID
   * @return status information
   */
  AvatarVideoStatusDto getVideoStatus(String providerVideoId);

  /**
   * Get the provider type for this implementation.
   *
   * @return the provider type
   */
  AvatarProviderType getProviderType();

  /**
   * Check if this provider is currently available and configured.
   *
   * @return true if the provider can be used
   */
  boolean isSupported();

  /**
   * Cancel a video generation job if supported.
   *
   * @param providerVideoId the provider's video ID
   * @return true if cancellation was successful
   */
  default boolean cancelVideo(String providerVideoId) {
    // Default implementation - not all providers support cancellation
    return false;
  }

  /**
   * Get the download URL for a completed video.
   *
   * @param providerVideoId the provider's video ID
   * @return the video download URL, or null if not available
   */
  default String getVideoDownloadUrl(String providerVideoId) {
    // Default implementation - providers may override
    return null;
  }
}
