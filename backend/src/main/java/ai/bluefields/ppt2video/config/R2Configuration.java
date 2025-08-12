package ai.bluefields.ppt2video.config;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Cloudflare R2 storage integration. Manages bucket configurations,
 * credentials, and URL generation settings.
 */
@Configuration
@ConfigurationProperties(prefix = "cloudflare.r2")
@Data
public class R2Configuration {

  private String accessKeyId;
  private String secretAccessKey;
  private String endpoint;
  private String region = "auto";
  private Map<String, BucketConfig> buckets;
  private UrlConfig urls = new UrlConfig();

  @Data
  public static class BucketConfig {
    private String name;
    private String prefix;
  }

  @Data
  public static class UrlConfig {
    private Duration defaultExpiration = Duration.ofHours(48);
    private Duration maxExpiration = Duration.ofDays(7);
  }
}
