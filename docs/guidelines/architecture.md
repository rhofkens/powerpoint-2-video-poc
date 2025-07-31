# Architecture Document
## PowerPoint to Video Story POC

### 1. Overview

This document defines the technical architecture for the PowerPoint to Video Story POC system. The system automatically converts PowerPoint presentations into narrative-styled videos with configurable emotional tones (business, funny, cynical), leveraging AI for content analysis and video generation.

**Document Scope:** This architecture document covers the high-level system design, technology stack, component structures, security guidelines, and deployment approach for the POC phase.

### 2. Technology Stack (Mandatory)

The following technology stack is **mandatory** and has been frozen for this POC:

#### Frontend
- **React** 18.x - Component-based UI framework
- **TailwindCSS** 3.x - Utility-first CSS framework
- **shadcn** - Pre-built UI components
- **Vite** - Build tool and development server
- **TypeScript** - Type safety for JavaScript
- **Zustand** - State management (learning opportunity)

#### Backend
- **Spring Boot** 3.5.4 - Application framework
- **Java** 24 - Programming language
- **Spring AI** 1.0.1 - AI/LLM integration framework
- **PostgreSQL** 17 - Primary database
- **Maven** - Build and dependency management

#### External Services
- **OpenAI GPT-4o-mini** - LLM for content analysis and narrative generation
- **ElevenLabs API** - Text-to-speech generation
- **D-ID API** - Avatar video generation
- **OpenRouter** (potential) - Alternative LLM model access

### 3. System Architecture

#### 3.1 Architecture Style
The system follows a **monolithic architecture** pattern, appropriate for a POC where:
- Rapid development and iteration are priorities
- The development team is small
- Deployment complexity should be minimized
- Performance overhead of distributed systems is unnecessary

#### 3.2 High-Level Architecture

```
┌─────────────────┐     ┌──────────────────────────────────┐     ┌─────────────────┐
│                 │     │         Spring Boot Backend      │     │                 │
│  React SPA      │────▶│  ┌────────────────────────────┐  │────▶│  PostgreSQL 17  │
│  (Vite/TS)      │     │  │     REST Controllers       │  │     │                 │
│                 │     │  └────────────────────────────┘  │     └─────────────────┘
└─────────────────┘     │  ┌────────────────────────────┐  │
                        │  │     Service Layer          │  │     ┌─────────────────┐
                        │  └────────────────────────────┘  │────▶│   File System   │
                        │  ┌────────────────────────────┐  │     │  (PNG/MP3/MP4)  │
                        │  │    Spring AI Integration   │  │     └─────────────────┘
                        │  └────────────────────────────┘  │
                        └────────────│─────────────────────┘
                                     │
                        ┌────────────┴─────────────────────┐
                        │                                  │
                  ┌─────▼─────┐  ┌─────────┐  ┌──────────▼──────┐
                  │  OpenAI   │  │ElevenLabs│  │     D-ID API    │
                  │GPT-4o-mini│  │   API    │  │                 │
                  └───────────┘  └─────────┘  └─────────────────┘
```

### 4. Monorepo Architecture & Structure

#### 4.1 Monorepo Structure (Best Practices)
```
powerpoint-2-video-poc/
├── backend/                   # Spring Boot backend application
│   └── src/
│       └── main/
│           └── java/ai/bluefields/ppt2video/
│               ├── controller/       # REST endpoints
│               ├── service/          # Business logic
│               ├── repository/       # Data access layer
│               ├── entity/           # JPA entities
│               ├── dto/              # Data transfer objects
│               ├── config/           # Configuration classes
│               └── exception/        # Custom exceptions
├── frontend/                  # React SPA application
│   └── src/
│       ├── components/        # Reusable UI components
│       ├── pages/             # Page-level components
│       ├── hooks/             # Custom React hooks
│       ├── services/          # API communication
│       ├── store/             # Zustand state management
│       ├── types/             # TypeScript definitions
│       └── utils/             # Utility functions
├── shared/                    # Shared resources
│   └── api-docs/              # API documentation (OpenAPI/Swagger)
├── scripts/                   # Build and deployment scripts
├── docs/                      # Documentation
│   ├── PRD/                   # Product Requirements
│   └── guidelines/            # Architecture & coding guidelines
└── storage/                   # File storage (gitignored)
    └── presentations/         # Uploaded presentations and media
```

