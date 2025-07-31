package ai.bluefields.ppt2video.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for health check endpoints. Provides a simple health status endpoint to verify
 * that the application is running and accessible, commonly used for monitoring and load balancer
 * health checks.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

  /**
   * Returns the health status of the application.
   *
   * @return ResponseEntity containing a map with the application status (always "UP" when
   *     accessible)
   */
  @GetMapping("/health")
  public ResponseEntity<Map<String, String>> health() {
    return ResponseEntity.ok(Map.of("status", "UP"));
  }
}
