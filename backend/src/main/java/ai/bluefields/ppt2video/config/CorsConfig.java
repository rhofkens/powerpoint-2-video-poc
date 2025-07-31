package ai.bluefields.ppt2video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Configuration class for Cross-Origin Resource Sharing (CORS) settings. This configuration allows
 * the frontend development server to communicate with the backend API during development without
 * CORS restrictions.
 */
@Configuration
public class CorsConfig {

  /**
   * Creates and configures a CORS filter for the application.
   *
   * @return CorsFilter configured to allow requests from the frontend development server
   */
  @Bean
  public CorsFilter corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();

    // Allow frontend development server
    config.addAllowedOrigin("http://localhost:5173");

    // Allow all common HTTP methods
    config.addAllowedMethod("*");

    // Allow all headers
    config.addAllowedHeader("*");

    // Allow credentials
    config.setAllowCredentials(true);

    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }
}
