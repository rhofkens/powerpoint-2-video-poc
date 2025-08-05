package ai.bluefields.ppt2video.service.rendering;

import lombok.Builder;
import lombok.Data;

/** Metrics for slide rendering performance and quality. */
@Data
@Builder
public class RenderingMetrics {

  private long totalRenderTime;
  private int slidesRendered;
  private int renderingFailures;
  private double averageRenderTimeMs;
  private long peakMemoryUsageMb;
  private String rendererName;
  private long lastRenderTime;

  /**
   * Calculate average render time per slide.
   *
   * @return average time in milliseconds
   */
  public double getAverageRenderTime() {
    if (slidesRendered == 0) {
      return 0;
    }
    return (double) totalRenderTime / slidesRendered;
  }

  /**
   * Get success rate as a percentage.
   *
   * @return success rate between 0 and 100
   */
  public double getSuccessRate() {
    int totalAttempts = slidesRendered + renderingFailures;
    if (totalAttempts == 0) {
      return 100.0;
    }
    return ((double) slidesRendered / totalAttempts) * 100;
  }
}
