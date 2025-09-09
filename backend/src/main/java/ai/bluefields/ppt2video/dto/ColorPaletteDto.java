package ai.bluefields.ppt2video.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the color palette extracted from a slide. Contains dominant colors and color
 * statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColorPaletteDto {

  private List<String> dominantColors; // Hex color codes
  private String primaryColor;
  private String secondaryColor;
  private String accentColor;
  private Double brightness; // 0.0 to 1.0
  private Double saturation; // 0.0 to 1.0
  private String colorScheme; // e.g., "monochromatic", "complementary", "analogous"
}