#### 4.2 Backend Structure Details

**Package Organization:**
- **controller:** REST API endpoints, request/response handling
- **service:** Core business logic, orchestration
- **repository:** Database operations via Spring Data JPA
- **entity:** JPA entities mapping to database tables
- **dto:** Data transfer objects for API communication
- **config:** Spring configuration and bean definitions
- **exception:** Custom exception types

#### 4.3 Frontend Structure Details

**Component Organization:**
- **components:** Reusable UI components using shadcn
- **pages:** Top-level page components
- **hooks:** Custom React hooks for shared logic
- **services:** API client and external service integration
- **store:** Zustand stores for global state management
- **types:** TypeScript interfaces and type definitions
- **utils:** Helper functions and utilities

#### 4.4 Monorepo Benefits
- **Unified Version Control:** Single source of truth
- **Shared Dependencies:** Common tooling and configurations
- **Atomic Changes:** Frontend and backend changes in single commits
- **Simplified CI/CD:** Single pipeline for entire application

#### 4.5 Key Components

**Controllers:** Handle HTTP requests/responses, minimal business logic
- REST API endpoints under `/api/v1`
- Request validation using Bean Validation
- Standard HTTP status codes and error responses

**Services:** Core business logic implementation
- Transaction management at service layer
- Asynchronous processing for long-running tasks
- Error handling and retry logic

**Repositories:** Data persistence using Spring Data JPA
- Standard CRUD operations
- Custom queries for complex data retrieval

#### 4.6 API Communication
- **Protocol:** REST over HTTP
- **Format:** JSON request/response bodies
- **Endpoints:**
  - `POST /api/v1/presentations` - Upload presentation
  - `GET /api/v1/presentations/{id}` - Get presentation details
  - `GET /api/v1/presentations/{id}/slides` - Get all slides
  - `POST /api/v1/slides/{id}/generate-speech` - Generate TTS
  - `POST /api/v1/slides/{id}/generate-video` - Generate video
  - `POST /api/v1/presentations/{id}/generate-full-video` - Generate complete video

### 5. Frontend Architecture

#### 5.1 Application Type
**Single Page Application (SPA)** built with React 18.x, already created via Lovable.

#### 5.2 State Management
- **Zustand** - For global state management
  - Presentation data and status
  - UI state (loading, errors)
  - Media generation progress
  - Learning opportunity for the team
- **Local Component State** - React useState for component-specific UI state
- **React Query** (optional) - For server state caching

#### 5.3 UI Components
- **shadcn** - Pre-built, customizable components
- **TailwindCSS** - Utility classes for styling
- Grid-based layout for slide viewer as specified in PRD

### 6. Data Architecture

#### 6.1 Database Design
- **PostgreSQL 17** as the primary database
- **Entity Design:**
  - Presentations: Core presentation metadata
  - Slides: Individual slide data and generated content
  - Processing Status: Track async processing steps
- **Relationships:** One-to-many between presentations and slides

#### 6.2 File Storage
- Local filesystem during POC phase
- Organized directory structure:
  ```
  /storage/
  ├── presentations/{presentation-id}/
  │   ├── original.pptx
  │   ├── slides/
  │   │   ├── slide-1.png
  │   │   ├── slide-2.png
  │   │   └── ...
  │   ├── audio/
  │   │   ├── slide-1.mp3
  │   │   └── ...
  │   └── videos/
  │       ├── slide-1.mp4
  │       └── final.mp4
  ```

### 7. Security Guidelines (POC Scope)

#### 7.1 Simplified Security for POC
- **No Authentication/Authorization** - Open access for POC testing
- **No User Management** - Single-user system
- **No Session Management** - Stateless operation

#### 7.2 Basic Security Measures
- **Input Validation:** File type validation for PPTX uploads
- **File Size Limits:** Reasonable limits to prevent resource exhaustion
- **SQL Injection Prevention:** Use parameterized queries (Spring Data JPA)
- **XSS Prevention:** React's built-in escaping for rendered content
- **CORS Configuration:** Configured for local development

