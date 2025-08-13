package ai.bluefields.ppt2video.service.avatar.providers;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for HeyGen avatar video generation service. Manages API credentials and
 * default settings for HeyGen integration.
 */
@Configuration
@ConfigurationProperties(prefix = "heygen")
@Data
public class HeyGenConfiguration {

  /** HeyGen API configuration */
  private Api api = new Api();

  /** Avatar configuration */
  private Avatar avatar = new Avatar();

  /** Video generation settings */
  private Video video = new Video();

  @Data
  public static class Api {
    /** API key for HeyGen authentication */
    private String key;

    /** Base URL for HeyGen API */
    private String baseUrl = "https://api.heygen.com";

    /** API version */
    private String version = "v2";

    /** Connection timeout in milliseconds */
    private int connectTimeout = 30000;

    /** Read timeout in milliseconds */
    private int readTimeout = 60000;

    /** Maximum retry attempts */
    private int maxRetries = 3;

    /** Retry delay in milliseconds */
    private long retryDelay = 2000;
  }

  @Data
  public static class Avatar {
    /** Default avatar ID to use */
    private String defaultId = "Brandon_expressive2_public";

    /** Default background color for chroma key */
    private String backgroundColor = "#F5DEB3";

    /** Whether to use transparent background */
    private boolean transparentBackground = false;
  }

  @Data
  public static class Video {
    /** Default video dimension (width) */
    private int width = 1920;

    /** Default video dimension (height) */
    private int height = 1080;

    /** Video aspect ratio */
    private String aspectRatio = "16:9";

    /** Default video quality */
    private String quality = "high";

    /** Test mode (uses test endpoints if true) */
    private boolean testMode = false;

    /** Webhook URL for status updates (optional) */
    private String webhookUrl;

    /** Whether to enable callbacks */
    private boolean callbackEnabled = false;
  }

  /**
   * Check if HeyGen is properly configured.
   *
   * @return true if API key is set
   */
  public boolean isConfigured() {
    return api.getKey() != null && !api.getKey().isEmpty();
  }

  /**
   * Get the full API URL for a specific endpoint.
   *
   * @param endpoint the endpoint path
   * @return the full URL
   */
  public String getApiUrl(String endpoint) {
    // Use v2 for video creation, v1 for everything else
    String version = endpoint.equals("video/generate") ? "v2" : "v1";
    return String.format("%s/%s/%s", api.getBaseUrl(), version, endpoint);
  }
}
