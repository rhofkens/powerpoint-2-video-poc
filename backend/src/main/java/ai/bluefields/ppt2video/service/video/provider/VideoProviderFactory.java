package ai.bluefields.ppt2video.service.video.provider;

import ai.bluefields.ppt2video.entity.VideoProviderType;
import ai.bluefields.ppt2video.exception.VideoProviderException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Factory for selecting appropriate video provider implementations. */
@Component
@Slf4j
public class VideoProviderFactory {

  private final Map<VideoProviderType, VideoProvider> providers;

  public VideoProviderFactory(List<VideoProvider> providerList) {
    this.providers = new EnumMap<>(VideoProviderType.class);

    for (VideoProvider provider : providerList) {
      VideoProviderType type = provider.getProviderType();
      if (type != null) {
        providers.put(type, provider);
        log.info("Registered video provider: {} ({})", provider.getProviderName(), type);
      }
    }

    if (providers.isEmpty()) {
      log.warn("No video providers registered. Video rendering will not be available.");
    }
  }

  /**
   * Gets a video provider by type.
   *
   * @param type The provider type
   * @return The video provider implementation
   * @throws VideoProviderException if provider not found
   */
  public VideoProvider getProvider(VideoProviderType type) {
    VideoProvider provider = providers.get(type);
    if (provider == null) {
      throw new VideoProviderException("Video provider not available: " + type);
    }
    return provider;
  }

  /**
   * Gets a video provider by type, returning Optional.
   *
   * @param type The provider type
   * @return Optional containing the provider if found
   */
  public Optional<VideoProvider> findProvider(VideoProviderType type) {
    return Optional.ofNullable(providers.get(type));
  }

  /**
   * Checks if a provider type is available.
   *
   * @param type The provider type
   * @return true if provider is available
   */
  public boolean isProviderAvailable(VideoProviderType type) {
    return providers.containsKey(type);
  }

  /**
   * Gets the default provider (currently Shotstack).
   *
   * @return The default video provider
   * @throws VideoProviderException if no default provider available
   */
  public VideoProvider getDefaultProvider() {
    return getProvider(VideoProviderType.SHOTSTACK);
  }

  /**
   * Gets all available provider types.
   *
   * @return Set of available provider types
   */
  public VideoProviderType[] getAvailableProviders() {
    return providers.keySet().toArray(new VideoProviderType[0]);
  }
}
