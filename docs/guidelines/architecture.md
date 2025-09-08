# Architecture Document V2
## PowerPoint to Video Story POC

### 1. Overview

This document defines the current technical architecture for the PowerPoint to Video Story POC system. The system automatically converts PowerPoint presentations into narrative-styled videos with AI-generated content, leveraging multiple rendering strategies, cloud storage, and avatar video generation capabilities.

**Document Scope:** This architecture document covers the high-level system design, technology stack, component structures, and deployment approach for the current POC implementation.

### 2. Technology Stack

The following technology stack is currently implemented:

#### Frontend
- **React** 18.x - Component-based UI framework
- **TailwindCSS** 3.x - Utility-first CSS framework
- **shadcn/ui** - Pre-built UI components
- **Vite** - Build tool and development server
- **TypeScript** - Type safety for JavaScript
- **Zustand** - State management
- **Axios** - HTTP client for API communication

#### Backend
- **Spring Boot** 3.5.4 - Application framework
- **Java** 24 - Programming language
- **Spring AI** 1.0.1 - AI/LLM integration framework
- **PostgreSQL** 17 - Primary database
- **Maven** - Build and dependency management
- **Flyway** - Database migration management

#### External Services
- **OpenAI GPT-4o-mini** - LLM for content analysis and narrative generation
- **ElevenLabs API** - Text-to-speech generation with emotional enhancement
- **HeyGen API** - Avatar video generation (replaced D-ID)
- **Microsoft Graph API** - PowerPoint rendering via OneDrive
- **Cloudflare R2** - Object storage for media assets
- **Aspose.Slides** - Alternative PowerPoint parsing/rendering

### 3. System Architecture

#### 3.1 Architecture Style
The system follows a **modular monolithic architecture** pattern with:
- Clear separation of concerns through service layers
- Multiple rendering and parsing strategies
- Asynchronous processing for long-running tasks
- Cloud storage integration for scalability

#### 3.2 High-Level Architecture

```
┌─────────────────┐     ┌──────────────────────────────────┐     ┌─────────────────┐
│                 │     │         Spring Boot Backend      │     │                 │
│  React SPA      │────▶│  ┌────────────────────────────┐  │────▶│  PostgreSQL 17  │
│  (Vite/TS)      │     │  │     REST Controllers       │  │     │   + Flyway      │
│                 │     │  └────────────────────────────┘  │     └─────────────────┘
└─────────────────┘     │  ┌────────────────────────────┐  │
                        │  │     Service Layer          │  │     ┌─────────────────┐
                        │  │  - Orchestrators           │  │────▶│  Cloudflare R2  │
                        │  │  - Rendering Strategies    │  │     │  Object Storage │
                        │  │  - AI Services             │  │     └─────────────────┘
                        │  └────────────────────────────┘  │
                        │  ┌────────────────────────────┐  │     ┌─────────────────┐
                        │  │    Spring AI Integration   │  │────▶│  MS Graph API   │
                        │  └────────────────────────────┘  │     │   (OneDrive)    │
                        └────────────│─────────────────────┘     └─────────────────┘
                                     │
                        ┌────────────┴─────────────────────────────┐
                        │                                          │
                  ┌─────▼─────┐  ┌─────────┐  ┌──────────┐  ┌────▼────┐
                  │  OpenAI   │  │ElevenLabs│  │  HeyGen  │  │ Aspose  │
                  │GPT-4o-mini│  │   API    │  │   API    │  │ Slides  │
                  └───────────┘  └─────────┘  └──────────┘  └─────────┘
```

### 4. Core Components & Services

#### 4.1 Presentation Processing Pipeline
- **PresentationUploadService** - Handles file uploads and initial processing
- **PresentationParsingService** - Coordinates parsing strategies
- **SlideRenderingService** - Manages slide image generation
- **ParserFactory** - Selects appropriate parser (POI, Aspose)
- **RenderingStrategyFactory** - Selects rendering strategy (POI, Aspose, MS Graph)

#### 4.2 AI Analysis Services
- **DeckAnalysisService** - Analyzes entire presentation context
- **SlideAnalysisService** - Individual slide content analysis
- **BatchSlideAnalysisOrchestrator** - Batch processing for efficiency
- **OpenAIService** - Central LLM integration point

#### 4.3 Narrative Generation Services
- **NarrativeGenerationService** - Core narrative creation
- **BatchNarrativeOrchestrator** - Batch narrative generation
- **NarrativeOptimizationOrchestrator** - Narrative enhancement pipeline
- **ElevenLabsEmotionalEnhancer** - Adds emotional markers for TTS
- **TransitionOptimizationService** - Improves slide transitions
- **NarrativeContextService** - Manages narrative context

#### 4.4 Media Generation Services
- **TextToSpeechService** - ElevenLabs TTS integration
- **AvatarVideoService** - Avatar video generation orchestration
- **AvatarProviderFactory** - Selects avatar provider (HeyGen)
- **AvatarVideoMonitorService** - Monitors async video generation

