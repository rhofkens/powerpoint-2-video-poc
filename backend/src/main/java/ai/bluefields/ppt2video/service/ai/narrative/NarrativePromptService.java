package ai.bluefields.ppt2video.service.ai.narrative;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for building prompts for narrative generation. Handles different narrative
 * styles and prompt construction.
 */
@Slf4j
@Service
public class NarrativePromptService {

  /**
   * Get system prompt based on narrative style.
   *
   * @param style The narrative style ("business", "funny", "cynical")
   * @return The system prompt for the specified style
   */
  public String getSystemPromptForStyle(String style) {
    switch (style) {
      case "funny":
        return getFunnyStylePrompt();
      case "cynical":
        return getCynicalStylePrompt();
      case "business":
      default:
        return getBusinessStylePrompt();
    }
  }

  /**
   * Build user prompt for narrative generation.
   *
   * @param contextDataJson The context data in JSON format
   * @return The formatted user prompt
   */
  public String buildUserPrompt(String contextDataJson) {
    return String.format(
        """
        Generate an engaging narrative for the CURRENT SLIDE ONLY, using the provided context for continuity.

        IMPORTANT: Focus your narrative exclusively on the "currentSlide" section below.
        The "previousSlide" and "nextSlide" are provided for context only - do not narrate them.

        Context provided:
        %s

        OUTPUT FORMAT:
        Create a JSON object with the following structure:

        Required fields:
        - narrativeText: The main narrative text to be spoken for the CURRENT slide
        - emotionIndicators: Array of emotion markers throughout the narrative
        - avatarInstructions: Instructions for avatar rendering
        - speechMarkers: Guidance for speech synthesis
        - durationSeconds: Target duration in seconds (typically 30-60)
        - transitionPhrase: Optional phrase to connect to the next slide

        EXAMPLE OUTPUT:
        {
          "narrativeText": "The main narrative text to be spoken",
          "emotionIndicators": [
            {
              "startWord": 0,
              "endWord": 5,
              "emotion": "enthusiastic"
            }
          ],
          "avatarInstructions": {
            "primaryEmotion": "confident",
            "gestureIntensity": "MEDIUM",
            "facialExpression": "Friendly smile with engaged eyes"
          },
          "speechMarkers": {
            "pauses": [
              {
                "afterWord": 10,
                "duration": "medium"
              }
            ],
            "emphasis": ["key", "important", "critical"]
          },
          "durationSeconds": 45,
          "transitionPhrase": "Now let's explore..."
        }
        """,
        contextDataJson);
  }

  /**
   * Get the JSON schema for the expected response format.
   *
   * @return The response format schema
   */
  public String getResponseFormatSchema() {
    return """
        {
          "narrativeText": "string",
          "emotionIndicators": [
            {
              "startWord": "number",
              "endWord": "number",
              "emotion": "string"
            }
          ],
          "avatarInstructions": {
            "primaryEmotion": "string",
            "gestureIntensity": "string (LOW|MEDIUM|HIGH)",
            "facialExpression": "string"
          },
          "speechMarkers": {
            "pauses": [
              {
                "afterWord": "number",
                "duration": "string (short|medium|long)"
              }
            ],
            "emphasis": ["string"]
          },
          "durationSeconds": "number",
          "transitionPhrase": "string or null"
        }
        """;
  }

  private String getBusinessStylePrompt() {
    return """
        You are an expert presentation narrator who creates engaging, natural-sounding narratives
        for video presentations. Your narratives should be conversational yet professional,
        incorporating emotional cues for avatar rendering.

        Guidelines:
        1. Create fluid, engaging narratives that sound natural when spoken
        2. Include appropriate emotional indicators (enthusiasm, seriousness, curiosity, etc.)
        3. Add natural transitions when referencing other slides
        4. Include pauses and emphasis markers for better delivery
        5. Consider the target audience and overall tone
        6. Make the narrative 30-60 seconds when spoken (approximately 75-150 words)

        Emotional indicators to use:
        - neutral: Default professional tone
        - enthusiastic: Excited, energetic delivery
        - serious: Grave, important topics
        - curious: Questioning, exploratory tone
        - confident: Assertive, decisive delivery
        - friendly: Warm, approachable tone
        """;
  }

  private String getFunnyStylePrompt() {
    return """
        You are a witty, entertaining presentation narrator who creates humorous, engaging narratives
        for video presentations. Your narratives should be light-hearted and amusing while still
        conveying the key information, incorporating emotional cues for avatar rendering.

        Guidelines:
        1. Create funny, entertaining narratives with appropriate humor and wit
        2. Use amusing analogies, clever wordplay, and light-hearted observations
        3. Include comedic timing with strategic pauses and emphasis
        4. Keep the humor professional and accessible to a business audience
        5. Include appropriate emotional indicators (playful, amused, excited, surprised)
        6. Make the narrative 30-60 seconds when spoken (approximately 75-150 words)

        Emotional indicators to use:
        - playful: Light, fun delivery
        - amused: Finding humor in the content
        - excited: Enthusiastic, energetic
        - surprised: Mock surprise for effect
        - cheeky: Slightly mischievous tone
        - cheerful: Upbeat and positive
        """;
  }

  private String getCynicalStylePrompt() {
    return """
        You are a sharp, skeptical presentation narrator who creates thought-provoking narratives
        with a cynical edge. Your narratives should question assumptions and offer critical
        perspectives while still being professional, incorporating emotional cues for avatar rendering.

        Guidelines:
        1. Create narratives with a skeptical, questioning tone
        2. Point out potential flaws, contradictions, or overly optimistic assumptions
        3. Use dry humor and sardonic observations
        4. Include reality checks and "let's be honest" moments
        5. Remain professional despite the cynical perspective
        6. Make the narrative 30-60 seconds when spoken (approximately 75-150 words)

        Emotional indicators to use:
        - skeptical: Questioning, doubtful tone
        - sardonic: Dry, mocking humor
        - serious: Grave delivery for emphasis
        - resigned: Accepting unfortunate realities
        - ironic: Highlighting contradictions
        - deadpan: Flat delivery for effect
        """;
  }
}
