package ai.bluefields.ppt2video.service.ai.narrative;

import ai.bluefields.ppt2video.entity.SlideType;
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
   * Build user prompt for narrative generation with default duration.
   *
   * @param contextDataJson The context data in JSON format
   * @return The formatted user prompt
   */
  public String buildUserPrompt(String contextDataJson) {
    // Default duration range for backward compatibility
    return buildUserPrompt(contextDataJson, 30, 60, SlideType.CONTENT);
  }

  /**
   * Build user prompt for narrative generation with specific duration requirements.
   *
   * @param contextDataJson The context data in JSON format
   * @param minDurationSeconds Minimum duration in seconds
   * @param maxDurationSeconds Maximum duration in seconds
   * @return The formatted user prompt
   */
  public String buildUserPrompt(
      String contextDataJson, int minDurationSeconds, int maxDurationSeconds) {
    return buildUserPrompt(
        contextDataJson, minDurationSeconds, maxDurationSeconds, SlideType.CONTENT);
  }

  /**
   * Build user prompt for narrative generation with specific duration and slide type.
   *
   * @param contextDataJson The context data in JSON format
   * @param minDurationSeconds Minimum duration in seconds
   * @param maxDurationSeconds Maximum duration in seconds
   * @param slideType The type of slide being narrated
   * @return The formatted user prompt
   */
  public String buildUserPrompt(
      String contextDataJson, int minDurationSeconds, int maxDurationSeconds, SlideType slideType) {
    // Calculate approximate word count for the duration
    // Assuming 150 words per minute speaking pace
    int minWords = (minDurationSeconds * 150) / 60;
    int maxWords = (maxDurationSeconds * 150) / 60;

    String slideTypeGuidance =
        getSlideTypeGuidance(slideType, minDurationSeconds, maxDurationSeconds);

    return String.format(
        """
        Generate an engaging narrative for the CURRENT SLIDE ONLY, using the provided context for continuity.

        %s

        CRITICAL DURATION REQUIREMENT:
        The narrative MUST be EXACTLY %d-%d seconds when spoken aloud at a normal pace (150 words per minute).
        This translates to approximately %d-%d words.
        IMPORTANT: Count the words in your response to ensure it matches this requirement!

        IMPORTANT: Focus your narrative exclusively on the "currentSlide" section below.
        The "previousSlide" and "nextSlide" are provided for context only - do not narrate them.

        CRITICAL: DO NOT include specific contact details in the narrative:
        - NEVER read out phone numbers, email addresses, or postal addresses
        - Instead, use phrases like "contact information is available", "reach out via email", "give us a call", or "visit our website"
        - This makes the narrative more pleasant to listen to and avoids awkward pronunciation of technical details

        Context provided:
        %s

        OUTPUT FORMAT:
        Create a JSON object with the following structure:

        Required fields:
        - narrativeText: The main narrative text to be spoken for the CURRENT slide (MUST be %d-%d words)
        - emotionIndicators: Array of emotion markers throughout the narrative
        - avatarInstructions: Instructions for avatar rendering
        - speechMarkers: Guidance for speech synthesis
        - durationSeconds: Target duration in seconds (MUST be between %d and %d)
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
        slideTypeGuidance,
        minDurationSeconds,
        maxDurationSeconds,
        minWords,
        maxWords,
        contextDataJson,
        minWords,
        maxWords,
        minDurationSeconds,
        maxDurationSeconds);
  }

  /**
   * Get slide type-specific guidance for narrative generation.
   *
   * @param slideType The type of slide
   * @param minDurationSeconds Minimum duration in seconds
   * @param maxDurationSeconds Maximum duration in seconds
   * @return Guidance text for the specific slide type
   */
  private String getSlideTypeGuidance(
      SlideType slideType, int minDurationSeconds, int maxDurationSeconds) {
    switch (slideType) {
      case INTRO:
        return String.format(
            """
            SLIDE TYPE: INTRODUCTION SLIDE
            This is the opening slide that sets the stage for the entire presentation.
            Generate a %d-%d second narrative that:
            - Welcomes the audience warmly
            - Introduces the topic with enthusiasm
            - Sets expectations for what's to come
            - Creates engagement from the start
            Even if the slide has minimal text, provide rich context and background.""",
            minDurationSeconds, maxDurationSeconds);

      case SEPARATOR:
        return String.format(
            """
            SLIDE TYPE: SECTION SEPARATOR
            This is a transition slide between major sections.
            Generate a BRIEF %d-%d second narrative that:
            - Smoothly transitions from the previous section
            - Introduces the upcoming section
            - Maintains presentation flow
            - Keeps the audience engaged during the transition
            Be concise and focused on the transition.""",
            minDurationSeconds, maxDurationSeconds);

      case THANK_YOU:
        return String.format(
            """
            SLIDE TYPE: CLOSING/THANK YOU SLIDE
            This is the conclusion slide.
            Generate a CONCISE %d-%d second narrative that:
            - Summarizes key takeaways briefly
            - Thanks the audience sincerely
            - Ends on a memorable note
            - Invites questions or next steps if appropriate
            Keep it brief and impactful.""",
            minDurationSeconds, maxDurationSeconds);

      case CONTENT:
      default:
        return String.format(
            """
            SLIDE TYPE: CONTENT SLIDE
            This is a standard content slide with information to convey.
            Generate a %d-%d second narrative that:
            - Explains the content clearly and engagingly
            - Provides appropriate detail based on the text density
            - Connects to the overall presentation theme
            - Maintains audience interest
            Match the narrative length to the content amount.""",
            minDurationSeconds, maxDurationSeconds);
    }
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
        6. CRITICAL: Respect the exact duration requirements specified in the prompt
        7. Use concise language for short durations, more detail for longer durations
        8. NEVER pronounce phone numbers, email addresses, or postal addresses - use general references instead

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
        6. CRITICAL: Respect the exact duration requirements specified in the prompt
        7. Balance humor with content - shorter durations need punchier jokes
        8. NEVER pronounce phone numbers, email addresses, or postal addresses - make jokes about "sliding into DMs" or "carrier pigeons" instead

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
        6. CRITICAL: Respect the exact duration requirements specified in the prompt
        7. Adjust cynicism intensity based on duration - brief for short, elaborate for long
        8. NEVER pronounce phone numbers, email addresses, or postal addresses - be cynical about "yet another way to reach out"

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
