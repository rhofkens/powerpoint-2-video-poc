package ai.bluefields.ppt2video.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for narrative generation with style selection. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateNarrativeRequestDto {

  /**
   * The narrative style to use for generation. Supported values: "business", "funny", "cynical"
   * Default: "business"
   */
  private String style = "business";

  /**
   * Validates that the style is one of the supported values.
   *
   * @return true if valid, false otherwise
   */
  public boolean isValidStyle() {
    return style != null
        && (style.equals("business") || style.equals("funny") || style.equals("cynical"));
  }
}
