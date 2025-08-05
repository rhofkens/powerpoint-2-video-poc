# Increment 4: AI Integration & Content Analysis

## Overview
This increment focuses on integrating AI capabilities to analyze PowerPoint presentations and generate engaging narratives with emotional context. The implementation follows the two-step process outlined in the AI steps guidelines: first analyzing user intent at both deck and slide levels, then creating fluid narratives with instructions for video avatar rendering.

## Detailed Scope

### 1. Workflow Status Management
- **Add new status values** to the presentation processing workflow:
  - `INTENT_ANALYSIS_COMPLETE` - After deck and slide analysis is finished
  - `NARRATIVE_COMPLETE` - After narrative generation is complete
- **Update status progression**:
  - From `RENDERING_READY` → `INTENT_ANALYSIS_COMPLETE` → `NARRATIVE_COMPLETE` → `COMPLETED`
- **Implement status transitions** in the backend service layer
- **Update frontend** to display new status states in the workflow

### 2. Spring AI and OpenAI Integration
- **Configure Spring AI framework** with OpenAI as the primary LLM provider
- **Set up API credentials** and connection parameters in application properties
- **Implement service layer** for AI interactions with proper error handling and retry logic
- **Create DTOs** for AI request/response handling

### 3. Deck-Level Analysis (Step 1.1 from AI Guidelines)
- **Implement overall deck analysis service** that processes the entire presentation
- **Extract and structure data** in JSON format:
  - All extracted text from slides
  - Speaker notes
  - Slide titles
  - Slide numbers
- **Generate overall story and communication intent** using LLM
- **Store analysis results** in database with proper schema

### 4. Single Slide Analysis (Step 1.2 from AI Guidelines)
- **Implement multimodal analysis service** for individual slides
- **Process slide images** along with text content
- **Extract and categorize visual concepts** (timelines, process flows, comparisons, lists, data charts, architecture diagrams)
- **Generate slide-specific insights**:
  - General message (2-3 sentences)
  - Visual concepts with structured details
- **Store slide analysis** in database linked to slide entities

### 5. Narrative Generation (Step 2 from AI Guidelines)
- **Implement narrative generation service** that creates fluid, engaging narratives
- **Consider context** from surrounding slides (previous and next) for cohesive flow
- **Generate narratives with**:
  - Main content based on analysis
  - Emotional indicators for delivery style
  - Instructions for video avatar rendering
  - Transition phrases when referencing other slides
- **Return structured JSON** with narrative and metadata
- **Store narratives** in database with versioning support

### 6. Frontend Integration
- **Create API endpoints** for triggering analysis and retrieving results
- **Implement data display components** for:
  - Deck analysis results
  - Individual slide analysis
  - Generated narratives with emotion indicators
- **Add progress tracking** for long-running AI operations
- **Update workflow status display** to show new AI processing states

### 7. Prompt Engineering
- **Design and optimize prompts** for each AI task:
  - Deck analysis prompt
  - Slide analysis prompt with visual concept guidelines
  - Narrative generation prompt with emotion and flow instructions
- **Implement prompt templates** with variable substitution
- **Create configuration** for easy prompt updates

### Explicitly Excluded from This Increment:
- Audio generation (ElevenLabs integration)
- Video avatar generation (D-ID integration)
- Final video compilation
- Narrative editing functionality (moved to increment 5)

## Detailed Acceptance Criteria

### 1. Workflow Status Updates
- [ ] New status values added to presentation entity
- [ ] Status transitions properly enforced in service layer
- [ ] Frontend displays correct status at each stage
- [ ] Status changes trigger appropriate UI updates
- [ ] Workflow progresses correctly: RENDERING_READY → INTENT_ANALYSIS_COMPLETE → NARRATIVE_COMPLETE → COMPLETED

