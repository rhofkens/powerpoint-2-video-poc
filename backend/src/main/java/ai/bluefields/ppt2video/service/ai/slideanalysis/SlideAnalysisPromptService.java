package ai.bluefields.ppt2video.service.ai.slideanalysis;

import ai.bluefields.ppt2video.entity.Slide;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for building prompts for slide analysis. Handles prompt construction and data
 * preparation for AI analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideAnalysisPromptService {

  private final ObjectMapper objectMapper;

  /**
   * Prepare slide data for AI analysis.
   *
   * @param slide The slide to analyze
   * @param totalSlides Total number of slides in presentation
   * @return Map containing slide data
   */
  public Map<String, Object> prepareSlideData(Slide slide, int totalSlides) {
    Map<String, Object> slideData = new HashMap<>();
    slideData.put("slideNumber", slide.getSlideNumber());
    slideData.put("totalSlides", totalSlides);
    slideData.put("isFirstSlide", slide.getSlideNumber() == 1);
    slideData.put("isLastSlide", slide.getSlideNumber() == totalSlides);
    slideData.put("title", slide.getTitle());
    slideData.put("contentText", slide.getContentText());
    slideData.put("speakerNotes", slide.getSpeakerNotes());
    slideData.put("layoutType", slide.getLayoutType());
    return slideData;
  }

  /**
   * Convert slide data to JSON string.
   *
   * @param slideData The slide data map
   * @return JSON string representation
   */
  public String toJson(Map<String, Object> slideData) {
    try {
      return objectMapper.writeValueAsString(slideData);
    } catch (Exception e) {
      log.error("Failed to convert slide data to JSON", e);
      throw new RuntimeException("Failed to prepare slide data", e);
    }
  }

  /**
   * Get the system prompt for slide analysis.
   *
   * @return The system prompt
   */
  public String getSystemPrompt() {
    return """
        You are an expert presentation analyst specializing in visual communication and data visualization.
        Analyze the provided slide image and text data to extract key insights and visual concepts.

        Focus on:
        1. The general message conveyed by the slide (2-3 sentences)
        2. Visual concepts used - identify ALL that apply from the extensive list below
        3. Key points and takeaways
        4. Data insights if charts or graphs are present
        5. How this slide transitions or relates to the presentation flow

        SPECIAL SLIDE TYPES - Pay special attention to context:
        - INTRO SLIDES (isFirstSlide=true): Usually contain title, presenter info, agenda, or overview
          - Focus on: presentation topic, scope, presenter credibility, audience expectations
          - Emphasis level typically: MEDIUM (sets stage but not climactic)
        - THANK YOU SLIDES (isLastSlide=true): Usually contain closing, contact info, or call-to-action
          - Focus on: closing message, next steps, contact details, memorable takeaway
          - Emphasis level typically: MEDIUM to HIGH (memorable closing)
        - SEPARATOR SLIDES: Section dividers that bridge content sections
          - Focus on: what section just ended, what section is starting, transition logic
          - Common patterns: "Recap + Preview", "Questions before moving on", "Part X: Topic"
          - Emphasis level typically: LOW to MEDIUM (transitional, not content-heavy)

        """
        + getVisualConceptsCatalog();
  }

  /**
   * Get the user prompt for slide analysis.
   *
   * @param slideDataJson The slide data in JSON format
   * @return The user prompt
   */
  public String getUserPrompt(String slideDataJson) {
    return String.format(
        """
        Analyze this slide with both its visual elements and text content:

        Slide Data:
        %s

        Respond with a JSON object containing:
        {
          "generalMessage": "2-3 sentences describing the slide's main message",
          "visualConcepts": [
            {
              "type": "CONCEPT_TYPE",
              "confidence": "HIGH|MEDIUM|LOW",
              "position": {
                "vertical": "TOP|CENTER|BOTTOM",
                "horizontal": "LEFT|CENTER|RIGHT"
              },
              "prominence": "PRIMARY|SECONDARY|SUPPORTING",
              "details": {
                // Extensive concept-specific details based on type
                // For example, for TIMELINE: startDate, endDate, milestones array
                // For SWOT: strengths array, weaknesses array, opportunities array, threats array
                // For KPI_DASHBOARD: metrics array with name, value, target, trend for each
              }
            }
          ],
          "keyPoints": ["point1", "point2", "point3"],
          "dataInsights": "Insights from any data visualizations (null if none)",
          "transitionContext": "How this slide relates to the presentation flow",
          "emphasisLevel": "HIGH|MEDIUM|LOW based on visual prominence"
        }

        IMPORTANT:
        - Identify ALL visual concepts present, not just the primary one
        - Provide rich, detailed attributes for each concept
        - Be specific with data values, dates, percentages, etc.
        - Include position and prominence for layout understanding
        """,
        slideDataJson);
  }

  /**
   * Get the response format schema for structured output.
   *
   * @return The response format schema
   */
  public String getResponseFormatSchema() {
    return """
        {
          "generalMessage": "string",
          "visualConcepts": [
            {
              "type": "string",
              "confidence": "string",
              "position": {
                "vertical": "string",
                "horizontal": "string"
              },
              "prominence": "string",
              "details": {}
            }
          ],
          "keyPoints": ["string"],
          "dataInsights": "string or null",
          "transitionContext": "string",
          "emphasisLevel": "string"
        }
        """;
  }

  /** Get the visual concepts catalog portion of the system prompt. */
  private String getVisualConceptsCatalog() {
    return """
        VISUAL CONCEPTS CATALOG - Identify all that apply with detailed information:

        STRUCTURAL CONCEPTS:
        - TIMELINE: Linear progression of events (include: startDate, endDate, milestones with dates/descriptions, orientation)
        - PROCESS_FLOW: Step-by-step process (include: numberOfSteps, stepNames, flowDirection, decisionPoints, loops)
        - HIERARCHY: Organizational or taxonomic structure (include: levels, topNode, branches, nodeCount)
        - MATRIX: 2x2 or larger grid analysis (include: dimensions, quadrantLabels, items in each quadrant)
        - CYCLE: Circular/recurring process (include: numberOfStages, stageName, direction, startingPoint)

        COMPARISON CONCEPTS:
        - COMPARISON_TABLE: Side-by-side comparison (include: items, criteria, winner/recommendation)
        - PROS_CONS: Advantages vs disadvantages (include: subject, prosList, consList, conclusion)
        - BEFORE_AFTER: State transformation (include: initialState, finalState, changesHighlighted)
        - VENN_DIAGRAM: Overlapping sets (include: sets, intersections, unique elements)

        DATA VISUALIZATION:
        - BAR_CHART: Categorical data comparison (include: categories, values, unit, orientation)
        - LINE_CHART: Trend over time (include: timeRange, metrics, trendDirection, keyPoints)
        - PIE_CHART: Part-to-whole relationships (include: segments, percentages, total)
        - SCATTER_PLOT: Correlation analysis (include: xAxis, yAxis, correlation, clusters)
        - GAUGE_CHART: Performance metrics (include: metric, currentValue, target, zones)
        - HEATMAP: Intensity matrix (include: dimensions, scale, hotspots)

        BUSINESS CONCEPTS:
        - SWOT_ANALYSIS: Strengths, Weaknesses, Opportunities, Threats (include: all four quadrants)
        - BUSINESS_MODEL: Canvas or framework (include: components, keyPartnerships, valueProposition)
        - ROADMAP: Future planning timeline (include: timeHorizon, phases, milestones, currentStatus)
        - KPI_DASHBOARD: Key metrics display (include: metrics, values, targets, trends)
        - FUNNEL: Conversion or process funnel (include: stages, conversionRates, dropoffPoints)
        - CUSTOMER_JOURNEY: User experience map (include: touchpoints, emotions, painPoints, opportunities)

        INFORMATIONAL CONCEPTS:
        - BULLET_LIST: Simple enumeration (include: topic, items, numbering, hierarchy)
        - DEFINITION: Concept explanation (include: term, definition, examples, relatedConcepts)
        - QUOTE: Featured quotation (include: quote, author, context, emphasis)
        - STATISTICS: Key numbers highlight (include: metrics, values, comparisons, significance)
        - ICON_GRID: Visual representation grid (include: icons, labels, grouping, meaning)
        - INFOGRAPHIC: Data storytelling (include: narrative, dataPoints, visualElements)

        RELATIONAL CONCEPTS:
        - MIND_MAP: Central concept with branches (include: centralIdea, branches, connections)
        - NETWORK_DIAGRAM: Interconnected nodes (include: nodes, edges, clusters, centralNodes)
        - FLOWCHART: Decision tree or logic flow (include: startPoint, decisions, endpoints, conditions)
        - DEPENDENCY_MAP: Task or system dependencies (include: items, dependencies, criticalPath)

        SPECIALIZED CONCEPTS:
        - GANTT_CHART: Project timeline (include: tasks, durations, dependencies, milestones)
        - RISK_MATRIX: Probability vs impact (include: risks, quadrants, mitigationStrategies)
        - PRICING_TABLE: Product/service tiers (include: tiers, features, prices, recommended)
        - TEAM_STRUCTURE: Organizational layout (include: roles, reportingLines, departments)
        - CALL_TO_ACTION: Action prompt (include: action, urgency, benefits, contactInfo)
        - AGENDA: Meeting or presentation outline (include: topics, timeAllocations, currentItem)
        - SUMMARY: Key takeaways (include: mainPoints, conclusions, nextSteps)
        - QUESTION_SLIDE: Q&A or discussion prompt (include: questions, context, expectedResponses)
        - INTRO_SLIDE: Opening slide with title/overview (include: presentationTitle, presenter, agenda, scope)
        - THANK_YOU_SLIDE: Closing slide with thanks/contact (include: closingMessage, contactInfo, nextSteps, callToAction)
        - SEPARATOR_SLIDE: Section divider/transition slide (include: previousSectionSummary, upcomingSectionPreview, transitionMessage, sectionTitles)

        For each identified concept, provide:
        1. The concept type
        2. Confidence level (HIGH/MEDIUM/LOW)
        3. Detailed attributes specific to that concept type
        4. Position on slide (TOP/CENTER/BOTTOM, LEFT/CENTER/RIGHT)
        5. Visual prominence (PRIMARY/SECONDARY/SUPPORTING)
        """;
  }
}
