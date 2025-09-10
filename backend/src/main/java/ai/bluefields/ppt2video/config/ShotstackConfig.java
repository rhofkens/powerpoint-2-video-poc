package ai.bluefields.ppt2video.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "shotstack")
@Data
@Slf4j
public class ShotstackConfig {

  private Api api = new Api();
  private Assets assets = new Assets();

  @PostConstruct
  public void init() {
    // Log the active configuration based on environment
    String activeBaseUrl = api.getActiveBaseUrl();
    String activeKey = api.getActiveApiKey();
    log.info(
        "Shotstack configured for {} environment with base URL: {} (API key {}configured)",
        api.getEnvironment(),
        activeBaseUrl,
        (activeKey != null && !activeKey.isEmpty()) ? "" : "NOT ");
  }

  @Data
  public static class Api {
    private Key key = new Key();
    private BaseUrl baseUrl = new BaseUrl();
    private String environment = "production"; // Default to production
    private int timeout = 30000;
    private int maxRetries = 3;

    /** Gets the active API key based on the configured environment. */
    public String getActiveApiKey() {
      if ("production".equalsIgnoreCase(environment)) {
        return key.getProduction();
      } else if ("sandbox".equalsIgnoreCase(environment) || "stage".equalsIgnoreCase(environment)) {
        return key.getSandbox();
      }
      // Default to production key if environment is unknown
      return key.getProduction();
    }

    /** Gets the active base URL based on the configured environment. */
    public String getActiveBaseUrl() {
      if ("production".equalsIgnoreCase(environment)) {
        return baseUrl.getProduction();
      } else if ("sandbox".equalsIgnoreCase(environment) || "stage".equalsIgnoreCase(environment)) {
        return baseUrl.getSandbox();
      }
      // Default to production URL
      return baseUrl.getProduction();
    }

    @Data
    public static class Key {
      private String production;
      private String sandbox;
    }

    @Data
    public static class BaseUrl {
      private String production = "https://api.shotstack.io/edit";
      private String sandbox = "https://api.shotstack.io";
    }
  }

  @Data
  public static class Assets {
    private LowerThird lowerThird = new LowerThird();
    private LumaMatte lumaMatte = new LumaMatte();
  }

  @Data
  public static class LowerThird {
    private String in;
    private String out;
  }

  @Data
  public static class LumaMatte {
    private String pattern;
    private String arrowRight;
  }
}
