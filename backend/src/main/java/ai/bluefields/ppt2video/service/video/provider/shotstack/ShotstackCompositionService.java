package ai.bluefields.ppt2video.service.video.provider.shotstack;

import ai.bluefields.ppt2video.config.ShotstackConfig;
import ai.bluefields.ppt2video.entity.IntroVideo;
import ai.bluefields.ppt2video.entity.Presentation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  /** Builds the full timeline composition combining intro and slides. */
  public JsonNode buildFullTimeline(Presentation presentation, IntroVideo introVideo) {
    log.info("Building full timeline for presentation: {}", presentation.getId());

    ObjectNode edit = objectMapper.createObjectNode();
    ObjectNode timeline = objectMapper.createObjectNode();
    ArrayNode tracks = objectMapper.createArrayNode();

    // Build intro composition (8 seconds)
    List<ObjectNode> introTracks = buildIntroComposition(presentation, introVideo);

    // Build slide composition (empty for now)
    List<ObjectNode> slideTracks = buildSlideComposition(presentation);

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
  public List<ObjectNode> buildIntroComposition(Presentation presentation, IntroVideo introVideo) {
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
    tracks.add(buildIntroVideoTrack(introVideo.getPublishedUrl()));

    return tracks;
  }

  /** Builds the slide composition. Currently empty placeholder for Phase 3 implementation. */
  public List<ObjectNode> buildSlideComposition(Presentation presentation) {
    log.info("Building slide composition for presentation: {} (placeholder)", presentation.getId());

    // Empty for Phase 2 - will be implemented in Phase 3
    return new ArrayList<>();
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
}
