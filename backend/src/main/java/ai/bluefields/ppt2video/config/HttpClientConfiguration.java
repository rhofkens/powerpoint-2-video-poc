package ai.bluefields.ppt2video.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for HTTP client used by avatar providers and other services. Provides a shared,
 * configured HttpClient instance.
 */
@Configuration
public class HttpClientConfiguration {

  /**
   * Create a configured HTTP client bean.
   *
   * @return the HTTP client
   */
  @Bean
  public HttpClient httpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
