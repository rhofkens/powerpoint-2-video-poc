package ai.bluefields.ppt2video.service.ai;

import ai.bluefields.ppt2video.entity.DeckAnalysis;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.repository.DeckAnalysisRepository;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for analyzing entire presentation decks using AI. Extracts overall story arc,
 * communication intent, and key themes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeckAnalysisService {

  private final OpenAIService openAIService;
  private final PresentationRepository presentationRepository;
  private final DeckAnalysisRepository deckAnalysisRepository;
  private final ObjectMapper objectMapper;

  @Value("${app.ai.deck-analysis.enabled:true}")
  private boolean deckAnalysisEnabled;

  @Value("${spring.ai.openai.chat.options.model:gpt-4}")
  private String modelName;

  /**
   * Analyze an entire presentation deck and store the results.
   *
   * @param presentationId The ID of the presentation to analyze
   * @return The created DeckAnalysis entity
   */
  @Transactional
  public DeckAnalysis analyzeDeck(UUID presentationId) {
    return analyzeDeck(presentationId, false);
  }

  /**
   * Analyze an entire presentation deck and store the results.
   *
   * @param presentationId The ID of the presentation to analyze
   * @param force If true, regenerate analysis even if it already exists
   * @return The created or updated DeckAnalysis entity
   */
  @Transactional
  public DeckAnalysis analyzeDeck(UUID presentationId, boolean force) {
    if (!deckAnalysisEnabled) {
      throw new IllegalStateException("Deck analysis is disabled");
    }

    log.info("Starting deck analysis for presentation: {}", presentationId);

    // Fetch presentation with slides
    Presentation presentation =
        presentationRepository
            .findById(presentationId)
            .orElseThrow(
                () -> new IllegalArgumentException("Presentation not found: " + presentationId));

    // Check if analysis already exists
    if (presentation.getDeckAnalysis() != null && !force) {
      log.info("Deck analysis already exists for presentation: {}", presentationId);
      return presentation.getDeckAnalysis();
    }

    // If forcing regeneration, delete existing analysis
    if (presentation.getDeckAnalysis() != null && force) {
      log.info("Force regenerating deck analysis for presentation: {}", presentationId);
      DeckAnalysis existingAnalysis = presentation.getDeckAnalysis();

      // First, break the relationship
      presentation.setDeckAnalysis(null);
      presentationRepository.saveAndFlush(presentation);

      // Then delete the analysis
      deckAnalysisRepository.delete(existingAnalysis);
      deckAnalysisRepository.flush();

      log.info("Deleted existing deck analysis for presentation: {}", presentationId);

      // Refresh the presentation entity to ensure clean state
      presentation =
          presentationRepository
              .findById(presentationId)
              .orElseThrow(
                  () -> new IllegalArgumentException("Presentation not found: " + presentationId));
    }

    // Update status
    presentation.setProcessingStatus(Presentation.ProcessingStatus.ANALYZING);
    presentationRepository.save(presentation);

    try {
      // Prepare deck data for analysis
      Map<String, Object> deckData = prepareDeckData(presentation);
      String deckDataJson = objectMapper.writeValueAsString(deckData);

      // Generate analysis using AI
      String analysisResult = generateDeckAnalysis(presentationId, deckDataJson);

      // Parse and save analysis
      DeckAnalysis analysis = parseDeckAnalysis(analysisResult, presentation);
      analysis = deckAnalysisRepository.save(analysis);

      log.info("Completed deck analysis for presentation: {}", presentationId);
      return analysis;

    } catch (Exception e) {
      log.error("Failed to analyze deck for presentation: {}", presentationId, e);
      presentation.setProcessingStatus(Presentation.ProcessingStatus.FAILED);
      presentationRepository.save(presentation);
      throw new RuntimeException("Failed to analyze deck", e);
    }
  }

  /** Prepare deck data for AI analysis. */
  private Map<String, Object> prepareDeckData(Presentation presentation) {
    List<Map<String, Object>> slidesData =
        presentation.getSlides().stream()
            .sorted(Comparator.comparing(Slide::getSlideNumber))
            .map(
                slide -> {
                  Map<String, Object> slideData = new HashMap<>();
                  slideData.put("slideNumber", slide.getSlideNumber());
                  slideData.put("title", slide.getTitle());
                  slideData.put("contentText", slide.getContentText());
                  slideData.put("speakerNotes", slide.getSpeakerNotes());
                  return slideData;
                })
            .collect(Collectors.toList());

    Map<String, Object> deckData = new HashMap<>();
    deckData.put("presentationTitle", presentation.getTitle());
    deckData.put("totalSlides", presentation.getSlides().size());
    deckData.put("slides", slidesData);

    return deckData;
  }

  /** Generate deck analysis using AI. */
  private String generateDeckAnalysis(UUID presentationId, String deckDataJson) {
    String systemPrompt =
        """
        You are an expert presentation analyst. Analyze the provided PowerPoint presentation data
        and extract key metadata and insights.

        Focus on understanding:
        1. The actual presentation title (usually the largest/main text on the first slide)
           - Look for the main heading on the first slide
           - This is typically the largest text element
           - Avoid using filenames or technical identifiers
           - Create a clean, professional title suitable for display

        2. The author or company name (often found on the first slide)
           - Look for author name, company name, or "Presented by" text on the first slide
           - Check speaker notes for author information
           - If multiple authors, list the primary one
           - Format as "By [Name]" or "By [Company Name]"

        3. The main message and story flow across all slides
        4. The intended communication goals and objectives
        5. Key themes and topics that recur throughout the presentation
        6. The likely target audience based on content and language
        7. The overall tone (professional, casual, educational, persuasive, etc.)

        Provide a comprehensive analysis that helps understand the presentation's purpose and effectiveness.
        """;

    String userPrompt =
        String.format(
            """
        Analyze this presentation deck and provide a detailed analysis in JSON format:

        %s

        Respond with a JSON object containing:
        {
          "presentationTitle": "The actual presentation title from the first slide",
          "presentationAuthor": "Author name or company, formatted as 'By [Name]' or 'By [Company]'",
          "overallStory": "A 2-3 paragraph summary of the presentation's story arc and main message",
          "communicationIntent": "The primary communication goals and objectives",
          "keyThemes": ["theme1", "theme2", "theme3"],
          "targetAudience": "Description of the intended audience (max 400 characters)",
          "tone": "The overall tone of the presentation (max 400 characters)"
        }

        IMPORTANT:
        - Keep targetAudience and tone fields under 400 characters each
        - Keep presentationTitle under 200 characters
        - Keep presentationAuthor under 100 characters
        """,
            deckDataJson);

    String responseFormat =
        """
        {
          "presentationTitle": "string",
          "presentationAuthor": "string",
          "overallStory": "string",
          "communicationIntent": "string",
          "keyThemes": ["string"],
          "targetAudience": "string",
          "tone": "string"
        }
        """;

    return openAIService.generateJsonCompletion(
        systemPrompt, userPrompt, responseFormat, "deck-analysis", presentationId);
  }

  /** Parse AI analysis result and create DeckAnalysis entity. */
  private DeckAnalysis parseDeckAnalysis(String analysisResult, Presentation presentation) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> analysisMap = objectMapper.readValue(analysisResult, Map.class);

      DeckAnalysis analysis = new DeckAnalysis();
      analysis.setPresentation(presentation);

      // Extract new fields
      analysis.setPresentationTitle((String) analysisMap.get("presentationTitle"));
      analysis.setPresentationAuthor((String) analysisMap.get("presentationAuthor"));

      // Extract existing fields
      analysis.setOverallStory((String) analysisMap.get("overallStory"));
      analysis.setCommunicationIntent((String) analysisMap.get("communicationIntent"));

      // Convert key themes array to JSON string
      @SuppressWarnings("unchecked")
      List<String> keyThemes = (List<String>) analysisMap.get("keyThemes");
      analysis.setKeyThemes(objectMapper.writeValueAsString(keyThemes));

      analysis.setTargetAudience((String) analysisMap.get("targetAudience"));
      analysis.setTone((String) analysisMap.get("tone"));

      // Set metadata
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("analysisDate", new Date());
      metadata.put("slidesAnalyzed", presentation.getSlides().size());
      analysis.setAnalysisMetadata(objectMapper.writeValueAsString(metadata));

      analysis.setModelUsed(modelName);
      analysis.setPromptVersion("1.0");

      return analysis;

    } catch (Exception e) {
      log.error("Failed to parse deck analysis result", e);
      throw new RuntimeException("Failed to parse deck analysis", e);
    }
  }
}
