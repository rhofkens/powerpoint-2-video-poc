package ai.bluefields.ppt2video.config;

import com.aspose.slides.License;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Configuration for Aspose Slides library. Sets up licensing and locale configuration to prevent
 * culture-related errors.
 */
@Configuration
@Slf4j
public class AsposeConfiguration {

  @PostConstruct
  public void initialize() {
    // Set default locale to US English to avoid unsupported culture errors
    Locale.setDefault(Locale.US);
    log.info("Set default locale to: {}", Locale.getDefault());

    // Try to load Aspose license if available
    try {
      ClassPathResource licenseResource = new ClassPathResource("aspose/Aspose.Slides.lic");
      if (licenseResource.exists()) {
        try (InputStream licenseStream = licenseResource.getInputStream()) {
          License license = new License();
          license.setLicense(licenseStream);
          log.info("Aspose Slides license loaded successfully");
        }
      } else {
        log.warn("Aspose Slides license file not found. Running in evaluation mode.");
      }
    } catch (Exception e) {
      log.error("Failed to load Aspose Slides license: {}", e.getMessage());
    }
  }
}