#### 7.3 API Security
- **HTTP:** POC will use plain HTTP for simplicity
- **API Keys:** Secure storage of external service API keys in environment variables
- **Rate Limiting:** Basic rate limiting on expensive operations (LLM calls)

### 8. External Service Integration

#### 8.1 OpenAI Integration
- **Library:** Spring AI 1.0.1
- **Model:** GPT-4o-mini
- **Usage:**
  - Conceptual analysis of slide content
  - Narrative generation with style adaptation
  - Context-aware transitions between slides

#### 8.2 ElevenLabs Integration
- **Protocol:** REST API
- **Authentication:** API Key
- **Features Used:**
  - Text-to-speech conversion
  - Voice selection (if available)
  - Audio format: MP3

#### 8.3 D-ID Integration
- **Protocol:** REST API
- **Authentication:** API Key
- **Features Used:**
  - Avatar video generation
  - Lip-sync with generated audio
  - Video format: MP4

### 9. Deployment Architecture (POC)

#### 9.1 Development Environment
- **Frontend:** Vite dev server (port 5173)
- **Backend:** Spring Boot embedded Tomcat (port 8080)
- **Database:** Local PostgreSQL instance

#### 9.2 POC Deployment
- **Single Server Deployment:** All components on one machine
- **Process Management:** 
  - Frontend: Vite build served via nginx or similar
  - Backend: Spring Boot JAR with embedded Tomcat
  - Database: Local PostgreSQL installation
- **Startup Script:** Simple bash script to start all services

### 10. Performance Considerations

#### 10.1 Expected Bottlenecks
- **LLM API Calls:** Primary latency source
- **Video Generation:** D-ID processing time
- **File I/O:** Large presentation files

#### 10.2 POC Optimizations
- Asynchronous processing for long-running tasks
- Database connection pooling (HikariCP default)
- Client-side caching of generated content
- Parallel processing where external APIs allow

### 11. Testing Strategy

#### 11.1 POC Testing Approach
**Manual Testing Only** - As specified, no automated testing for POC phase.

#### 11.2 Testing Focus Areas
- File upload and parsing accuracy
- LLM response quality for different narrative styles
- TTS and video generation success rates
- End-to-end workflow completion
- Performance benchmarking
- Error handling scenarios

### 12. Monitoring and Logging

#### 12.1 Logging Strategy
- **Framework:** SLF4J with Logback
- **Log Levels:** INFO for normal operation, DEBUG for troubleshooting
- **Key Logging Points:**
  - API request/response
  - External service calls
  - Processing milestones
  - Error conditions

#### 12.2 Metrics Collection
- Processing time per slide
- Total presentation processing time
- External API response times
- Success/failure rates
- Resource usage (memory, disk)

### 13. Architecture Decisions Record (ADR)

#### ADR-001: Monolithic Architecture
- **Status:** Accepted
- **Context:** POC requiring rapid development
- **Decision:** Single deployable unit
- **Consequences:** Simpler development, limited scalability

#### ADR-002: Local File Storage
- **Status:** Accepted for POC
- **Context:** Simplified deployment, no cloud dependency
- **Decision:** Store files on local filesystem
- **Consequences:** Not suitable for production, simple for POC

#### ADR-003: No Authentication
- **Status:** Accepted for POC
- **Context:** Internal testing only
- **Decision:** Skip auth implementation
- **Consequences:** Cannot be exposed publicly

#### ADR-004: Synchronous UI Updates
- **Status:** Accepted
- **Context:** POC scope, simple implementation
- **Decision:** Polling/refresh instead of WebSockets
- **Consequences:** Less responsive UI, simpler implementation

#### ADR-005: Zustand for State Management
- **Status:** Accepted
- **Context:** Team learning opportunity
- **Decision:** Use Zustand even if overkill for POC
- **Consequences:** Over-engineered for POC scope, valuable learning experience

### 14. Future Considerations (Post-POC)

While out of scope for the POC, these items should be considered for production:
- Authentication and authorization
- Horizontal scaling capability
- Cloud storage integration
- WebSocket for real-time updates
- Caching layer (Redis)
- Message queue for job processing
- Comprehensive error recovery
- API rate limiting and throttling
- Monitoring and alerting infrastructure