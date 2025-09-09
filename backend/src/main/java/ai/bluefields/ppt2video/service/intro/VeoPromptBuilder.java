package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.ColorPaletteDto;
import ai.bluefields.ppt2video.dto.veo.VeoPrompt;
import ai.bluefields.ppt2video.entity.DeckAnalysis;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for building optimized prompts for Google Veo API. Creates dynamic prompts based on deck
 * analysis and extracted colors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VeoPromptBuilder {

  private final ObjectMapper objectMapper;

  // Concise prompt template following Google Veo best practices (under 1024 tokens)
  private static final String PROMPT_TEMPLATE =
      """
      Subject: Abstract geometric shapes and flowing particles representing %s, morphing visualizations, light trails

      Action: Shapes transforming, particles flowing in patterns, smooth transitions, pulsating rhythms expressing %s

      Style: Modern motion graphics, abstract corporate style, minimalist %s aesthetic

      Camera: Wide aerial establishing shot, smooth dolly in, orbital motion, depth transitions

      Composition: Wide to medium shot, rule of thirds, layered depth, cinematic framing

      Focus: Shallow depth of field, soft bokeh, selective focus shifts

      Ambiance: %s color palette, %s lighting for %s, volumetric effects

      Visual themes: Abstract %s representations, flowing energy patterns

      Audio: Ambient %s electronic soundtrack, no dialogue

      8 seconds, HD 1920x1080, 16:9 widescreen, professional quality
      """;

  // Concise negative prompt following Google's guidelines
  private static final String NEGATIVE_PROMPT_TEMPLATE =
      """
      Negative prompt: text, words, logos, human faces, dialogue, literal office scenes, stock footage, low quality, pixelation, static images, cliché business imagery, watermarks, people, realistic objects
      """;

  /**
   * Build a VeoPrompt from deck analysis and color palette.
   *
   * @param deckAnalysis the presentation analysis
   * @param colorPalette the extracted color palette
   * @return optimized prompt for Veo API
   */
  public VeoPrompt buildPrompt(DeckAnalysis deckAnalysis, ColorPaletteDto colorPalette) {
    try {
      // Extract key information from deck analysis
      String topic = extractTopic(deckAnalysis);
      String intent = deckAnalysis.getCommunicationIntent();
      String tone = deckAnalysis.getTone() != null ? deckAnalysis.getTone() : "professional";
      String audience =
          deckAnalysis.getTargetAudience() != null
              ? deckAnalysis.getTargetAudience()
              : "general audience";
      List<String> themes = parseThemes(deckAnalysis.getKeyThemes());

      // Format colors
      String colorDescription = formatColorDescription(colorPalette);

      // Determine audio mood based on tone
      String audioMood = determineAudioMood(tone);

      // Determine lighting style
      String lightingStyle = determineLightingStyle(tone, colorPalette);

      // Build the prompt
      String promptText =
          String.format(
              PROMPT_TEMPLATE,
              topic, // Subject representation
              intent, // Action expression
              tone.toLowerCase(), // Style aesthetic
              colorDescription, // Color palette
              lightingStyle, // Lighting style
              audience, // Target audience
              formatThemes(themes), // Visual themes
              audioMood // Audio mood
              );

      // Clean up extra whitespace
      promptText = promptText.trim().replaceAll("\\s+", " ");

      return VeoPrompt.builder()
          .promptText(promptText)
          .negativePrompt(NEGATIVE_PROMPT_TEMPLATE.trim())
          .aspectRatio("16:9")
          .resolution("1080p")
          .personGeneration(false)
          .build();

    } catch (Exception e) {
      log.error("Error building Veo prompt", e);
      return createFallbackPrompt();
    }
  }

  /** Extract the main topic from deck analysis. */
  private String extractTopic(DeckAnalysis analysis) {
    if (analysis.getOverallStory() != null && !analysis.getOverallStory().isEmpty()) {
      // Extract first key phrase from overall story
      String story = analysis.getOverallStory();
      int endIndex = Math.min(story.length(), 100);
      String snippet = story.substring(0, endIndex);

      // Clean and simplify
      return snippet.replaceAll("[.!?].*", "").replaceAll("\\s+", " ").trim().toLowerCase();
    }

    if (analysis.getCommunicationIntent() != null) {
      return analysis.getCommunicationIntent().toLowerCase();
    }

    return "business innovation and growth";
  }

  /** Parse themes from JSON string. */
  private List<String> parseThemes(String keyThemesJson) {
    if (keyThemesJson == null || keyThemesJson.isEmpty()) {
      return List.of("innovation", "progress", "transformation");
    }

    try {
      List<String> themes =
          objectMapper.readValue(keyThemesJson, new TypeReference<List<String>>() {});
      return themes.stream()
          .limit(3) // Limit to top 3 themes for brevity
          .map(String::toLowerCase)
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to parse themes JSON", e);
      return List.of("innovation", "progress", "transformation");
    }
  }

  /** Format themes for inclusion in prompt. */
  private String formatThemes(List<String> themes) {
    if (themes == null || themes.isEmpty()) {
      return "innovation and progress";
    }

    return themes.stream().limit(3).collect(Collectors.joining(", "));
  }

  /** Format color description for the prompt. */
  private String formatColorDescription(ColorPaletteDto palette) {
    if (palette == null
        || palette.getDominantColors() == null
        || palette.getDominantColors().isEmpty()) {
      return "Modern blue and gray gradient";
    }

    StringBuilder description = new StringBuilder();

    // Describe primary colors
    List<String> colors = palette.getDominantColors();
    if (!colors.isEmpty()) {
      description.append(describeColor(colors.get(0)));

      if (colors.size() > 1) {
        description.append(" with ").append(describeColor(colors.get(1))).append(" accents");
      }
    }

    // Add brightness/saturation characteristics
    if (palette.getBrightness() != null) {
      if (palette.getBrightness() < 0.3) {
        description.append(", dark and moody");
      } else if (palette.getBrightness() > 0.7) {
        description.append(", bright and vibrant");
      }
    }

    if (palette.getSaturation() != null && palette.getSaturation() < 0.3) {
      description.append(", desaturated");
    }

    return description.toString();
  }

  /** Convert hex color to descriptive name. */
  private String describeColor(String hexColor) {
    if (hexColor == null || !hexColor.startsWith("#")) {
      return "neutral";
    }

    try {
      // Parse RGB values
      int r = Integer.parseInt(hexColor.substring(1, 3), 16);
      int g = Integer.parseInt(hexColor.substring(3, 5), 16);
      int b = Integer.parseInt(hexColor.substring(5, 7), 16);

      // Simple color naming based on RGB dominance
      if (r > g && r > b) {
        if (r > 200) return "bright red";
        if (r > 150) return "warm red";
        return "deep red";
      } else if (g > r && g > b) {
        if (g > 200) return "bright green";
        if (g > 150) return "fresh green";
        return "deep green";
      } else if (b > r && b > g) {
        if (b > 200) return "bright blue";
        if (b > 150) return "vibrant blue";
        return "deep blue";
      } else if (r > 200 && g > 200 && b < 100) {
        return "warm yellow";
      } else if (r > 200 && g < 100 && b > 200) {
        return "vibrant purple";
      } else if (r < 100 && g > 200 && b > 200) {
        return "cool cyan";
      } else if (r > 180 && g > 180 && b > 180) {
        return "light gray";
      } else if (r < 80 && g < 80 && b < 80) {
        return "dark gray";
      } else {
        return "neutral tone";
      }
    } catch (Exception e) {
      return "neutral";
    }
  }

  /** Determine audio mood based on presentation tone. */
  private String determineAudioMood(String tone) {
    if (tone == null) {
      return "corporate";
    }

    return switch (tone.toLowerCase()) {
      case "professional", "formal" -> "corporate";
      case "inspirational", "motivational" -> "uplifting";
      case "casual", "friendly" -> "relaxed";
      case "urgent", "serious" -> "intense";
      case "educational", "informative" -> "thoughtful";
      case "innovative", "creative" -> "futuristic";
      case "emotional", "personal" -> "emotive";
      default -> "ambient";
    };
  }

  /** Determine lighting style based on tone and colors. */
  private String determineLightingStyle(String tone, ColorPaletteDto palette) {
    StringBuilder lighting = new StringBuilder();

    // Base lighting on tone
    if (tone != null) {
      switch (tone.toLowerCase()) {
        case "professional", "formal" -> lighting.append("clean, balanced");
        case "inspirational", "motivational" -> lighting.append("bright, optimistic");
        case "serious", "urgent" -> lighting.append("dramatic, focused");
        case "innovative", "creative" -> lighting.append("dynamic, colorful");
        default -> lighting.append("soft, ambient");
      }
    } else {
      lighting.append("balanced");
    }

    // Adjust based on color brightness
    if (palette != null && palette.getBrightness() != null) {
      if (palette.getBrightness() < 0.3) {
        lighting.append(" with deep shadows");
      } else if (palette.getBrightness() > 0.7) {
        lighting.append(" with bright highlights");
      }
    }

    return lighting.toString();
  }

  /** Create a fallback prompt when analysis fails. */
  private VeoPrompt createFallbackPrompt() {
    String fallbackPrompt =
        """
        Subject: Abstract geometric shapes, flowing particles, modern corporate visualization
        Action: Smooth transformations, particle flows, rhythmic pulsations
        Style: Professional motion graphics, minimalist aesthetic
        Camera: Wide establishing shot, smooth dolly movements
        Composition: Cinematic framing, layered depth
        Ambiance: Blue and gray gradient, balanced lighting, volumetric effects
        Audio: Ambient corporate soundtrack, no dialogue
        8 seconds, HD 1920x1080, 16:9 widescreen
        """;

    return VeoPrompt.builder()
        .promptText(fallbackPrompt.trim())
        .negativePrompt(NEGATIVE_PROMPT_TEMPLATE.trim())
        .aspectRatio("16:9")
        .resolution("1080p")
        .personGeneration(false)
        .build();
  }
}
