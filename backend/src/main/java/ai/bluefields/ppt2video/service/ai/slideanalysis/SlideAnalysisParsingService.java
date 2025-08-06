package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.entity.SlideAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service responsible for parsing AI analysis results. Handles JSON parsing and entity creation for
 * slide analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideAnalysisParsingService {

  private final ObjectMapper objectMapper;

  @Value("${spring.ai.openai.chat.options.model:gpt-4}")
  private String modelName;

  /**
   * Parse AI analysis result and create SlideAnalysis entity.
   *
   * @param analysisResult The JSON result from AI
   * @param slide The slide being analyzed
   * @return The created SlideAnalysis entity
   */
  public SlideAnalysis parseSlideAnalysis(String analysisResult, Slide slide) {
    try {
      Map<String, Object> analysisMap = objectMapper.readValue(analysisResult, Map.class);

      SlideAnalysis analysis = new SlideAnalysis();
      analysis.setSlide(slide);

      // Parse basic fields
      parseBasicFields(analysisMap, analysis);

      // Parse visual concepts
      parseVisualConcepts(analysisMap, analysis);

      // Parse key points
      parseKeyPoints(analysisMap, analysis);

      // Set metadata
      setAnalysisMetadata(analysis, slide);

      analysis.setModelUsed(modelName);
      analysis.setPromptVersion("1.0");

      return analysis;

    } catch (Exception e) {
      log.error("Failed to parse slide analysis result for slide {}", slide.getId(), e);
      throw new RuntimeException("Failed to parse slide analysis", e);
    }
  }

  /** Parse basic fields from analysis result. */
  private void parseBasicFields(Map<String, Object> analysisMap, SlideAnalysis analysis) {
    analysis.setGeneralMessage((String) analysisMap.get("generalMessage"));
    analysis.setDataInsights((String) analysisMap.get("dataInsights"));
    analysis.setTransitionContext((String) analysisMap.get("transitionContext"));
    analysis.setEmphasisLevel((String) analysisMap.get("emphasisLevel"));
  }

  /** Parse and store visual concepts as JSON. */
  @SuppressWarnings("unchecked")
  private void parseVisualConcepts(Map<String, Object> analysisMap, SlideAnalysis analysis)
      throws Exception {
    List<Map<String, Object>> visualConcepts =
        (List<Map<String, Object>>) analysisMap.get("visualConcepts");

    if (visualConcepts != null) {
      // Validate and enrich visual concepts
      validateVisualConcepts(visualConcepts);
      analysis.setVisualConcepts(objectMapper.writeValueAsString(visualConcepts));

      // Log concept types for debugging
      if (log.isDebugEnabled()) {
        visualConcepts.forEach(
            concept ->
                log.debug(
                    "Found visual concept: {} with confidence: {}",
                    concept.get("type"),
                    concept.get("confidence")));
      }
    }
  }

  /** Parse and store key points as JSON. */
  @SuppressWarnings("unchecked")
  private void parseKeyPoints(Map<String, Object> analysisMap, SlideAnalysis analysis)
      throws Exception {
    List<String> keyPoints = (List<String>) analysisMap.get("keyPoints");

    if (keyPoints != null && !keyPoints.isEmpty()) {
      analysis.setKeyPoints(objectMapper.writeValueAsString(keyPoints));
      log.debug("Parsed {} key points", keyPoints.size());
    } else {
      analysis.setKeyPoints("[]");
    }
  }

  /** Validate visual concepts structure. */
  private void validateVisualConcepts(List<Map<String, Object>> visualConcepts) {
    for (Map<String, Object> concept : visualConcepts) {
      // Ensure required fields are present
      if (!concept.containsKey("type")) {
        log.warn("Visual concept missing 'type' field: {}", concept);
        concept.put("type", "UNKNOWN");
      }

      if (!concept.containsKey("confidence")) {
        concept.put("confidence", "LOW");
      }

      // Ensure position structure
      if (!concept.containsKey("position")) {
        Map<String, String> defaultPosition = new HashMap<>();
        defaultPosition.put("vertical", "CENTER");
        defaultPosition.put("horizontal", "CENTER");
        concept.put("position", defaultPosition);
      }

      if (!concept.containsKey("prominence")) {
        concept.put("prominence", "SECONDARY");
      }

      if (!concept.containsKey("details")) {
        concept.put("details", new HashMap<String, Object>());
      }
    }
  }

  /** Set analysis metadata. */
  private void setAnalysisMetadata(SlideAnalysis analysis, Slide slide) throws Exception {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("analysisDate", new Date());
    metadata.put("imageAnalyzed", true);
    metadata.put("slideNumber", slide.getSlideNumber());
    metadata.put("slideId", slide.getId().toString());

    // Add image metadata if available
    if (slide.getImagePath() != null) {
      metadata.put("imagePath", slide.getImagePath());
      metadata.put("imageWidth", slide.getImageWidth());
      metadata.put("imageHeight", slide.getImageHeight());
    }

    analysis.setAnalysisMetadata(objectMapper.writeValueAsString(metadata));
  }

  /**
   * Extract visual concept types from analysis.
   *
   * @param analysis The slide analysis
   * @return List of visual concept types
   */
  @SuppressWarnings("unchecked")
  public List<String> extractVisualConceptTypes(SlideAnalysis analysis) {
    try {
      if (analysis.getVisualConcepts() != null) {
        List<Map<String, Object>> concepts =
            objectMapper.readValue(analysis.getVisualConcepts(), List.class);

        return concepts.stream()
            .map(c -> (String) c.get("type"))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
      }
    } catch (Exception e) {
      log.warn("Failed to extract visual concept types", e);
    }
    return Collections.emptyList();
  }

  /**
   * Check if analysis contains specific visual concept type.
   *
   * @param analysis The slide analysis
   * @param conceptType The concept type to check for
   * @return true if the concept type is present
   */
  public boolean hasVisualConcept(SlideAnalysis analysis, String conceptType) {
    return extractVisualConceptTypes(analysis).contains(conceptType);
  }
}
