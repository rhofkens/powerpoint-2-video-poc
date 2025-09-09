package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.ColorPaletteDto;
import ai.bluefields.ppt2video.dto.veo.VeoPrompt;
import ai.bluefields.ppt2video.entity.DeckAnalysis;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for generating Veo prompts using LLM. Uses OpenAI to create high-quality, abstract
 * visualization prompts for Google Veo API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VeoPromptLLMService {

  private final ObjectMapper objectMapper;
  private final OpenAIService openAIService;

  @Value("${app.ai.veo-prompt.llm-enabled:true}")
  private boolean llmEnabled;

  @Value("${app.ai.veo-prompt.timeout-seconds:30}")
  private int timeoutSeconds;

  // System prompt for LLM
  private static final String SYSTEM_PROMPT =
      """
      You are an expert at creating prompts for Google Veo AI video generation API. You specialize in creating abstract, professional intro videos for business presentations.

      CRITICAL RULES:
      - NEVER include text, words, logos, or written content in the video
      - Focus on abstract visualizations, geometric shapes, particle effects
      - Use flowing, organic movements and transformations
      - Keep descriptions concise (under 1024 tokens)
      - Follow Google Veo prompt structure guidelines
      """;

  // User prompt template
  private static final String USER_PROMPT_TEMPLATE =
      """
      Create a Google Veo prompt for an 8-second intro video based on this presentation:

      Topic: %s
      Communication Intent: %s
      Target Audience: %s
      Tone: %s
      Key Themes: %s
      Color Palette: %s
      Color Characteristics: Brightness: %.2f, Saturation: %.2f

      Requirements:
      1. Create abstract visualizations that metaphorically represent the topic
      2. Use the provided color palette
      3. Include smooth camera movements
      4. Specify lighting and ambiance
      5. NO text, words, or logos
      6. Format as: Subject, Action, Style, Camera, Composition, Focus, Ambiance, Audio

      Example of good output:
      "Subject: Abstract geometric shapes morphing into flowing particles representing innovation and data transformation, crystalline structures dissolving into streams of light
      Action: Smooth transformations, particles flowing in helical patterns, shapes rotating and merging, pulsating rhythms expressing growth and evolution
      Style: Modern motion graphics, minimalist professional aesthetic, clean lines with organic transitions
      Camera: Wide aerial establishing shot transitioning to smooth dolly in, subtle orbital movement around central forms
      Composition: Rule of thirds with dynamic balance, layered depth with foreground particles and background gradients
      Focus: Shallow depth of field with selective focus shifts following the main action
      Ambiance: Deep blue and silver gradient palette, balanced lighting with soft volumetric rays, subtle bloom effects
      Audio: Ambient corporate electronic soundtrack with subtle crescendo, no dialogue"

      """;

  // Expected JSON response format
  private static final String RESPONSE_FORMAT =
      """
      {
        "prompt": "The main prompt text following the Subject, Action, Style, Camera, Composition, Focus, Ambiance, Audio structure",
        "negative_prompt": "Things to avoid in the video",
        "metadata": {
          "main_visual_concept": "Brief description of the main visual concept",
          "dominant_motion": "Type of camera/object movement",
          "visual_complexity": "low/medium/high"
        }
      }
      """;

  /**
   * Generate a Veo prompt using LLM based on deck analysis and color palette.
   *
   * @param deckAnalysis the presentation analysis
   * @param colorPalette the extracted color palette
   * @return the generated VeoPrompt
   */
  @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 1000))
  public VeoPrompt generatePromptWithLLM(DeckAnalysis deckAnalysis, ColorPaletteDto colorPalette) {
    if (!llmEnabled) {
      throw new IllegalStateException("LLM prompt generation is disabled");
    }

    try {
      log.info(
          "Generating Veo prompt using LLM for presentation: {}",
          deckAnalysis.getPresentation().getId());

      // Prepare context from deck analysis
      String topic = extractTopic(deckAnalysis);
      String intent =
          deckAnalysis.getCommunicationIntent() != null
              ? deckAnalysis.getCommunicationIntent()
              : "inform and engage";
      String audience =
          deckAnalysis.getTargetAudience() != null
              ? deckAnalysis.getTargetAudience()
              : "general business audience";
      String tone = deckAnalysis.getTone() != null ? deckAnalysis.getTone() : "professional";
      String themes = formatThemes(parseThemes(deckAnalysis.getKeyThemes()));
      String colors = formatColorPalette(colorPalette);
      float brightness =
          colorPalette != null && colorPalette.getBrightness() != null
              ? colorPalette.getBrightness().floatValue()
              : 0.5f;
      float saturation =
          colorPalette != null && colorPalette.getSaturation() != null
              ? colorPalette.getSaturation().floatValue()
              : 0.5f;

      // Format the user prompt
      String userPrompt =
          String.format(
              USER_PROMPT_TEMPLATE,
              topic,
              intent,
              audience,
              tone,
              themes,
              colors,
              brightness,
              saturation);

      // Call OpenAI service with JSON response format using Veo prompt model
      long startTime = System.currentTimeMillis();
      String responseContent =
          openAIService.generateVeoPromptCompletion(
              SYSTEM_PROMPT,
              userPrompt,
              RESPONSE_FORMAT,
              "VeoPromptLLMService",
              deckAnalysis.getPresentation().getId());
      long generationTime = System.currentTimeMillis() - startTime;

      log.debug("LLM prompt generation took {} ms", generationTime);
      log.debug("LLM response: {}", responseContent);

      return parseResponse(responseContent, generationTime);

    } catch (Exception e) {
      log.error("Failed to generate prompt using LLM", e);
      throw new RuntimeException("LLM prompt generation failed", e);
    }
  }

  /**
   * Generate a Veo prompt asynchronously.
   *
   * @param deckAnalysis the presentation analysis
   * @param colorPalette the extracted color palette
   * @return CompletableFuture with the generated VeoPrompt
   */
  @Async
  public CompletableFuture<VeoPrompt> generatePromptWithLLMAsync(
      DeckAnalysis deckAnalysis, ColorPaletteDto colorPalette) {
    return CompletableFuture.supplyAsync(() -> generatePromptWithLLM(deckAnalysis, colorPalette))
        .orTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
  }

  /** Parse the LLM response into a VeoPrompt. */
  private VeoPrompt parseResponse(String responseContent, long generationTime) {
    try {
      Map<String, Object> responseMap =
          objectMapper.readValue(responseContent, new TypeReference<>() {});

      String promptText = (String) responseMap.get("prompt");
      String negativePrompt = (String) responseMap.get("negative_prompt");

      // Validate the prompt
      if (promptText == null || promptText.isEmpty()) {
        throw new IllegalArgumentException("LLM returned empty prompt");
      }

      // Check for text-related keywords that shouldn't be in the prompt
      validatePrompt(promptText);

      // Default negative prompt if not provided
      if (negativePrompt == null || negativePrompt.isEmpty()) {
        negativePrompt =
            "text, words, logos, human faces, dialogue, literal office scenes, stock footage, low quality, pixelation, static images, cliché business imagery, watermarks, people, realistic objects";
      }

      // Log metadata if available
      if (responseMap.containsKey("metadata")) {
        Map<String, Object> metadata = (Map<String, Object>) responseMap.get("metadata");
        log.info(
            "Prompt metadata - Visual concept: {}, Motion: {}, Complexity: {}",
            metadata.get("main_visual_concept"),
            metadata.get("dominant_motion"),
            metadata.get("visual_complexity"));
      }

      return VeoPrompt.builder()
          .promptText(promptText)
          .negativePrompt(negativePrompt)
          .aspectRatio("16:9")
          .resolution("1080p")
          .personGeneration(false)
          .build();

    } catch (Exception e) {
      log.error("Failed to parse LLM response", e);
      throw new RuntimeException("Failed to parse LLM response", e);
    }
  }

  /** Validate that the prompt doesn't contain forbidden keywords. */
  private void validatePrompt(String prompt) {
    String lowerPrompt = prompt.toLowerCase();
    String[] forbiddenWords = {
      "text overlay", "title card", "logo animation", "written text", "subtitle", "caption"
    };

    for (String forbidden : forbiddenWords) {
      if (lowerPrompt.contains(forbidden)) {
        log.warn("Prompt contains forbidden keyword: {}", forbidden);
        // Remove or throw exception based on requirements
      }
    }
  }

  /** Extract the main topic from deck analysis. */
  private String extractTopic(DeckAnalysis analysis) {
    if (analysis.getOverallStory() != null && !analysis.getOverallStory().isEmpty()) {
      String story = analysis.getOverallStory();
      // Take first 200 chars or first sentence
      int endIndex = Math.min(story.length(), 200);
      String snippet = story.substring(0, endIndex);
      if (snippet.contains(".")) {
        snippet = snippet.substring(0, snippet.indexOf("."));
      }
      return snippet.trim();
    }
    return "business innovation and digital transformation";
  }

  /** Parse themes from JSON string. */
  private List<String> parseThemes(String keyThemesJson) {
    if (keyThemesJson == null || keyThemesJson.isEmpty()) {
      return List.of("innovation", "growth", "transformation");
    }

    try {
      List<String> themes =
          objectMapper.readValue(keyThemesJson, new TypeReference<List<String>>() {});
      return themes.stream()
          .limit(5) // Limit to top 5 themes
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Failed to parse themes JSON", e);
      return List.of("innovation", "growth", "transformation");
    }
  }

  /** Format themes for the prompt. */
  private String formatThemes(List<String> themes) {
    if (themes == null || themes.isEmpty()) {
      return "innovation, growth, and transformation";
    }
    return String.join(", ", themes);
  }

  /** Format color palette for the prompt. */
  private String formatColorPalette(ColorPaletteDto palette) {
    if (palette == null
        || palette.getDominantColors() == null
        || palette.getDominantColors().isEmpty()) {
      return "professional blue and gray gradient";
    }

    List<String> colorDescriptions =
        palette.getDominantColors().stream()
            .limit(3)
            .map(this::describeColor)
            .collect(Collectors.toList());

    return String.join(", ", colorDescriptions);
  }

  /** Convert hex color to descriptive name. */
  private String describeColor(String hexColor) {
    if (hexColor == null || !hexColor.startsWith("#") || hexColor.length() != 7) {
      return "neutral";
    }

    try {
      int r = Integer.parseInt(hexColor.substring(1, 3), 16);
      int g = Integer.parseInt(hexColor.substring(3, 5), 16);
      int b = Integer.parseInt(hexColor.substring(5, 7), 16);

      // Determine color name based on RGB values
      if (r > g && r > b) {
        if (r > 200) return "bright red";
        if (r > 150) return "warm red";
        return "deep red";
      } else if (g > r && g > b) {
        if (g > 200) return "bright green";
        if (g > 150) return "fresh green";
        return "forest green";
      } else if (b > r && b > g) {
        if (b > 200) return "bright blue";
        if (b > 150) return "vibrant blue";
        return "deep blue";
      } else if (r > 200 && g > 200 && b < 100) {
        return "golden yellow";
      } else if (r > 200 && g < 100 && b > 200) {
        return "vibrant purple";
      } else if (r < 100 && g > 200 && b > 200) {
        return "cool cyan";
      } else if (r > 180 && g > 180 && b > 180) {
        return "light silver";
      } else if (r < 80 && g < 80 && b < 80) {
        return "charcoal gray";
      } else {
        return "neutral tone";
      }
    } catch (Exception e) {
      return "neutral";
    }
  }
}
