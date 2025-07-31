# Product Requirements Document
## PowerPoint to Video Story POC

### 1. Introduction / Overview

**Product Name:** PowerPoint to Video Story POC

**Description:** A proof-of-concept system that automatically converts PowerPoint presentations into narrative-styled videos with configurable emotional tones (business, funny, cynical), leveraging AI for content analysis and video generation.

**Problem/Opportunity:** Validating the technical feasibility of automated presentation-to-video conversion with narrative styling and different emotional tones, while assessing quality of libraries, processing times, and resource usage.

**Primary User:** Internal team/developers to validate technical feasibility before building a full product

### 2. Goals & Objectives

**Primary Goals:**
1. Prove the documented system flow works
2. Get insights into library quality for presentation parsing, AI analysis, TTS, and video generation
3. Achieve "narrative styling" with different emotions (serious business, funny, cynical)
4. Validate TTS (ElevenLabs) and avatar video creation (D-ID) providers for suitability
5. Understand processing times and resource usage for long-running tasks

**Business Benefits:**
- Time savings: Demonstrate that automated video generation is significantly faster than manual video creation from presentations
- Cost reduction: Prove that AI-driven conversion is more cost-effective than hiring video production teams

**User Benefits (for internal development team):**
- Clear validation of technical stack before full product development
- Understanding of system capabilities and limitations
- Performance benchmarks for planning production system

### 3. Target Audience / User Personas

**Primary Target Users:** Internal development team consisting of:

1. **Technical Architects**
   - Key needs: Evaluate if the proposed architecture can scale to production with acceptable performance and costs
   - Pain points: Need concrete data on processing times, resource usage, and system reliability before committing to architecture decisions

2. **Product Managers**
   - Key needs: Validate if AI-generated narratives meet quality standards for customer-facing products
   - Pain points: Need to ensure the different narrative styles (business, funny, cynical) are appropriate and effective for end users

3. **Full-stack Developers**
   - Key needs: Assess integration complexity and identify potential technical challenges early
   - Pain points: Need to understand the complexity of integrating multiple AI services, handling long-running tasks, and managing the data flow

4. **DevOps Engineers**
   - Key needs: Understand resource requirements and infrastructure needs for deployment
   - Pain points: Need to plan for scalable infrastructure that can handle concurrent processing of multiple presentations

### 4. User Stories / Requirements

**User Stories:**

1. **As a** developer, **I want to** upload a PowerPoint file **so that** I can start the automated video generation process.

2. **As a** developer, **I want to** select a narrative style (business, funny, cynical) **so that** I can test different emotional tones in the generated videos.

3. **As a** developer, **I want to** see the parsing run status **so that** I know the system is processing my file.

4. **As a** developer, **I want to** stop and restart a parsing run **so that** I can abort failed attempts and try again.

5. **As a** technical architect, **I want to** see extracted text, notes, and PNG exports per slide **so that** I can verify the parsing accuracy.

6. **As a** product manager, **I want to** review the AI's conceptual analysis of each slide **so that** I can validate the quality of content understanding.

7. **As a** developer, **I want to** generate speech for individual slides **so that** I can test TTS quality without processing the entire presentation.

8. **As a** developer, **I want to** generate video with speech for individual slides **so that** I can test avatar video creation quality.

9. **As a** developer, **I want to** refresh the slides data view **so that** I can see updates as processing completes.

10. **As a** developer, **I want to** generate a full story video **so that** I can evaluate the complete end-to-end result.

**Functional Requirements:**
- System must parse PPTX files and extract text, notes, and visual elements
- System must export PNG versions of each slide
- System must analyze slides using LLM for conceptual understanding
- System must generate contextual narratives with transitions between slides
- System must support three narrative styles: business, funny, cynical
- System must integrate with TTS and video generation providers
- System must store all extracted and generated data in a database

**Non-functional Requirements:**
- System should handle long-running tasks gracefully
- Processing status should be persistable and retrievable
- System should be able to process presentations of varying lengths

### 5. Proposed Solution / Features

**Core Features:**
1. **File Upload & Style Selection**
   - PowerPoint file upload interface
   - Style selector (business, funny, cynical)
   - Upload status indicator

2. **Presentation Processing Engine**
   - PPTX parser to extract text and notes
   - PNG export functionality for each slide
   - LLM integration for conceptual analysis
   - Narrative generation with style-specific tone
   - Database storage for all extracted/generated content