#### 4.5 Storage Services
- **FileStorageService** - Local file system management
- **R2AssetService** - Cloudflare R2 integration
- **AssetMetadataService** - Tracks asset metadata
- **PresignedUrlService** - Manages presigned URLs for assets

### 5. Data Architecture

#### 5.1 Database Schema (Current)
- **presentations** - Core presentation metadata
- **slides** - Individual slide data
- **slide_images** - Generated slide images
- **deck_analysis** - Presentation-level AI analysis
- **slide_analysis** - Slide-level AI analysis
- **slide_narratives** - Generated narratives (original + enhanced)
- **slide_speeches** - TTS audio metadata
- **asset_metadata** - Cloud storage asset tracking
- **presigned_urls** - Cached presigned URLs
- **avatar_videos** - Avatar video generation tracking

#### 5.2 Storage Architecture
```
Local Storage:
/storage/
├── presentations/{presentation-id}/
│   ├── original.pptx
│   ├── slides/
│   └── temp/

Cloudflare R2:
├── presentations/{presentation-id}/
│   ├── slides/
│   │   ├── images/
│   │   └── audio/
│   └── avatar-videos/
```

### 6. Rendering Strategies

The system supports multiple rendering strategies for flexibility:

#### 6.1 MS Graph Strategy (Primary)
- Uploads to OneDrive
- Uses Microsoft's rendering engine
- Highest quality output
- Requires Azure authentication

#### 6.2 Aspose Strategy
- Java-based rendering
- Good compatibility
- Commercial license required

#### 6.3 POI Strategy (Fallback)
- Apache POI library
- Open source
- Limited formatting support

### 7. AI Processing Pipeline

#### 7.1 Analysis Flow
1. **Deck Analysis** - Overall presentation understanding
2. **Batch Slide Analysis** - Parallel slide content analysis
3. **Narrative Generation** - Context-aware narrative creation
4. **Emotional Enhancement** - ElevenLabs-specific optimization
5. **Transition Optimization** - Smooth narrative flow

#### 7.2 Orchestration Services
- **BatchNarrativeOrchestrator** - Manages batch processing
- **NarrativeOptimizationOrchestrator** - Coordinates enhancements
- **BatchSlideAnalysisOrchestrator** - Parallel slide analysis

### 8. External Service Integration

#### 8.1 OpenAI Integration
- **Library:** Spring AI 1.0.1
- **Model:** GPT-4o-mini with vision capabilities
- **Usage:** Content analysis, narrative generation, emotional enhancement

#### 8.2 ElevenLabs Integration
- **Protocol:** REST API
- **Features:** TTS with emotional markers (CAPS, ellipses, etc.)
- **Formats:** MP3 audio output

#### 8.3 HeyGen Integration
- **Protocol:** REST API with async processing
- **Features:** Avatar video generation with custom audio
- **Monitoring:** Polling-based status checking

#### 8.4 Microsoft Graph Integration
- **Authentication:** OAuth2 with Azure AD
- **Features:** OneDrive upload, PowerPoint rendering
- **Caching:** Token and file ID caching

#### 8.5 Cloudflare R2 Integration
- **Protocol:** S3-compatible API
- **Features:** Object storage, presigned URLs
- **Organization:** Hierarchical folder structure

### 9. API Structure

#### 9.1 Core Endpoints
- `POST /api/v1/presentations/upload` - Upload presentation
- `GET /api/v1/presentations/{id}` - Get presentation details
- `POST /api/v1/presentations/{id}/analyze` - Trigger AI analysis
- `POST /api/v1/presentations/{id}/generate-narratives` - Generate narratives
- `POST /api/v1/presentations/{id}/optimize-narratives` - Enhance narratives
- `POST /api/v1/slides/{id}/generate-speech` - Generate TTS
- `POST /api/v1/avatar-videos/generate` - Generate avatar video
- `GET /api/v1/assets/{type}/{id}` - Get asset URLs

### 10. Frontend Architecture

#### 10.1 Component Structure
```
frontend/src/
├── components/       # Reusable UI components
│   ├── ui/          # shadcn components
│   └── custom/      # Custom components
├── pages/           # Page components
├── hooks/           # Custom React hooks
├── services/        # API integration
├── store/           # Zustand stores
└── types/           # TypeScript definitions
```

#### 10.2 State Management
- **Zustand Stores:**
  - Presentation store
  - Processing status store
  - UI state store
- **React Query:** Server state caching (partial implementation)

### 11. Backend Architecture

