package ai.bluefields.ppt2video.service.video;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for validating R2 presigned URLs to ensure they remain valid during video rendering.
 * Parses expiration times from presigned URLs and determines if they need regeneration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PresignedUrlValidator {

  @Value("${shotstack.assets.url-validation.buffer-minutes:5}")
  private int bufferMinutes;

  @Value("${video.story.max-duration-minutes:60}")
  private int maxRenderDurationMinutes;

  /**
   * Validates if a presigned URL will remain valid for the expected rendering duration.
   *
   * @param url The presigned URL to validate
   * @return Validation result with details
   */
  public UrlValidationResult validateUrl(String url) {
    if (url == null || url.isEmpty()) {
      return UrlValidationResult.invalid("URL is null or empty");
    }

    // Check if this is an R2 presigned URL
    if (!isPresignedUrl(url)) {
      // Non-presigned URLs (like permanent Shotstack URLs) are always valid
      return UrlValidationResult.valid(null);
    }

    try {
      // Parse expiration from URL parameters
      Long expirationTimestamp = parseExpirationFromUrl(url);
      if (expirationTimestamp == null) {
        log.warn("Could not parse expiration from presigned URL: {}", url);
        return UrlValidationResult.invalid("Could not parse expiration timestamp");
      }

      LocalDateTime expirationTime =
          LocalDateTime.ofInstant(Instant.ofEpochSecond(expirationTimestamp), ZoneId.of("UTC"));

      // Calculate time until expiration
      LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
      long minutesUntilExpiration = ChronoUnit.MINUTES.between(now, expirationTime);

      // Check if URL will remain valid for render duration plus buffer
      int requiredValidityMinutes = maxRenderDurationMinutes + bufferMinutes;
      boolean isValid = minutesUntilExpiration > requiredValidityMinutes;

      if (!isValid) {
        return UrlValidationResult.invalid(
            String.format(
                "URL expires in %d minutes, need at least %d minutes",
                minutesUntilExpiration, requiredValidityMinutes));
      }

      return UrlValidationResult.valid(expirationTime);

    } catch (Exception e) {
      log.error("Error validating presigned URL", e);
      return UrlValidationResult.invalid("Error parsing URL: " + e.getMessage());
    }
  }

  /**
   * Checks if multiple URLs are valid. Returns true only if all URLs are valid.
   *
   * @param urls Map of asset name to URL
   * @return Map of asset name to validation result
   */
  public Map<String, UrlValidationResult> validateUrls(Map<String, String> urls) {
    Map<String, UrlValidationResult> results = new HashMap<>();
    for (Map.Entry<String, String> entry : urls.entrySet()) {
      results.put(entry.getKey(), validateUrl(entry.getValue()));
    }
    return results;
  }

  /**
   * Checks if all provided URLs are valid.
   *
   * @param urls Map of asset name to URL
   * @return true if all URLs are valid
   */
  public boolean areAllUrlsValid(Map<String, String> urls) {
    return validateUrls(urls).values().stream().allMatch(UrlValidationResult::isValid);
  }

  /**
   * Determines if a URL is a presigned URL based on its parameters.
   *
   * @param url The URL to check
   * @return true if the URL appears to be a presigned URL
   */
  private boolean isPresignedUrl(String url) {
    return url != null
        && (url.contains("X-Amz-Expires") // AWS/R2 presigned URL
            || url.contains("Expires") // Alternative format
            || url.contains("signature")); // Generic signed URL
  }

  /**
   * Parses the expiration timestamp from a presigned URL.
   *
   * @param urlString The presigned URL
   * @return Expiration timestamp in seconds, or null if not found
   */
  private Long parseExpirationFromUrl(String urlString) {
    try {
      URI uri = new URI(urlString);
      String query = uri.getQuery();
      if (query == null) {
        return null;
      }

      // Parse query parameters
      Map<String, String> params = new HashMap<>();
      for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        if (pair.length == 2) {
          String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
          String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
          params.put(key, value);
        }
      }

      // Look for expiration in different formats
      // AWS/R2 format: X-Amz-Date + X-Amz-Expires
      if (params.containsKey("X-Amz-Date") && params.containsKey("X-Amz-Expires")) {
        String dateStr = params.get("X-Amz-Date");
        String expiresStr = params.get("X-Amz-Expires");

        // Parse date (format: yyyyMMddTHHmmssZ)
        Instant startTime = parseAmzDate(dateStr);
        if (startTime != null) {
          long expiresSeconds = Long.parseLong(expiresStr);
          return startTime.getEpochSecond() + expiresSeconds;
        }
      }

      // Direct expiration timestamp
      if (params.containsKey("Expires")) {
        return Long.parseLong(params.get("Expires"));
      }

      return null;

    } catch (Exception e) {
      log.debug("Error parsing expiration from URL: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Parses AWS date format (yyyyMMddTHHmmssZ) to Instant.
   *
   * @param dateStr The date string
   * @return Instant or null if parsing fails
   */
  private Instant parseAmzDate(String dateStr) {
    try {
      // Format: 20240110T123045Z
      if (dateStr.length() != 16 || !dateStr.endsWith("Z")) {
        return null;
      }

      int year = Integer.parseInt(dateStr.substring(0, 4));
      int month = Integer.parseInt(dateStr.substring(4, 6));
      int day = Integer.parseInt(dateStr.substring(6, 8));
      int hour = Integer.parseInt(dateStr.substring(9, 11));
      int minute = Integer.parseInt(dateStr.substring(11, 13));
      int second = Integer.parseInt(dateStr.substring(13, 15));

      return LocalDateTime.of(year, month, day, hour, minute, second)
          .atZone(ZoneId.of("UTC"))
          .toInstant();

    } catch (Exception e) {
      log.debug("Error parsing AMZ date: {}", e.getMessage());
      return null;
    }
  }

  /** Result of URL validation. */
  @Data
  public static class UrlValidationResult {
    private final boolean valid;
    private final LocalDateTime expirationTime;
    private final String errorMessage;

    public static UrlValidationResult valid(LocalDateTime expirationTime) {
      return new UrlValidationResult(true, expirationTime, null);
    }

    public static UrlValidationResult invalid(String errorMessage) {
      return new UrlValidationResult(false, null, errorMessage);
    }

    private UrlValidationResult(boolean valid, LocalDateTime expirationTime, String errorMessage) {
      this.valid = valid;
      this.expirationTime = expirationTime;
      this.errorMessage = errorMessage;
    }
  }
}