3. **Results Visualization Dashboard**
   - Grid-based slide viewer with columns for:
     - Large-format slide thumbnails
     - Extracted text (with modal for full view)
     - Extracted notes (with modal for full view)
     - Conceptual analysis (with modal for full view)
     - Generated narrative (with modal for full view)
   - Individual slide actions (generate speech, generate video)
   - Refresh capability for status updates

4. **Media Generation Integration**
   - ElevenLabs API integration for speech generation
   - D-ID API integration for avatar video creation
   - Full presentation video compilation

**Technology Stack:**
- Frontend: React 18.x, TailwindCSS 3.x, shadcn, Vite (already created via Lovable)
- Backend: Spring Boot 3.5.4, Java 24, Spring AI 1.0.1, PostgreSQL 17, Maven
- AI/LLM: OpenAI with GPT-4o-mini (with potential OpenRouter integration for other models)
- Voice Generation: ElevenLabs API
- Video Generation: D-ID API (https://www.d-id.com/api/)

**Workflow:**
Upload → Parse → Analyze → Generate Narratives → Display Results → Generate Media → Compile Full Video

**UI/UX:** The UI is a simple but professional looking SPA with React 18.x, TailwindCSS 3.x, shadcn, Vite. It has already been created using Lovable and will be added as a static UI to the frontend project.

### 6. Success Metrics / KPIs

**Key Performance Indicators:**

1. **Processing Time Metrics**
   - Average processing time per slide (target: establish baseline)
   - Total presentation processing time for various presentation sizes
   - Time breakdown by operation (parsing, LLM analysis, narrative generation)
   - Measured through: System timestamps logged at each processing stage

2. **Quality Metrics**
   - Narrative quality score (1-10 scale) for each style:
     - Business style average score (target: ≥7/10)
     - Funny style average score (target: ≥7/10)
     - Cynical style average score (target: ≥7/10)
   - Measured through: Manual review by product team

3. **Reliability Metrics**
   - Overall success rate (target: ≥80% successful completions)
   - Error rate by component (parsing, LLM, TTS, video generation)
   - Types of errors encountered and their frequency
   - Measured through: System logs and error tracking

**Data Collection:**
- Automated logging for all processing times and errors
- Manual scoring sheet for narrative quality assessment
- Dashboard or report to visualize metrics

### 7. Assumptions & Constraints

**Technical Constraints:**
- **Backend Stack (Mandatory):**
  - Spring Boot 3.5.4
  - Java 24
  - Spring AI 1.0.1
  - PostgreSQL 17
  - Maven build system
  - OpenAI with GPT-4o-mini (with potential OpenRouter integration for other models)

- **Frontend Stack (Mandatory):**
  - React 18.x
  - TailwindCSS 3.x
  - shadcn components
  - Single Page Application (SPA)
  - Vite build tool
  - Must maintain Lovable's setup configuration

**UX/Scope Constraints:**
- No authentication, user management, or session management
- No optimization for long-running task UX
- No real-time updates (no websockets, events) - simple polling/refresh only
- Frontend UI already created via Lovable

**Assumptions:**
- Presentations will be of reasonable size for POC testing
- Access to necessary AI/ML APIs is available
- TTS and avatar video generation providers have APIs that can be integrated
- The system will have sufficient resources to handle concurrent processing
- File storage will be handled on the local filesystem during POC

**Business Constraints:**
- This is a POC only - not intended for production use
- Focus is on technical validation, not user experience optimization

### 8. Out of Scope (What We Are Not Doing)

**Explicitly Out of Scope for this POC:**

**User Management & Security:**
- Authentication and authorization
- User accounts and session management
- Multi-user support or concurrent user access
- User roles and permissions

**Advanced Features:**
- Presentation management (saving, organizing, history)
- Batch processing of multiple presentations
- Advanced video editing capabilities
- Custom avatar selection or configuration
- Voice customization or selection
- Analytics and reporting dashboards
- API endpoints for external system integration

**UX Optimizations:**
- Real-time updates (websockets, server-sent events)
- Optimized UI for long-running tasks
- Progressive status indicators
- Background job management UI

**Production Features:**
- Error recovery and retry mechanisms
- Queue management for processing
- Horizontal scaling capabilities
- Performance optimization
- Data persistence beyond POC testing

**Content Features:**
- Support for presentations in languages other than English
- Custom branding or theming of generated videos
- Integration with cloud storage services
- Export to various video formats