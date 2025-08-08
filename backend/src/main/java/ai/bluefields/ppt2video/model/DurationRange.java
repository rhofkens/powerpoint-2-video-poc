package ai.bluefields.ppt2video.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a duration range with minimum and maximum values in seconds. Used to specify target
 * durations for slide narratives based on slide type and content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DurationRange {
  /** Minimum duration in seconds */
  private int minSeconds;

  /** Maximum duration in seconds */
  private int maxSeconds;

  /**
   * Get the midpoint of the duration range.
   *
   * @return The average of min and max duration
   */
  public int getMidpoint() {
    return (minSeconds + maxSeconds) / 2;
  }

  /**
   * Check if a duration falls within this range.
   *
   * @param durationSeconds The duration to check
   * @return true if the duration is within the range (inclusive)
   */
  public boolean isWithinRange(int durationSeconds) {
    return durationSeconds >= minSeconds && durationSeconds <= maxSeconds;
  }

  /**
   * Get the range as a formatted string.
   *
   * @return String representation like "30-45 seconds"
   */
  public String toFormattedString() {
    return String.format("%d-%d seconds", minSeconds, maxSeconds);
  }

  /**
   * Create a duration range from a single target duration with a tolerance.
   *
   * @param targetSeconds The target duration
   * @param toleranceSeconds The tolerance in seconds (±)
   * @return A new duration range
   */
  public static DurationRange fromTargetWithTolerance(int targetSeconds, int toleranceSeconds) {
    return new DurationRange(
        Math.max(1, targetSeconds - toleranceSeconds), targetSeconds + toleranceSeconds);
  }
}
