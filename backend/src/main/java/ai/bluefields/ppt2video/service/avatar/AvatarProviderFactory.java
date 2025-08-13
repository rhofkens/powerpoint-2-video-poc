package ai.bluefields.ppt2video.service.avatar;

import ai.bluefields.ppt2video.entity.AvatarProviderType;
import ai.bluefields.ppt2video.exception.ProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for creating and managing avatar video providers. Handles provider selection and
 * instantiation based on configuration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvatarProviderFactory {

  private final List<AvatarProvider> providers;

  /**
   * Get an avatar provider by type.
   *
   * @param providerType the provider type
   * @return the avatar provider
   * @throws ProcessingException if provider is not available
   */
  public AvatarProvider getProvider(AvatarProviderType providerType) {
    Map<AvatarProviderType, AvatarProvider> providerMap =
        providers.stream()
            .collect(Collectors.toMap(AvatarProvider::getProviderType, Function.identity()));

    AvatarProvider provider = providerMap.get(providerType);
    if (provider == null) {
      throw new ProcessingException("Avatar provider not found for type: " + providerType);
    }

    if (!provider.isSupported()) {
      throw new ProcessingException(
          "Avatar provider is not configured or available: " + providerType);
    }

    log.debug("Using avatar provider: {}", providerType);
    return provider;
  }

  /**
   * Get the default avatar provider. Currently defaults to HeyGen if available.
   *
   * @return the default avatar provider
   * @throws ProcessingException if no provider is available
   */
  public AvatarProvider getDefaultProvider() {
    // Try HeyGen first as it's our primary provider
    try {
      return getProvider(AvatarProviderType.HEYGEN);
    } catch (ProcessingException e) {
      log.warn("HeyGen provider not available, looking for alternatives");
    }

    // Fall back to any available provider
    for (AvatarProvider provider : providers) {
      if (provider.isSupported()) {
        log.info("Using fallback avatar provider: {}", provider.getProviderType());
        return provider;
      }
    }

    throw new ProcessingException("No avatar providers are available");
  }

  /**
   * Check if a specific provider type is available.
   *
   * @param providerType the provider type to check
   * @return true if the provider is available and configured
   */
  public boolean isProviderAvailable(AvatarProviderType providerType) {
    try {
      AvatarProvider provider = getProvider(providerType);
      return provider.isSupported();
    } catch (ProcessingException e) {
      return false;
    }
  }

  /**
   * Get all available provider types.
   *
   * @return list of available provider types
   */
  public List<AvatarProviderType> getAvailableProviders() {
    return providers.stream()
        .filter(AvatarProvider::isSupported)
        .map(AvatarProvider::getProviderType)
        .collect(Collectors.toList());
  }
}