#### 11.1 Project Structure
```
backend/src/main/java/ai/bluefields/ppt2video/
├── controller/              # REST API endpoints
│   ├── PresentationController
│   ├── SlideController
│   ├── AvatarVideoController
│   └── AssetController
├── service/                 # Business logic layer
│   ├── ai/                 # AI-related services
│   │   ├── narrative/      # Narrative generation
│   │   │   ├── optimization/
│   │   │   └── BatchNarrativeOrchestrator
│   │   ├── slideanalysis/  # Slide analysis
│   │   ├── DeckAnalysisService
│   │   ├── OpenAIService
│   │   └── TextToSpeechService
│   ├── avatar/             # Avatar video services
│   │   ├── providers/      # Provider implementations
│   │   └── AvatarVideoService
│   ├── parsing/            # PowerPoint parsing
│   │   ├── poi/           # Apache POI parser
│   │   └── aspose/        # Aspose parser
│   ├── rendering/          # Slide rendering
│   │   ├── msgraph/       # MS Graph strategy
│   │   ├── aspose/        # Aspose strategy
│   │   └── poi/           # POI strategy
│   ├── PresentationService
│   ├── FileStorageService
│   ├── R2AssetService
│   └── AssetMetadataService
├── repository/              # Data access layer
│   ├── PresentationRepository
│   ├── SlideRepository
│   ├── SlideNarrativeRepository
│   └── AvatarVideoRepository
├── entity/                  # JPA entities
│   ├── Presentation
│   ├── Slide
│   ├── SlideNarrative
│   ├── AvatarVideo
│   └── AssetMetadata
├── dto/                     # Data transfer objects
│   ├── request/
│   └── response/
├── config/                  # Configuration classes
│   ├── OpenAIConfig
│   ├── R2Config
│   └── MSGraphConfig
└── exception/               # Custom exceptions
    └── handlers/
```

#### 11.2 Service Layer Organization
- **Orchestrators:** Coordinate complex workflows
- **Providers:** Abstract external service integrations
- **Strategies:** Implement different processing approaches
- **Factories:** Select appropriate implementations

### 12. Security & Configuration

#### 12.1 Current Security Measures
- Input validation on file uploads
- SQL injection prevention via JPA
- XSS prevention in React
- API key management via environment variables
- CORS configuration for development

#### 12.2 Configuration Management
- **application.yml** - Main configuration
- **application-dev.yml** - Development overrides
- Environment variables for sensitive data
- MS Graph credentials in environment

### 12. Performance Optimizations

#### 12.1 Implemented Optimizations
- Batch processing for AI operations
- Asynchronous processing for long tasks
- Database connection pooling (HikariCP)
- Presigned URL caching (15-minute TTL)
- Parallel slide processing
- MS Graph token caching

#### 12.2 Processing Metrics
- Slide rendering metrics tracking
- AI processing time monitoring
- Asset upload/download timing

### 13. Testing Approach

#### 13.1 Current Testing
- **Manual Testing:** Primary approach for POC
- **Test Scripts:** Shell scripts for API testing
- **Database Verification:** SQL scripts for data validation

#### 13.2 Test Coverage Areas
- File upload and parsing
- AI narrative generation
- TTS generation
- Avatar video creation
- Asset storage and retrieval
- End-to-end workflow

### 14. Monitoring & Logging

#### 14.1 Logging Implementation
- **Framework:** SLF4J with Logback
- **Structured Logging:** Service-level logging
- **Key Areas:**
  - API requests/responses
  - External service calls
  - Processing milestones
  - Error conditions
  - Performance metrics

#### 14.2 Metrics Collection
- Processing times per operation
- External API response times
- Success/failure rates
- Resource usage tracking

### 15. Architecture Decisions (Updates)

#### ADR-006: Cloudflare R2 for Storage
- **Status:** Implemented
- **Context:** Need for scalable storage
- **Decision:** Use R2 for media assets
- **Benefits:** Cost-effective, S3-compatible

#### ADR-007: HeyGen for Avatar Videos
- **Status:** Implemented
- **Context:** D-ID limitations
- **Decision:** Switch to HeyGen API
- **Benefits:** Better quality, more features

#### ADR-008: Multiple Rendering Strategies
- **Status:** Implemented
- **Context:** Quality vs compatibility trade-offs
- **Decision:** Support multiple strategies
- **Benefits:** Flexibility, fallback options

#### ADR-009: Batch Processing
- **Status:** Implemented
- **Context:** Efficiency for multiple slides
- **Decision:** Batch AI operations
- **Benefits:** Reduced API calls, faster processing

#### ADR-010: Narrative Enhancement Pipeline
- **Status:** Implemented
- **Context:** TTS quality improvement
- **Decision:** Separate enhancement step
- **Benefits:** Better audio quality, flexibility

### 16. Known Limitations

Current POC limitations:
- No user authentication/authorization
- Single-tenant architecture
- Limited error recovery
- Manual testing only
- No horizontal scaling
- Basic rate limiting

### 17. Future Enhancements

Potential improvements identified:
- WebSocket for real-time updates
- Redis caching layer
- Message queue for job processing
- Comprehensive error recovery
- Multi-tenant support
- Automated testing suite
- Production monitoring stack
- Video composition with Shotstack