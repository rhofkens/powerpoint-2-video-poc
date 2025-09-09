# LLM-Based Veo Prompt Generation Implementation Plan

## Problem Statement

Current intro videos generated using static templates in `VeoPromptBuilder` are producing poor quality results with text artifacts and non-abstract visuals. We need to implement an LLM-based approach to generate better visualization prompts.

## Solution Overview

Replace the static template-based prompt generation with an OpenAI LLM call that takes deck analysis data and generates high-quality, abstract visualization prompts specifically optimized for Google Veo API.

## Architecture Design

### 1. New Service: `VeoPromptLLMService`

**Location**: `ai.bluefields.ppt2video.service.intro.VeoPromptLLMService`

**Responsibilities**:

- Take deck analysis and color palette as input
- Call OpenAI to generate visualization prompt
- Return structured VeoPrompt object
- Handle errors with fallback to existing VeoPromptBuilder

**Key Methods**:

```java
public VeoPrompt generatePromptWithLLM(DeckAnalysis deckAnalysis, ColorPaletteDto colorPalette)
public VeoPrompt generatePromptWithLLMAsync(DeckAnalysis deckAnalysis, ColorPaletteDto colorPalette)
```

### 2. OpenAI Integration

**Model**: Use GPT-5-mini for efficient prompt generation
**Configuration**:

```properties
app.ai.models.veo-prompt.model=gpt-5-mini
app.ai.models.veo-prompt.temperature=0.8
app.ai.models.veo-prompt.max-completion-tokens=2048
app.ai.models.veo-prompt.timeout-seconds=30
```

### 3. Prompt Engineering Template

The LLM prompt will be structured to:

- Provide context about Google Veo capabilities
- Include deck analysis insights
- Specify color palette information
- Request abstract, text-free visualizations
- Enforce Google Veo best practices

**System Prompt**:

```text
You are an expert at creating prompts for Google Veo AI video generation API. You specialize in creating abstract, professional intro videos for business presentations.

CRITICAL RULES:
- NEVER include text, words, logos, or written content in the video
- Focus on abstract visualizations, geometric shapes, particle effects
- Use flowing, organic movements and transformations
- Keep descriptions concise (under 1024 tokens)
- Follow Google Veo prompt structure guidelines
```

**User Prompt Template**:

```text
Create a Google Veo prompt for an 8-second intro video based on this presentation:

Topic: {overall_story}
Communication Intent: {communication_intent}
Target Audience: {target_audience}
Tone: {tone}
Key Themes: {key_themes}
Color Palette: {dominant_colors}
Color Characteristics: Brightness: {brightness}, Saturation: {saturation}

Requirements:
1. Create abstract visualizations that metaphorically represent the topic
2. Use the provided color palette
3. Include smooth camera movements
4. Specify lighting and ambiance
5. NO text, words, or logos
6. Format as: Subject, Action, Style, Camera, Composition, Focus, Ambiance, Audio

Example of good output:
"Subject: Abstract geometric shapes morphing into flowing particles...
Action: Smooth transformations, pulsating rhythms...
Style: Modern motion graphics, minimalist aesthetic..."
```

### 4. Response Processing

**Expected LLM Response Structure**:

```json
{
  "prompt": "Subject: ... Action: ... Style: ...",
  "negative_prompt": "text, words, logos, faces...",
  "metadata": {
    "main_visual_concept": "flowing data streams",
    "dominant_motion": "orbital camera movement",
    "visual_complexity": "medium"
  }
}
```

### 5. Integration Points

#### Update `IntroVideoGenerationService`

