package ai.bluefields.ppt2video.service.video.provider.shotstack;

import ai.bluefields.ppt2video.config.ShotstackConfig;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.service.R2AssetService;
import ai.bluefields.ppt2video.service.video.PresignedUrlValidator;
import ai.bluefields.ppt2video.service.video.ShotstackAssetPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for building Shotstack-specific video compositions. Handles timeline construction with
 * intro and slide sections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShotstackCompositionService {

  private final ShotstackConfig shotstackConfig;
  private final ObjectMapper objectMapper;
  private final R2AssetService r2AssetService;
  private final PresignedUrlValidator urlValidator;
  private final ShotstackAssetPublisher assetPublisher;

  @Value("${shotstack.assets.mode:r2-direct}")
  private String assetMode;

  /** Builds the full timeline composition combining intro and slides. */
  public JsonNode buildFullTimeline(Presentation presentation, IntroVideo introVideo) {
    log.info(
        "Building full timeline for presentation: {} using asset mode: {}",
        presentation.getId(),
        assetMode);

    ObjectNode edit = objectMapper.createObjectNode();
    ObjectNode timeline = objectMapper.createObjectNode();
    ArrayNode tracks = objectMapper.createArrayNode();

    // Prepare asset URLs based on mode
    Map<String, String> assetUrls = prepareAssetUrls(presentation, introVideo);

    // Build intro composition (8 seconds)
    List<ObjectNode> introTracks = buildIntroComposition(presentation, introVideo, assetUrls);

    // Build slide composition (empty for now)
    List<ObjectNode> slideTracks = buildSlideComposition(presentation, assetUrls);

    // Combine tracks (intro first, then slides)
    // Tracks are layered in reverse order (last track is bottom layer)
    slideTracks.forEach(tracks::add);
    introTracks.forEach(tracks::add);

    timeline.put("background", "#000000");
    timeline.set("tracks", tracks);

    edit.set("timeline", timeline);
    edit.set("output", buildOutput());

    return edit;
  }

  /**
   * Builds the intro composition (8 seconds). Includes intro video, title overlays, and lower
   * thirds.
   */
  public List<ObjectNode> buildIntroComposition(
      Presentation presentation, IntroVideo introVideo, Map<String, String> assetUrls) {
    log.info("Building intro composition for presentation: {}", presentation.getId());

    List<ObjectNode> tracks = new ArrayList<>();

    // Track 1: Title text overlay (top layer)
    tracks.add(buildTitleTrack(presentation.getTitle()));

    // Track 2: Subtitle text overlay (using target audience from deck analysis if available)
    String subtitle = "A Professional Presentation";
    if (presentation.getDeckAnalysis() != null
        && presentation.getDeckAnalysis().getTargetAudience() != null) {
      subtitle = "For " + presentation.getDeckAnalysis().getTargetAudience();
    }
    tracks.add(buildSubtitleTrack(subtitle));

    // Track 3: Lower third in animation
    tracks.add(buildLowerThirdInTrack());

    // Track 4: Lower third out animation
    tracks.add(buildLowerThirdOutTrack());

    // Track 5: Intro video with luma transition (bottom layer)
    String introVideoUrl = assetUrls.getOrDefault("intro-video", introVideo.getPublishedUrl());
    tracks.add(buildIntroVideoTrack(introVideoUrl));

    return tracks;
  }

  // Keep old signature for backward compatibility
  public List<ObjectNode> buildIntroComposition(Presentation presentation, IntroVideo introVideo) {
    return buildIntroComposition(presentation, introVideo, new HashMap<>());
  }

  /** Builds the slide composition. Currently empty placeholder for Phase 3 implementation. */
  public List<ObjectNode> buildSlideComposition(
      Presentation presentation, Map<String, String> assetUrls) {
    log.info("Building slide composition for presentation: {} (placeholder)", presentation.getId());

    // Empty for Phase 2 - will be implemented in Phase 3
    return new ArrayList<>();
  }

  // Keep old signature for backward compatibility
  public List<ObjectNode> buildSlideComposition(Presentation presentation) {
    return buildSlideComposition(presentation, new HashMap<>());
  }

  private ObjectNode buildTitleTrack(String title) {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    ObjectNode clip = objectMapper.createObjectNode();
    ObjectNode asset = objectMapper.createObjectNode();

    asset.put("type", "html");
    // TODO: TEMP FIX - Replace hardcoded "Consulting Proposal" with dynamic title
    // Original: asset.put("html", "<p data-html-type=\"text\">" + escapeHtml(title) + "</p>");
    asset.put("html", "<p data-html-type=\"text\">Consulting Proposal</p>");
    asset.put(
        "css",
        "p { color: #ffffff; font-size: 39px; font-family: 'Clear Sans', sans-serif; text-align: center; }");
    asset.put("width", 640);
    asset.put("height", 46);

    clip.set("asset", asset);
    clip.put("start", 0.7);
    clip.put("length", 4.3);
    clip.put("position", "center");

    ObjectNode offset = objectMapper.createObjectNode();
    offset.put("x", -0.209);
    offset.put("y", -0.317);
    clip.set("offset", offset);

    ObjectNode transition = objectMapper.createObjectNode();
    transition.put("in", "wipeRight");
    transition.put("out", "wipeLeft");
    clip.set("transition", transition);

    clips.add(clip);
    track.set("clips", clips);

    return track;
  }

  private ObjectNode buildSubtitleTrack(String subtitle) {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    ObjectNode clip = objectMapper.createObjectNode();
    ObjectNode asset = objectMapper.createObjectNode();

    asset.put("type", "html");
    // TODO: TEMP FIX - Replace hardcoded "By Roeland Hofkens" with dynamic subtitle/author
    // Original: asset.put("html", "<p data-html-type=\"text\">" + escapeHtml(subtitle) + "</p>");
    asset.put("html", "<p data-html-type=\"text\">By Roeland Hofkens</p>");
    asset.put(
        "css",
        "p { color: #282828; font-size: 26px; font-family: 'Clear Sans', sans-serif; text-align: center; }");
    asset.put("width", 457);
    asset.put("height", 46);

    clip.set("asset", asset);
    clip.put("start", 0.8);
    clip.put("length", 4.3);
    clip.put("position", "center");

    ObjectNode offset = objectMapper.createObjectNode();
    offset.put("x", -0.125);
    offset.put("y", -0.376);
    clip.set("offset", offset);

    ObjectNode transition = objectMapper.createObjectNode();
    transition.put("in", "wipeLeft");
    transition.put("out", "wipeRight");
    clip.set("transition", transition);

    clips.add(clip);
    track.set("clips", clips);

    return track;
  }

  private ObjectNode buildLowerThirdInTrack() {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    ObjectNode clip = objectMapper.createObjectNode();
    ObjectNode asset = objectMapper.createObjectNode();

    asset.put("type", "video");
    asset.put("src", shotstackConfig.getAssets().getLowerThird().getIn());
    asset.put("volume", 1);

    clip.set("asset", asset);

    // Add offset for positioning
    ObjectNode offset = objectMapper.createObjectNode();
    offset.put("x", -0.18);
    offset.put("y", -0.347);
    clip.set("offset", offset);

    clip.put("position", "center");
    clip.put("fit", "none");
    clip.put("start", 0.5);
    clip.put("length", 4.0);

    clips.add(clip);
    track.set("clips", clips);

    return track;
  }

  private ObjectNode buildLowerThirdOutTrack() {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    ObjectNode clip = objectMapper.createObjectNode();
    ObjectNode asset = objectMapper.createObjectNode();

    asset.put("type", "video");
    asset.put("src", shotstackConfig.getAssets().getLowerThird().getOut());
    asset.put("volume", 1);

    clip.set("asset", asset);
    clip.put("start", 4.0);
    clip.put("length", 1.5);

    // Add offset for positioning
    ObjectNode offset = objectMapper.createObjectNode();
    offset.put("x", -0.18);
    offset.put("y", -0.347);
    clip.set("offset", offset);

    clip.put("fit", "none");

    clips.add(clip);
    track.set("clips", clips);

    return track;
  }

  private ObjectNode buildIntroVideoTrack(String introVideoUrl) {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    // Main intro video clip
    ObjectNode videoClip = objectMapper.createObjectNode();
    ObjectNode videoAsset = objectMapper.createObjectNode();

    videoAsset.put("type", "video");
    videoAsset.put("src", introVideoUrl);
    videoAsset.put("volume", 1.0);

    videoClip.set("asset", videoAsset);
    videoClip.put("start", 0);
    videoClip.put("length", 8);
    videoClip.put("scale", 1);

    clips.add(videoClip);

    // Luma matte transition at the end
    ObjectNode lumaClip = objectMapper.createObjectNode();
    ObjectNode lumaAsset = objectMapper.createObjectNode();

    lumaAsset.put("type", "luma");
    lumaAsset.put("src", shotstackConfig.getAssets().getLumaMatte().getPattern());

    lumaClip.set("asset", lumaAsset);
    lumaClip.put("start", 6);
    lumaClip.put("length", 2);

    clips.add(lumaClip);

    track.set("clips", clips);

    return track;
  }

  private ObjectNode buildOutput() {
    ObjectNode output = objectMapper.createObjectNode();
    output.put("format", "mp4");
    output.put("resolution", "hd");
    output.put("fps", 25);
    output.put("quality", "medium");

    ObjectNode size = objectMapper.createObjectNode();
    size.put("width", 1920);
    size.put("height", 1080);
    output.set("size", size);

    return output;
  }

  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * Prepares asset URLs based on the configured asset mode. In R2 Direct mode: validates and
   * refreshes presigned URLs if needed In Shotstack Upload mode: uploads assets to Shotstack for
   * preview capability
   */
  private Map<String, String> prepareAssetUrls(Presentation presentation, IntroVideo introVideo) {
    Map<String, String> assetUrls = new HashMap<>();
    String mode = assetMode;

    log.info("Preparing asset URLs for presentation {} in {} mode", presentation.getId(), mode);

    // Handle intro video URL
    if (introVideo != null && introVideo.getPublishedUrl() != null) {
      String processedUrl = processAssetUrl(introVideo.getPublishedUrl(), "video", mode);
      assetUrls.put("intro-video", processedUrl);
    }

    // For Phase 3: Add slide assets here
    // Map<String, String> slideAssets = r2AssetService.getSlideAssetUrls(presentation.getId());
    // for (Map.Entry<String, String> entry : slideAssets.entrySet()) {
    //   String processedUrl = processAssetUrl(entry.getValue(), "image", mode);
    //   assetUrls.put(entry.getKey(), processedUrl);
    // }

    return assetUrls;
  }

  /**
   * Processes a single asset URL based on the mode.
   *
   * @param sourceUrl The original asset URL (typically R2 presigned URL)
   * @param assetType The type of asset (video, image, audio)
   * @param mode The asset mode (r2-direct or shotstack-upload)
   * @return The processed URL suitable for Shotstack composition
   */
  private String processAssetUrl(String sourceUrl, String assetType, String mode) {
    if (sourceUrl == null || sourceUrl.isEmpty()) {
      return sourceUrl;
    }

    if ("shotstack-upload".equalsIgnoreCase(mode)) {
      // Upload to Shotstack for preview capability
      try {
        log.debug("Uploading {} asset to Shotstack for preview mode", assetType);
        return assetPublisher.uploadAsset(sourceUrl, assetType);
      } catch (Exception e) {
        log.error("Failed to upload asset to Shotstack, falling back to R2 URL", e);
        return validateAndRefreshUrl(sourceUrl);
      }
    } else {
      // R2 Direct mode - validate and refresh if needed
      return validateAndRefreshUrl(sourceUrl);
    }
  }

  /** Validates a presigned URL and refreshes it if expired or expiring soon. */
  private String validateAndRefreshUrl(String url) {
    if (url == null || !url.contains("X-Amz-Expires")) {
      // Not a presigned URL, return as-is
      return url;
    }

    PresignedUrlValidator.UrlValidationResult validation = urlValidator.validateUrl(url);
    if (!validation.isValid()) {
      log.warn(
          "Presigned URL validation failed: {}. Attempting to refresh.",
          validation.getErrorMessage());

      // Try to find the asset metadata by URL and regenerate
      try {
        // Extract the object key from the URL to find the asset
        String objectKey = extractObjectKeyFromUrl(url);
        if (objectKey != null) {
          // Find asset metadata by object key and regenerate URL
          java.util.Optional<ai.bluefields.ppt2video.entity.AssetMetadata> assetOpt =
              r2AssetService.findAssetByObjectKey(objectKey);
          if (assetOpt.isPresent()) {
            String newUrl = r2AssetService.regeneratePresignedUrl(assetOpt.get().getId());
            log.info("Successfully regenerated presigned URL for asset");
            return newUrl;
          }
        }
      } catch (Exception e) {
        log.error("Failed to regenerate presigned URL", e);
      }

      // If regeneration fails, return original URL and hope for the best
      log.warn("Could not regenerate URL, using original which may fail");
    }

    return url;
  }

  /** Extracts the object key from an R2 presigned URL. */
  private String extractObjectKeyFromUrl(String url) {
    try {
      // R2 URLs have format:
      // https://[bucket].[account].r2.cloudflarestorage.com/[object-key]?[params]
      java.net.URI uri = new java.net.URI(url);
      String path = uri.getPath();
      if (path != null && path.startsWith("/")) {
        return path.substring(1); // Remove leading slash
      }
    } catch (Exception e) {
      log.debug("Could not extract object key from URL: {}", e.getMessage());
    }
    return null;
  }

  /** Batch processes multiple asset URLs. */
  public Map<String, String> processAssetUrls(Map<String, String> sourceUrls, String assetType) {
    String mode = assetMode;
    Map<String, String> processedUrls = new HashMap<>();

    for (Map.Entry<String, String> entry : sourceUrls.entrySet()) {
      String processedUrl = processAssetUrl(entry.getValue(), assetType, mode);
      processedUrls.put(entry.getKey(), processedUrl);
    }

    return processedUrls;
  }
}
