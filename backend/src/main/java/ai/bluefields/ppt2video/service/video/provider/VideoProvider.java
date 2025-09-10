package ai.bluefields.ppt2video.service.video.provider;

import ai.bluefields.ppt2video.dto.video.*;
import ai.bluefields.ppt2video.entity.VideoProviderType;

/**
 * Interface for video rendering providers. Implementations handle provider-specific operations for
 * video composition and rendering.
 */
public interface VideoProvider {

  /** Gets the provider type identifier. */
  VideoProviderType getProviderType();

  /** Gets the human-readable provider name. */
  String getProviderName();

  /** Indicates if this provider supports asset hosting. */
  boolean supportsAssetHosting();

  /**
   * Uploads an asset to the provider's storage.
   *
   * @param request Asset upload request containing source URL and metadata
   * @return Result containing provider asset ID and URL
   * @throws VideoProviderException if upload fails
   */
  AssetUploadResult uploadAsset(AssetUploadRequest request);

  /**
   * Deletes an asset from the provider's storage.
   *
   * @param assetId Provider-specific asset identifier
   * @throws VideoProviderException if deletion fails
   */
  void deleteAsset(String assetId);

  /**
   * Creates a video composition from the provided request.
   *
   * @param request Composition request with timeline and assets
   * @return Video composition ready for rendering
   * @throws VideoProviderException if composition creation fails
   */
  VideoComposition createComposition(CompositionRequest request);

  /**
   * Validates a video composition before rendering.
   *
   * @param composition Video composition to validate
   * @return Validated composition
   * @throws VideoProviderException if composition is invalid
   */
  VideoComposition validateComposition(VideoComposition composition);

  /**
   * Submits a video composition for rendering.
   *
   * @param composition Video composition to render
   * @return Render response with job ID
   * @throws VideoProviderException if render submission fails
   */
  RenderResponse submitRender(VideoComposition composition);

  /**
   * Checks the status of a rendering job.
   *
   * @param renderId Provider-specific render job ID
   * @return Current render status
   * @throws VideoProviderException if status check fails
   */
  RenderStatus checkRenderStatus(String renderId);

  /**
   * Cancels an ongoing render job.
   *
   * @param renderId Provider-specific render job ID
   * @throws VideoProviderException if cancellation fails
   */
  void cancelRender(String renderId);

  /** Gets the maximum supported render duration in seconds. */
  default int getMaxRenderDuration() {
    return 3600; // 1 hour default
  }

  /** Gets the supported output formats. */
  default String[] getSupportedFormats() {
    return new String[] {"mp4"};
  }
}
