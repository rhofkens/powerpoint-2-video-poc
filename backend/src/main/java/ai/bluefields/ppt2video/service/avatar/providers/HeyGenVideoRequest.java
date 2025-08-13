package ai.bluefields.ppt2video.service.avatar.providers;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request model for HeyGen video generation API. Based on HeyGen API v2 specifications. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeyGenVideoRequest {

  /** Video inputs configuration - must be a list */
  @JsonProperty("video_inputs")
  private List<VideoInputs> videoInputs;

  /** Video dimension settings - at root level */
  @JsonProperty("dimension")
  private Dimension dimension;

  /** Whether to include captions */
  @JsonProperty("caption")
  private Boolean caption;

  /** Whether this is a test request */
  @JsonProperty("test")
  private Boolean test;

  /** Webhook URL for status callbacks */
  @JsonProperty("webhook_url")
  private String webhookUrl;

  /** Additional metadata */
  @JsonProperty("metadata")
  private Map<String, Object> metadata;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VideoInputs {
    /** Character configuration (avatar or talking photo) */
    @JsonProperty("character")
    private CharacterSettings character;

    /** Voice configuration */
    @JsonProperty("voice")
    private VoiceInput voice;

    /** Video background configuration */
    @JsonProperty("background")
    private Background background;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CharacterSettings {
    /** Type of character (avatar or talking_photo) */
    @JsonProperty("type")
    private String type;

    /** Avatar ID */
    @JsonProperty("avatar_id")
    private String avatarId;

    /** Avatar scale (0-5.0, default 1.0) */
    @JsonProperty("scale")
    private Double scale;

    /** Avatar style (normal, circle, closeUp) */
    @JsonProperty("avatar_style")
    private String avatarStyle;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VoiceInput {
    /** Type of voice input (audio or text) */
    @JsonProperty("type")
    private String type;

    /** Audio URL when type is "audio" */
    @JsonProperty("audio_url")
    private String audioUrl;

    /** Text content when type is "text" */
    @JsonProperty("text")
    private String text;

    /** Voice ID when using text input */
    @JsonProperty("voice_id")
    private String voiceId;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Background {
    /** Background type (color, image, video) */
    @JsonProperty("type")
    private String type;

    /** Background value - color in hex format when type is "color" */
    @JsonProperty("value")
    private String value;

    /** Background image URL when type is "image" */
    @JsonProperty("url")
    private String url;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Dimension {
    /** Video width in pixels */
    @JsonProperty("width")
    private Integer width;

    /** Video height in pixels */
    @JsonProperty("height")
    private Integer height;
  }

  /**
   * Create a request with audio URL source.
   *
   * @param avatarId the avatar ID
   * @param audioUrl the audio URL
   * @param backgroundColor the background color
   * @return the request object
   */
  public static HeyGenVideoRequest withAudioUrl(
      String avatarId, String audioUrl, String backgroundColor) {
    VideoInputs videoInput =
        VideoInputs.builder()
            .character(
                CharacterSettings.builder()
                    .type("avatar")
                    .avatarId(avatarId)
                    .scale(1.0)
                    .avatarStyle("normal")
                    .build())
            .voice(VoiceInput.builder().type("audio").audioUrl(audioUrl).build())
            .background(Background.builder().type("color").value(backgroundColor).build())
            .build();

    return HeyGenVideoRequest.builder()
        .videoInputs(List.of(videoInput))
        .dimension(Dimension.builder().width(1280).height(720).build())
        .caption(false)
        .test(false)
        .build();
  }
}