### 2. AI Integration Setup
- [ ] Spring AI dependency added and configured in pom.xml
- [ ] OpenAI API key securely stored in environment variables
- [ ] AI service can successfully call OpenAI API
- [ ] Proper error handling for API failures (rate limits, timeouts)
- [ ] Configuration allows switching between different OpenAI models

### 3. Deck Analysis Functionality
- [ ] Endpoint `/api/presentations/{id}/analyze-deck` triggers deck analysis
- [ ] Analysis processes all slides in the presentation
- [ ] Results include overall story arc and communication intent
- [ ] Analysis data stored in `deck_analysis` table
- [ ] Processing time for 10-slide deck < 30 seconds

### 3. Slide Analysis Functionality
- [ ] Endpoint `/api/slides/{id}/analyze` triggers individual slide analysis
- [ ] Multimodal analysis processes both image and text content
- [ ] Visual concepts correctly identified and categorized
- [ ] Detailed concept information extracted (e.g., timeline dates, process steps)
- [ ] Analysis results stored in `slide_analysis` table
- [ ] Each slide analysis completes in < 5 seconds

### 4. Narrative Generation
- [ ] Endpoint `/api/slides/{id}/generate-narrative` creates narratives
- [ ] Narratives include main content and emotional indicators
- [ ] Context from surrounding slides incorporated when relevant
- [ ] Avatar rendering instructions included in output
- [ ] Narratives stored in `slide_narratives` table
- [ ] Generation time per slide < 3 seconds

### 5. Frontend Display
- [ ] Analysis results displayed in expandable cards
- [ ] Visual concept badges shown for each slide
- [ ] Narratives displayed with emotion indicators (color coding or icons)
- [ ] Progress bars show analysis status
- [ ] Loading states properly handled

### 7. Data Validation
- [ ] All generated content validated for completeness
- [ ] JSON responses follow defined schemas
- [ ] No null or empty narratives generated
- [ ] Emotion indicators present for all narratives
- [ ] Visual concepts have required detail fields

## Detailed Documentation Tasks

### 1. API Documentation
- **Document all new endpoints** in `shared/api-docs/`:
  - `POST /api/presentations/{id}/analyze-deck`
  - `POST /api/slides/{id}/analyze`
  - `POST /api/slides/{id}/generate-narrative`
  - `GET /api/presentations/{id}/analysis`
  - `GET /api/slides/{id}/analysis`
  - `GET /api/slides/{id}/narrative`

### 2. Database Schema Documentation
- **Update database documentation** with new tables:
  - `deck_analysis` table structure
  - `slide_analysis` table structure
  - `slide_narratives` table structure
  - Relationships and indexes

### 3. AI Integration Guide
- **Create `docs/guidelines/ai-integration-guide.md`** covering:
  - OpenAI setup and configuration
  - Prompt template structure
  - Visual concept categories and details
  - Emotion indicator definitions
  - Troubleshooting common issues

### 4. User Guide Updates
- **Document the analysis workflow** for end users:
  - How to trigger analysis
  - Understanding analysis results
  - Interpreting visual concepts
  - Reading emotion indicators in narratives

### 5. Technical Architecture Updates
- **Update `architecture.md`** with:
  - AI service layer design
  - Data flow for analysis and generation
  - Caching strategy for AI responses
  - Error handling approach

## Implementation Notes

### Technology Considerations
- Use Spring AI 1.0.0 or later for OpenAI integration
- Consider implementing caching for repeated analyses
- Implement circuit breaker pattern for API resilience
- Use async processing for better user experience

### Testing Approach
- Unit tests for prompt generation
- Integration tests with mock AI responses
- End-to-end tests for complete analysis flow
- Performance tests for concurrent analyses

### Security Considerations
- API keys stored securely, never in code
- Rate limiting on analysis endpoints
- Input validation to prevent prompt injection
- Audit logging for AI operations

## Dependencies
- Completion of Increment 3 (MS Graph API Slide Rendering) for high-quality images
- Existing slide data and structure from Increment 2
- Frontend state management from previous increments