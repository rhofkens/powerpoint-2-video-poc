package ai.bluefields.ppt2video.service.video.provider.shotstack;

import ai.bluefields.ppt2video.config.ShotstackConfig;
import ai.bluefields.ppt2video.entity.AssetMetadata;
import ai.bluefields.ppt2video.entity.AssetType;
import ai.bluefields.ppt2video.entity.AvatarVideo;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.AssetMetadataRepository;
import ai.bluefields.ppt2video.repository.AvatarVideoRepository;
import ai.bluefields.ppt2video.repository.SlideRepository;
import ai.bluefields.ppt2video.service.R2AssetService;
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
import org.springframework.transaction.annotation.Transactional;

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
  private final ShotstackAssetPublisher assetPublisher;
  private final SlideRepository slideRepository;
  private final AvatarVideoRepository avatarVideoRepository;
  private final AssetMetadataRepository assetMetadataRepository;

  @Value("${shotstack.assets.mode:r2-direct}")
  private String assetMode;

  @Value("${shotstack.assets.cache-duration-hours:24}")
  private int cacheDurationHours;

  /** Builds the full timeline composition combining intro and slides. */
  public JsonNode buildFullTimeline(Presentation presentation, IntroVideo introVideo) {
    log.info(
        "Building full timeline for presentation: {} using asset mode: {}",
        presentation.getId(),
        assetMode);

    ObjectNode edit = objectMapper.createObjectNode();
    ObjectNode timeline = objectMapper.createObjectNode();
    ArrayNode tracks = objectMapper.createArrayNode();

    // Build intro composition (8 seconds)
    List<ObjectNode> introTracks = buildIntroComposition(presentation, introVideo);

    // Build slide composition
    List<ObjectNode> slideTracks = buildSlideComposition(presentation);

    // Combine tracks (slides first as background, intro on top)
    // Tracks are layered in reverse order (last track is bottom layer)
    introTracks.forEach(tracks::add);
    slideTracks.forEach(tracks::add);

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
    String introVideoUrl = getIntroVideoUrl(introVideo);
    tracks.add(buildIntroVideoTrack(introVideoUrl));

    return tracks;
  }

  // Keep old signature for backward compatibility
  public List<ObjectNode> buildIntroComposition(Presentation presentation, IntroVideo introVideo) {
    return buildIntroComposition(presentation, introVideo, new HashMap<>());
  }

  /** Builds the slide composition with avatar videos and slide images. */
  @Transactional(readOnly = true)
  public List<ObjectNode> buildSlideComposition(
      Presentation presentation, Map<String, String> assetUrls) {
    log.info("Building slide composition for presentation: {}", presentation.getId());

    List<ObjectNode> tracks = new ArrayList<>();

    // Fetch all slides for the presentation, ordered by slide number
    List<Slide> slides =
        slideRepository.findByPresentationIdOrderBySlideNumber(presentation.getId());
    if (slides.isEmpty()) {
      log.warn("No slides found for presentation: {}", presentation.getId());
      return tracks;
    }

    // Calculate timing for each slide
    double currentTime = 8.0; // Start after intro (8 seconds)

    for (Slide slide : slides) {
      log.info("Processing slide {} (number: {})", slide.getId(), slide.getSlideNumber());

      // Fetch avatar video for this slide
      List<AvatarVideo> avatarVideos =
          avatarVideoRepository.findBySlideIdAndStatusCompleted(slide.getId());

      if (avatarVideos.isEmpty()) {
        log.warn("No completed avatar video found for slide: {}, skipping", slide.getId());
        continue;
      }

      // Use the most recent completed avatar video
      AvatarVideo avatarVideo = avatarVideos.get(0);

      // Debug logging for r2Asset
      log.info(
          "Avatar video {} for slide {} - r2Asset: {}, r2AssetId: {}",
          avatarVideo.getId(),
          slide.getId(),
          avatarVideo.getR2Asset() != null ? "present" : "null",
          avatarVideo.getR2Asset() != null ? avatarVideo.getR2Asset().getId() : "N/A");

      // Check if we have duration data
      if (avatarVideo.getDurationSeconds() == null || avatarVideo.getDurationSeconds() <= 0) {
        log.error(
            "Avatar video {} has no duration data, skipping slide {}",
            avatarVideo.getId(),
            slide.getId());
        continue;
      }

      // Fetch slide image asset
      List<AssetMetadata> slideImages =
          assetMetadataRepository.findBySlideIdAndAssetType(slide.getId(), AssetType.SLIDE_IMAGE);

      if (slideImages.isEmpty()) {
        log.warn("No slide image found for slide: {}, skipping", slide.getId());
        continue;
      }

      // Use the first slide image (there should typically be only one)
      AssetMetadata slideImage = slideImages.get(0);

      // Get URLs directly - they are already processed by PreCompositionAssetPublisher
      String avatarVideoUrl = getAssetUrl(avatarVideo);
      String slideImageUrl = getAssetUrl(slideImage);

      // Calculate timing for this slide
      SlideTimingInfo timing = calculateSlideTiming(currentTime, avatarVideo.getDurationSeconds());

      // Build avatar video track (on top)
      ObjectNode avatarTrack =
          buildAvatarVideoTrack(
              avatarVideoUrl,
              timing.avatarStart,
              timing.avatarDuration,
              avatarVideo.getBackgroundColor());

      // Build slide image track (background)
      ObjectNode slideTrack =
          buildSlideImageTrack(
              slideImageUrl,
              timing.slideStart,
              timing.slideDuration,
              slide.getSlideNumber() < slides.size()); // Add transition except for last slide

      // Add tracks (remember: tracks are rendered in reverse order)
      tracks.add(avatarTrack);
      tracks.add(slideTrack);

      // Update current time for next slide
      currentTime = timing.nextSlideStart;

      log.info(
          "Added slide {} - start: {}, avatar duration: {}, next: {}",
          slide.getSlideNumber(),
          timing.slideStart,
          timing.avatarDuration,
          timing.nextSlideStart);
    }

    log.info(
        "Built {} tracks for {} slides, total duration: {} seconds",
        tracks.size(),
        slides.size(),
        currentTime);

    return tracks;
  }

  /** Helper class to hold slide timing information */
  private static class SlideTimingInfo {
    double slideStart; // When slide image appears
    double avatarStart; // When avatar video starts
    double avatarDuration; // Duration of avatar video
    double slideDuration; // Total duration of slide image
    double transitionStart; // When transition begins
    double nextSlideStart; // When next slide should start
  }

  /** Calculate precise timing for a slide */
  private SlideTimingInfo calculateSlideTiming(double currentTime, double avatarDurationSeconds) {
    SlideTimingInfo timing = new SlideTimingInfo();

    // Slide image starts slightly before avatar for smooth transition
    timing.slideStart = currentTime - 2.0; // 2 seconds overlap with previous transition
    if (timing.slideStart < currentTime) {
      timing.slideStart = currentTime; // But not before current time for first slide after intro
    }

    // Avatar starts after a small delay
    timing.avatarStart = currentTime + 1.0;
    timing.avatarDuration = avatarDurationSeconds;

    // Slide image extends beyond avatar for smooth transition
    timing.slideDuration = timing.avatarDuration + 4.0; // 1 second before + 3 seconds after

    // Transition starts before slide ends (2-second overlap)
    timing.transitionStart = timing.slideStart + timing.slideDuration - 2.0;

    // Next slide starts during the transition
    timing.nextSlideStart = timing.slideStart + timing.slideDuration - 1.0;

    return timing;
  }

  /** Build avatar video track with chroma key */
  private ObjectNode buildAvatarVideoTrack(
      String videoUrl, double start, double duration, String backgroundColor) {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    ObjectNode clip = objectMapper.createObjectNode();
    ObjectNode asset = objectMapper.createObjectNode();

    asset.put("type", "video");
    asset.put("src", videoUrl);
    asset.put("volume", 1);

    // Add chroma key for background removal
    ObjectNode chromaKey = objectMapper.createObjectNode();
    // Use the background color from avatar video, default to wheat if not specified
    String chromaColor = backgroundColor != null ? backgroundColor : "#F5DEB3";
    chromaKey.put("color", chromaColor);
    chromaKey.put("threshold", 15);
    chromaKey.put("halo", 50);
    asset.set("chromaKey", chromaKey);

    clip.set("asset", asset);
    clip.put("start", start);
    clip.put("length", duration);
    clip.put("scale", 0.519); // Standard scale from example

    // Position avatar in standard location
    ObjectNode offset = objectMapper.createObjectNode();
    offset.put("x", 0.371);
    offset.put("y", -0.241);
    clip.set("offset", offset);

    // Add fade transitions
    ObjectNode transition = objectMapper.createObjectNode();
    transition.put("in", "fade");
    transition.put("out", "fade");
    clip.set("transition", transition);

    clip.put("position", "center");

    clips.add(clip);
    track.set("clips", clips);

    return track;
  }

  /** Gets the appropriate intro video URL based on the asset mode. */
  private String getIntroVideoUrl(IntroVideo introVideo) {
    AssetMetadata assetMetadata = introVideo.getR2Asset();
    if (assetMetadata == null) {
      log.error("Intro video {} has no AssetMetadata", introVideo.getId());
      return null;
    }

    if ("shotstack-upload".equalsIgnoreCase(assetMode)) {
      // Check if we have a valid Shotstack URL in AssetMetadata
      if (assetMetadata.getShotstackUrl() != null
          && assetMetadata.getShotstackUploadedAt() != null
          && !isExpired(assetMetadata.getShotstackUploadedAt())) {
        return assetMetadata.getShotstackUrl();
      }
      // Fallback to R2 URL if Shotstack URL is expired or missing
      log.warn("Intro video {} Shotstack URL expired or missing, using R2 URL", introVideo.getId());
    }
    // Use fresh R2 URL directly
    return r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
  }

  /**
   * Gets the appropriate asset URL based on the asset mode. URLs are already processed by
   * PreCompositionAssetPublisher.
   */
  private String getAssetUrl(AvatarVideo avatarVideo) {
    AssetMetadata assetMetadata = avatarVideo.getR2Asset();
    if (assetMetadata == null) {
      log.error(
          "Avatar video {} has no AssetMetadata - slideId: {}",
          avatarVideo.getId(),
          avatarVideo.getSlide() != null ? avatarVideo.getSlide().getId() : "null");
      return null;
    }

    if ("shotstack-upload".equalsIgnoreCase(assetMode)) {
      // Check if we have a valid Shotstack URL in AssetMetadata
      if (assetMetadata.getShotstackUrl() != null
          && assetMetadata.getShotstackUploadedAt() != null
          && !isExpired(assetMetadata.getShotstackUploadedAt())) {
        return assetMetadata.getShotstackUrl();
      }
      // Fallback to R2 URL if Shotstack URL is expired or missing
      log.warn(
          "Avatar video {} Shotstack URL expired or missing, using R2 URL", avatarVideo.getId());
    }
    // Use fresh R2 URL directly
    return r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
  }

  /** Gets the appropriate asset URL for slide images based on the asset mode. */
  private String getAssetUrl(AssetMetadata assetMetadata) {
    if ("shotstack-upload".equalsIgnoreCase(assetMode)) {
      // Try to get from Shotstack cache first
      String cachedUrl = assetPublisher.getCachedUrl(assetMetadata.getId());
      if (cachedUrl != null) {
        return cachedUrl;
      }
      // Fallback to R2 URL if not in cache
      log.warn("Asset {} not found in Shotstack cache, using R2 URL", assetMetadata.getId());
    }
    // Use fresh R2 URL (already regenerated by PreCompositionAssetPublisher)
    return r2AssetService.regeneratePresignedUrl(assetMetadata.getId());
  }

  /** Build slide image track with optional transition */
  private ObjectNode buildSlideImageTrack(
      String imageUrl, double start, double duration, boolean addTransition) {
    ObjectNode track = objectMapper.createObjectNode();
    ArrayNode clips = objectMapper.createArrayNode();

    // Main slide image clip
    ObjectNode imageClip = objectMapper.createObjectNode();
    ObjectNode imageAsset = objectMapper.createObjectNode();

    imageAsset.put("type", "image");
    imageAsset.put("src", imageUrl);

    imageClip.set("asset", imageAsset);
    imageClip.put("start", start);
    imageClip.put("length", duration);

    // Add fade transitions for smooth appearance
    ObjectNode transition = objectMapper.createObjectNode();
    transition.put("in", "fade");
    transition.put("out", "fade");
    imageClip.set("transition", transition);

    clips.add(imageClip);

    // Add luma transition at the end if not the last slide
    if (addTransition) {
      ObjectNode lumaClip = objectMapper.createObjectNode();
      ObjectNode lumaAsset = objectMapper.createObjectNode();

      lumaAsset.put("type", "luma");
      // Use arrow transition from config
      String lumaUrl =
          "https://templates.shotstack.io/basic/asset/video/luma/double-arrow/double-arrow-right-45.mp4";
      lumaAsset.put("src", lumaUrl);

      lumaClip.set("asset", lumaAsset);
      lumaClip.put("start", start + duration - 2.0); // Start 2 seconds before slide ends
      lumaClip.put("length", 2.0);

      clips.add(lumaClip);
    }

    track.set("clips", clips);

    return track;
  }

  // Keep old signature for backward compatibility
  @Transactional(readOnly = true)
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

  /** Checks if a Shotstack URL is expired based on upload timestamp. */
  private boolean isExpired(java.time.LocalDateTime uploadedAt) {
    if (uploadedAt == null) {
      return true;
    }
    return uploadedAt.plusHours(cacheDurationHours).isBefore(java.time.LocalDateTime.now());
  }
}
