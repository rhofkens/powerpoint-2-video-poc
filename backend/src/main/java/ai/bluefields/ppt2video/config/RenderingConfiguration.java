package ai.bluefields.ppt2video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties for slide rendering options. */
@Configuration
@ConfigurationProperties(prefix = "app.slide-rendering")
@Data
public class RenderingConfiguration {

  /** Whether to use the enhanced renderer (recommended). */
  private boolean useEnhancedRenderer = true;

  /** Whether to use the alternative renderer. */
  private boolean useAlternativeRenderer = false;

  /** Whether to use supersampling in alternative renderer. */
  private boolean useSupersampling = false;

  /** Scale factor for enhanced rendering (higher = better quality but slower). */
  private double scaleFactor = 3.0;

  /** Output image format (PNG or JPEG). */
  private String outputFormat = "PNG";

  /** JPEG quality (0-100) if output format is JPEG. */
  private int jpegQuality = 95;

  /** Maximum width for rendered slides. */
  private int maxWidth = 1920;

  /** Maximum height for rendered slides. */
  private int maxHeight = 1080;

  /** Rendering timeout in seconds. */
  private int renderingTimeout = 30;

  /** Whether to cache rendered images. */
  private boolean enableCache = true;

  /** Cache TTL in hours. */
  private int cacheTtlHours = 24;
}
