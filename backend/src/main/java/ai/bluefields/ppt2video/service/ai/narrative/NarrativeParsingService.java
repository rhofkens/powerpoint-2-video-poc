package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for parsing and processing narrative responses from AI. Handles JSON parsing,
 * entity creation, and metadata management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NarrativeParsingService {

  private final ObjectMapper objectMapper;
  private final SlideNarrativeRepository slideNarrativeRepository;

  @Value("${spring.ai.openai.chat.options.model:gpt-4}")
  private String modelName;

  /**
   * Parse AI narrative result and create SlideNarrative entity.
   *
   * @param narrativeResult The JSON result from AI
   * @param slide The slide this narrative is for
   * @param style The narrative style used
   * @return The created SlideNarrative entity
   */
  public SlideNarrative parseNarrative(String narrativeResult, Slide slide, String style) {
    try {
      Map<String, Object> narrativeMap = objectMapper.readValue(narrativeResult, Map.class);

      SlideNarrative narrative = new SlideNarrative();
      narrative.setSlide(slide);
      narrative.setNarrativeText((String) narrativeMap.get("narrativeText"));

      // Parse and set emotion indicators
      parseEmotionIndicators(narrativeMap, narrative);

      // Parse and set avatar instructions
      parseAvatarInstructions(narrativeMap, narrative);

      // Parse and set speech markers
      parseSpeechMarkers(narrativeMap, narrative);

      // Set duration and transition
      narrative.setDurationSeconds((Integer) narrativeMap.get("durationSeconds"));
      narrative.setTransitionPhrase((String) narrativeMap.get("transitionPhrase"));

      // Set version
      setNarrativeVersion(narrative, slide.getId());

      // Set metadata
      setNarrativeMetadata(narrative, slide, style);

      narrative.setModelUsed(modelName);
      narrative.setPromptVersion("1.0");
      narrative.setIsActive(true);

      return narrative;

    } catch (Exception e) {
      log.error("Failed to parse narrative result for slide {}", slide.getId(), e);
      throw new RuntimeException("Failed to parse narrative", e);
    }
  }

  /**
   * Deactivate existing active narrative for a slide.
   *
   * @param slideId The slide ID
   */
  public void deactivateExistingNarrative(UUID slideId) {
    slideNarrativeRepository
        .findActiveNarrativeBySlideId(slideId)
        .ifPresent(
            oldNarrative -> {
              oldNarrative.setIsActive(false);
              slideNarrativeRepository.save(oldNarrative);
              log.debug("Deactivated existing narrative for slide {}", slideId);
            });
  }

  /**
   * Check if a narrative already exists for a slide.
   *
   * @param slideId The slide ID
   * @return true if narrative exists
   */
  public boolean narrativeExists(UUID slideId) {
    return slideNarrativeRepository.findBySlideId(slideId).isPresent();
  }

  /**
   * Delete existing narrative for a slide.
   *
   * @param slideId The slide ID
   */
  public void deleteExistingNarrative(UUID slideId) {
    slideNarrativeRepository
        .findBySlideId(slideId)
        .ifPresent(
            narrative -> {
              slideNarrativeRepository.delete(narrative);
              log.info("Deleted existing narrative for slide {}", slideId);
            });
  }

  private void parseEmotionIndicators(Map<String, Object> narrativeMap, SlideNarrative narrative)
      throws Exception {
    List<Map<String, Object>> emotionIndicators =
        (List<Map<String, Object>>) narrativeMap.get("emotionIndicators");
    if (emotionIndicators != null) {
      narrative.setEmotionIndicators(objectMapper.writeValueAsString(emotionIndicators));
    }
  }

  private void parseAvatarInstructions(Map<String, Object> narrativeMap, SlideNarrative narrative)
      throws Exception {
    Map<String, Object> avatarInstructions =
        (Map<String, Object>) narrativeMap.get("avatarInstructions");
    if (avatarInstructions != null) {
      narrative.setAvatarInstructions(objectMapper.writeValueAsString(avatarInstructions));
    }
  }

  private void parseSpeechMarkers(Map<String, Object> narrativeMap, SlideNarrative narrative)
      throws Exception {
    Map<String, Object> speechMarkers = (Map<String, Object>) narrativeMap.get("speechMarkers");
    if (speechMarkers != null) {
      narrative.setSpeechMarkers(objectMapper.writeValueAsString(speechMarkers));

      // Extract emphasis words
      List<String> emphasisWords = (List<String>) speechMarkers.get("emphasis");
      if (emphasisWords != null) {
        narrative.setEmphasisWords(objectMapper.writeValueAsString(emphasisWords));
      }
    }
  }

  private void setNarrativeVersion(SlideNarrative narrative, UUID slideId) {
    int version = 1;
    slideNarrativeRepository.findBySlideIdOrderByVersionDesc(slideId).stream()
        .findFirst()
        .ifPresent(prev -> narrative.setVersion(prev.getVersion() + 1));

    if (narrative.getVersion() == null) {
      narrative.setVersion(version);
    }
  }

  private void setNarrativeMetadata(SlideNarrative narrative, Slide slide, String style)
      throws Exception {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("generationDate", new Date());
    metadata.put("contextUsed", true);
    metadata.put("slideNumber", slide.getSlideNumber());
    metadata.put("narrativeStyle", style);
    narrative.setGenerationMetadata(objectMapper.writeValueAsString(metadata));
  }
}