```java
@Service
public class IntroVideoGenerationService {
    private final VeoPromptLLMService veoPromptLLMService;
    private final VeoPromptBuilder veoPromptBuilder; // Fallback
    
    @Value("${app.ai.veo-prompt.llm-enabled:true}")
    private boolean llmPromptEnabled;
    
    public IntroVideoResponse generateIntroVideo(IntroVideoRequest request) {
        // ... existing code ...
        
        VeoPrompt prompt;
        if (llmPromptEnabled) {
            try {
                prompt = veoPromptLLMService.generatePromptWithLLM(deckAnalysis, colorPalette);
            } catch (Exception e) {
                log.warn("LLM prompt generation failed, falling back to template", e);
                prompt = veoPromptBuilder.buildPrompt(deckAnalysis, colorPalette);
            }
        } else {
            prompt = veoPromptBuilder.buildPrompt(deckAnalysis, colorPalette);
        }
        
        // ... continue with Veo API call ...
    }
}
```

### 6. Error Handling & Fallback Strategy

- **Primary**: Use LLM-generated prompts
- **Fallback 1**: On LLM timeout/error, use enhanced static templates
- **Fallback 2**: Use minimal safe prompt if all else fails
- **Monitoring**: Log prompt generation metrics and success rates

### 7. Quality Assurance

#### Prompt Validation

Before sending to Veo API, validate:

- No text-related keywords in prompt
- Token count under limit
- Required fields present
- Negative prompt includes text exclusions

### 8. Database Schema Update

Track prompt generation method:

```sql
ALTER TABLE intro_videos 
ADD COLUMN prompt_generation_method VARCHAR(50) DEFAULT 'TEMPLATE',
ADD COLUMN prompt_generation_time_ms BIGINT,
ADD COLUMN prompt_text TEXT;
```

### 9. Monitoring & Metrics

- Track LLM prompt generation time
- Monitor fallback rate
- Compare video generation success rates (LLM vs template)
- Log prompt quality scores (if available from Veo)

## Implementation Steps

### Phase 1: Core Implementation (2-3 days)

1. Create `VeoPromptLLMService` class
2. Implement OpenAI integration for prompt generation
3. Design and test prompt engineering template
4. Add configuration properties
5. Implement basic error handling

### Phase 2: Integration (1-2 days)

1. Update `IntroVideoGenerationService` to use LLM service
2. Implement fallback mechanism
3. Add feature flag for enabling/disabling
4. Update database schema
5. Test end-to-end flow

### Phase 3: Optimization (1-2 days)

1. Enhance prompt validation
2. Add monitoring metrics
3. Performance testing
4. Fine-tune prompt templates
5. Implement retry logic

### Phase 4: Refinement (Ongoing)

1. Collect feedback on video quality
2. Iterate on prompt engineering
3. Adjust temperature and model parameters
4. Build prompt library from successful generations

## Configuration Properties to Add

```properties
# LLM Prompt Generation
app.ai.veo-prompt.llm-enabled=true
app.ai.veo-prompt.model=gpt-5-mini
app.ai.veo-prompt.temperature=0.8
app.ai.veo-prompt.max-completion-tokens=2048
app.ai.veo-prompt.timeout-seconds=30
```

## Success Criteria

1. **Quality**: 80% reduction in text artifacts in generated videos
2. **Performance**: LLM prompt generation < 3 seconds
3. **Reliability**: < 5% fallback rate to template-based prompts
4. **Variety**: Each presentation gets unique, contextually relevant visuals
5. **Cost**: Keep OpenAI API costs under $0.05 per video generation

## Risk Mitigation

1. **LLM Latency**: Implement aggressive timeouts and async processing
2. **Cost Overrun**: Monitor API usage, implement rate limiting
3. **Prompt Injection**: Validate and sanitize deck analysis before LLM
4. **Quality Regression**: Keep existing template system as fallback
5. **API Failures**: Implement circuit breaker pattern

## Example Implementation Flow

```text
1. User requests intro video generation
2. System retrieves deck analysis and color palette
3. Prepare context from deck analysis
4. Call OpenAI with engineered prompt
5. Parse and validate response
6. Send prompt to Veo API
7. Monitor generation and store results
```

## Next Steps

1. Review and approve this plan
2. Create feature branch `feature/llm-veo-prompts`
3. Implement Phase 1 components
4. Test with sample presentations
5. Iterate based on video quality results
