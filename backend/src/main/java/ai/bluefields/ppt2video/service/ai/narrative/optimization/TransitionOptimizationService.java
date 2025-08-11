package ai.bluefields.ppt2video.service.ai.narrative.optimization;

import ai.bluefields.ppt2video.entity.SlideNarrative;
import ai.bluefields.ppt2video.repository.SlideNarrativeRepository;
import ai.bluefields.ppt2video.service.ai.OpenAIService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for optimizing transitions between slide narratives. Takes all narratives for
 * a presentation and improves the transition phrases to create more natural flow between slides.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransitionOptimizationService {

  private final OpenAIService openAIService;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final ObjectMapper objectMapper;

  @Value("${app.ai.narrative.optimization.transition.timeout-seconds:60}")
  private int timeoutSeconds;

  /**
   * Optimize transitions for all narratives in a presentation. Sends all narratives to AI to
   * improve transition phrases between slides.
   *
   * @param presentationId The presentation ID
   * @param narratives List of narratives to optimize
   * @return Number of narratives successfully optimized
   */
  @Transactional
  public int optimizeTransitions(UUID presentationId, List<SlideNarrative> narratives) {
    if (narratives == null || narratives.isEmpty()) {
      log.info("No narratives to optimize for presentation {}", presentationId);
      return 0;
    }

    log.info(
        "Starting transition optimization for {} narratives in presentation {}",
        narratives.size(),
        presentationId);

    try {
      // Build the context with all narratives
      String narrativesJson = buildNarrativesJson(narratives);

      // Get optimized transitions from AI
      String optimizedJson = callAIForTransitionOptimization(narrativesJson, presentationId);

      // Parse and apply the optimizations
      int optimized = applyOptimizedTransitions(narratives, optimizedJson);

      log.info(
          "Successfully optimized transitions for {}/{} narratives", optimized, narratives.size());
      return optimized;

    } catch (Exception e) {
      log.error("Failed to optimize transitions for presentation {}", presentationId, e);
      return 0;
    }
  }

  /** Build JSON representation of all narratives for AI processing. */
  private String buildNarrativesJson(List<SlideNarrative> narratives) throws Exception {
    List<Map<String, Object>> narrativeData =
        narratives.stream()
            .map(
                narrative -> {
                  Map<String, Object> data = new java.util.HashMap<>();
                  data.put("id", narrative.getId().toString());
                  data.put("slideNumber", narrative.getSlide().getSlideNumber());
                  data.put("narrativeText", narrative.getNarrativeText());
                  data.put(
                      "currentTransition",
                      narrative.getTransitionPhrase() != null
                          ? narrative.getTransitionPhrase()
                          : "");
                  data.put(
                      "slideTitle",
                      narrative.getSlide().getTitle() != null
                          ? narrative.getSlide().getTitle()
                          : "");
                  data.put(
                      "slideContent",
                      narrative.getSlide().getContentText() != null
                          ? narrative.getSlide().getContentText()
                          : "");
                  return data;
                })
            .collect(Collectors.toList());

    return objectMapper.writeValueAsString(narrativeData);
  }

  /** Call OpenAI to optimize transitions between narratives. */
  private String callAIForTransitionOptimization(String narrativesJson, UUID presentationId) {
    String systemPrompt =
        """
        You are an expert presentation narrator specializing in creating smooth,
        natural transitions between slides. Your task is to review all slide narratives
        and improve the transition phrases to create better flow.

        Guidelines for transitions:
        1. Reference the content of the next slide naturally
        2. Use varied transition phrases (avoid repetition)
        3. Create logical connections between topics
        4. Keep transitions concise (5-15 words typically)
        5. Match the overall tone of the presentation

        Good transition examples:
        - "This naturally leads us to explore..."
        - "Building on that foundation, let's examine..."
        - "Now that we've covered X, we'll turn to..."
        - "Speaking of which, our next topic..."
        - "This brings us to an important point about..."

        Avoid generic transitions like:
        - "Let's move to the next slide"
        - "Next slide please"
        - "On the following slide"
        """;

    String userPrompt =
        String.format(
            """
        Review these presentation narratives and optimize the transitions between slides.
        Focus on making the flow from one slide to the next feel natural and engaging.

        Current narratives:
        %s

        Return a JSON array with the same structure, but with improved transition phrases.
        Each object should have:
        - id: (keep the same)
        - slideNumber: (keep the same)
        - improvedTransition: The new transition phrase to the NEXT slide (empty string for last slide)

        IMPORTANT: Only return the JSON array, no other text.
        """,
            narrativesJson);

    return openAIService.generateChatCompletion(
        systemPrompt, userPrompt, "TransitionOptimizationService", presentationId);
  }

  /** Apply the optimized transitions back to the narrative entities. */
  private int applyOptimizedTransitions(List<SlideNarrative> narratives, String optimizedJson) {
    try {
      log.debug("Parsing optimized transitions JSON response");

      // Parse the optimized transitions
      List<Map<String, Object>> optimizedData =
          objectMapper.readValue(optimizedJson, new TypeReference<List<Map<String, Object>>>() {});

      log.info("Parsed {} transition entries from AI response", optimizedData.size());

      // Create a map for quick lookup
      Map<String, String> transitionMap = new HashMap<>();
      for (Map<String, Object> data : optimizedData) {
        String id = data.get("id") != null ? data.get("id").toString() : null;
        String transition =
            data.get("improvedTransition") != null ? data.get("improvedTransition").toString() : "";

        if (id != null) {
          transitionMap.put(id, transition);
          log.debug(
              "Mapped transition for narrative {}: {}",
              id,
              transition.length() > 50 ? transition.substring(0, 50) + "..." : transition);
        }
      }

      log.info("Created transition map with {} entries", transitionMap.size());

      // Apply transitions to narratives
      int updated = 0;
      for (SlideNarrative narrative : narratives) {
        String narrativeId = narrative.getId().toString();
        String newTransition = transitionMap.get(narrativeId);

        log.debug(
            "Processing narrative {}: found transition = {}", narrativeId, newTransition != null);

        if (newTransition != null && !newTransition.isEmpty()) {
          log.info(
              "Updating transition for slide {}: '{}'",
              narrative.getSlide().getSlideNumber(),
              newTransition);

          narrative.setTransitionPhrase(newTransition);
          SlideNarrative saved = slideNarrativeRepository.save(narrative);

          // Verify the save worked
          if (saved.getTransitionPhrase() != null
              && saved.getTransitionPhrase().equals(newTransition)) {
            updated++;
            log.info(
                "✓ Successfully saved transition for slide {}",
                narrative.getSlide().getSlideNumber());
          } else {
            log.error(
                "Failed to save transition for slide {} - transition not persisted",
                narrative.getSlide().getSlideNumber());
          }
        } else if (newTransition != null && newTransition.isEmpty()) {
          // Last slide should have empty transition
          narrative.setTransitionPhrase("");
          slideNarrativeRepository.save(narrative);
          log.debug("Cleared transition for last slide {}", narrative.getSlide().getSlideNumber());
        }
      }

      log.info("Applied {} transition updates out of {} narratives", updated, narratives.size());
      return updated;

    } catch (Exception e) {
      log.error("Failed to parse and apply optimized transitions: {}", e.getMessage(), e);
      log.error(
          "Raw JSON that failed to parse: {}",
          optimizedJson.length() > 500 ? optimizedJson.substring(0, 500) + "..." : optimizedJson);
      return 0;
    }
  }
}
