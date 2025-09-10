# Video Story Service Architecture Plan

## Executive Summary

This plan outlines the architecture for implementing a comprehensive Video Story Service that will orchestrate the composition and rendering of final MP4 videos from previously generated presentation assets. The implementation follows a phased approach:

- **Phase 1** (Completed): Intro video generation using Google Veo API
- **Phase 2** (Minimalistic MVP): Shotstack integration for intro section composition only (8 seconds) with simple text overlays and lower thirds
- **Phase 3** (Full Implementation): Complete presentation video with slides, avatars, and audio

Phase 2 focuses on a minimal viable implementation that demonstrates end-to-end Shotstack integration with just the intro section, requiring only one asset upload (the Veo-generated intro video) and basic composition features. This approach enables rapid testing and validation before expanding to full presentation rendering.

## Current State Analysis

### Existing Architecture Overview

The application currently has:
- **Asset Management**: R2AssetService handles storage and retrieval of assets in Cloudflare R2 buckets
- **Third-party Integrations**: HeyGen for avatar generation, ElevenLabs for TTS, following consistent client/provider patterns
- **Database**: PostgreSQL with JPA entities for Presentation, Slide, AssetMetadata, AvatarVideo, SlideSpeech, SlideNarrative
- **Preflight Checks**: PreflightCheckService validates all required assets exist before video generation
- **Enhanced Narratives**: SlideNarrative entities include emotional markers and timing for better video generation
- **Frontend**: React/TypeScript with components for avatar video management and processing status

### Identified Limitations
- No final video composition capability
- Assets are generated but not assembled into a cohesive video
- No tracking of video rendering jobs or status
- Missing orchestration layer for video timeline generation

### Dependencies and Constraints
- Assets stored in R2 with presigned URL access patterns
- Async processing patterns already established with avatar video generation
- Spring Boot 3.x with Java 17+
- React frontend expecting consistent API patterns
- Database migration managed through Flyway

## Proposed Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend Application                     │
├─────────────────────────────────────────────────────────────┤
│                    REST API Controllers                      │
│  VideoStoryController │ WebhookController │ StatusController │
├─────────────────────────────────────────────────────────────┤
│                    Service Layer                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │            VideoStoryOrchestrationService            │  │
│  │  - Asset Publishing    - Script Generation           │  │
│  │  - Render Management   - Status Tracking             │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              VideoProviderFactory                    │  │
│  │   ┌────────────┐  ┌────────────┐  ┌────────────┐   │  │
│  │   │ Shotstack  │  │  Remotion  │  │   Custom   │   │  │
│  │   │  Provider  │  │  Provider  │  │  Provider  │   │  │
│  │   └────────────┘  └────────────┘  └────────────┘   │  │
│  └──────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                    Integration Layer                         │
│  ShotstackClient │ AssetPublisher │ WebhookProcessor        │
├─────────────────────────────────────────────────────────────┤
│                     Data Layer                               │
│  VideoStory │ RenderJob │ PublishedAsset │ StoryTemplate    │
└─────────────────────────────────────────────────────────────┘
```

### Detailed Technical Specifications

#### Backend Changes

##### 1. Phase 2 Simplified Service Interface
```java
// Phase 2: Minimal intro-only interface
public interface IntroVideoCompositionService {
    // Render intro section only (8 seconds)
    RenderJob renderIntroSection(UUID presentationId);
    RenderJobStatus getRenderStatus(UUID renderJobId);
    String getRenderedVideoUrl(UUID renderJobId);
}

// Phase 3: Full service interface
public interface VideoStoryService {
    // Asset Management
    PublishAssetsResponse publishAssets(UUID presentationId);
    void unpublishAssets(UUID presentationId);
    
    // Story Script Management
    VideoStoryScript createStoryScript(UUID presentationId, Optional<String> templateId);
    VideoStoryScript editStoryScript(UUID presentationId, UUID storyId, StoryScriptEditRequest request);
    void deleteStoryScript(UUID presentationId, UUID storyId);
    
    // Rendering
    RenderJob renderStory(UUID presentationId, UUID storyId);
    void cancelRender(UUID presentationId, UUID renderJobId);
    RenderJobStatus getRenderStatus(UUID renderJobId);
    
    // Query Operations
    List<VideoStoryScript> getStoryScripts(UUID presentationId);
    List<RenderJob> getRenderJobs(UUID presentationId);
}
```

##### 2. Provider Factory Pattern
```java
public interface VideoProvider {
    String getProviderName();
    boolean supportsAssetHosting();
    
    // Asset operations
    AssetUploadResult uploadAsset(AssetUploadRequest request);
    void deleteAsset(String assetId);
    
    // Composition operations
    VideoComposition createComposition(CompositionRequest request);
    VideoComposition validateComposition(VideoComposition composition);
    
    // Render operations
    RenderResponse submitRender(VideoComposition composition);
    RenderStatus checkRenderStatus(String renderId);
    void cancelRender(String renderId);
}

@Component
public class VideoProviderFactory {
    private final Map<VideoProviderType, VideoProvider> providers;
    
    public VideoProvider getProvider(VideoProviderType type) {
        return providers.get(type);
    }
}
```

##### 3. Shotstack Provider Implementation
```java
@Component
public class ShotstackVideoProvider implements VideoProvider {
    private final ShotstackClient client;
    private final ShotstackCompositionBuilder compositionBuilder;
    private final ShotstackAssetManager assetManager;
    
    // All methods are provider-specific and must be implemented
    
    @Override
    public AssetUploadResult uploadAsset(AssetUploadRequest request) {
        // Shotstack-specific asset upload logic
        // Copy from R2 to Shotstack's local storage
        return assetManager.uploadToShotstack(request);
    }
    
    @Override
    public void deleteAsset(String assetId) {
        // Shotstack-specific asset deletion
        assetManager.deleteFromShotstack(assetId);
    }
    
    @Override
    public VideoComposition createComposition(CompositionRequest request) {
        // Build Shotstack-specific timeline and tracks
        return compositionBuilder.buildComposition(request);
    }
    
    @Override
    public RenderResponse submitRender(VideoComposition composition) {
        // Convert to Shotstack JSON format
        Edit shotstackEdit = compositionBuilder.buildEdit(composition);
        
        // Submit to Shotstack API with quality settings
        return client.submitRender(shotstackEdit);
    }
    
    @Override
    public RenderStatus checkRenderStatus(String renderId) {
        // Poll Shotstack for render status
        return client.getRenderStatus(renderId);
    }
    
    @Override
    public void cancelRender(String renderId) {
        // Cancel ongoing render at Shotstack
        client.cancelRender(renderId);
    }
}
```

##### 4. Composition Builder for Shotstack
```java
@Component
public class ShotstackCompositionBuilder {
    
    public Edit buildEdit(VideoStoryScript script, List<PublishedAsset> assets) {
        Timeline timeline = buildTimeline(script, assets);
        Output output = buildOutput(script.getOutputSettings());
        
        return Edit.builder()
            .timeline(timeline)
            .output(output)
            .callback(buildWebhookUrl(script))
            .build();
    }
    
    private Timeline buildTimeline(VideoStoryScript script, List<PublishedAsset> assets) {
        List<Track> tracks = new ArrayList<>();
        
        // Track 1: Title overlays
        tracks.add(buildTitleTrack(script));
        
        // Track 2: Avatar videos with chroma key
        tracks.add(buildAvatarTrack(script, assets));
        
        // Track 3: Background slides
        tracks.add(buildSlideTrack(script, assets));
        
        // Track 4: Transitions
        tracks.add(buildTransitionTrack(script));
        
        return Timeline.builder()
            .tracks(tracks)
            .background("#FFFFFF")
            .soundtrack(buildSoundtrack(script, assets))
            .build();
    }
}
```

#### Frontend Changes

##### Phase 2: Minimal UI Implementation

###### 1. Frontend UI Components

**AI Analysis Panel Integration**:
- Add "Generate Video Story" button in the AI Actions tab (alongside "Generate Intro Video")
- Button enabled only when an intro video exists for the presentation
- Clicking opens the VideoStoryModal component

**VideoStoryModal Component**:
```typescript
// src/components/VideoStoryModal.tsx
interface VideoStoryModalProps {
    presentationId: string;
    onClose: () => void;
}

const VideoStoryModal: React.FC<VideoStoryModalProps> = ({ presentationId, onClose }) => {
    const [composition, setComposition] = useState<VideoComposition | null>(null);
    const [renderJob, setRenderJob] = useState<RenderJob | null>(null);
    const [loading, setLoading] = useState(false);
    const [renderStatus, setRenderStatus] = useState<RenderStatus>('idle');
    
    // Modal structure:
    // - Title: "Video Story Composition"
    // - Large JSON viewer (read-only, syntax highlighted)
    // - Bottom section with:
    //   - "Render Video" button (primary action)
    //   - "Cancel" button (close modal)
    //   - Status indicator area for render progress
};
```

**Frontend Implementation Files**:
- `src/components/AIAnalysisPanel.tsx` - Add "Generate Video Story" button
- `src/components/VideoStoryModal.tsx` - New modal component
- `src/services/api.ts` - Add API calls for composition and rendering
- `src/types/video-story.ts` - TypeScript types for video story entities

**User Flow**:
1. User clicks "Generate Video Story" → Modal opens with loading spinner
2. Backend generates composition JSON → Display JSON in modal
3. User reviews JSON and clicks "Render Video" → Show rendering progress
4. Backend processes render → Display status updates
5. Render completes → Show video URL and preview option

###### 2. API Service Methods

```typescript
// src/services/api.ts additions
export const videoStoryService = {
    // Generate composition JSON (mocked or real)
    generateComposition: async (presentationId: string): Promise<VideoComposition> => {
        const response = await api.post(`/video-story/presentations/${presentationId}/composition`);
        return response.data;
    },
    
    // Start render process
    renderIntroSection: async (presentationId: string): Promise<RenderJob> => {
        const response = await api.post(`/video-story/presentations/${presentationId}/render-intro`);
        return response.data;
    },
    
    // Check render status
    getRenderStatus: async (jobId: string): Promise<RenderJob> => {
        const response = await api.get(`/video-story/render-jobs/${jobId}`);
        return response.data;
    },
    
    // Poll for status updates
    pollRenderStatus: async (jobId: string, onUpdate: (job: RenderJob) => void): Promise<void> => {
        const poll = async () => {
            const job = await videoStoryService.getRenderStatus(jobId);
            onUpdate(job);
            
            if (!job.status.isTerminal) {
                setTimeout(poll, 5000); // Poll every 5 seconds
            }
        };
        await poll();
    }
};
```

###### 3. TypeScript Types

```typescript
// src/types/video-story.ts
export interface VideoComposition {
    timeline: Timeline;
    output: OutputSettings;
    metadata: CompositionMetadata;
}

export interface RenderJob {
    id: string;
    presentationId: string;
    type: 'INTRO_ONLY' | 'FULL_PRESENTATION';
    status: RenderStatus;
    progress: number;
    outputUrl?: string;
    errorMessage?: string;
    createdAt: string;
    completedAt?: string;
}

export interface RenderStatus {
    status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
    isTerminal: boolean;
    progress: number;
}
```

##### Phase 3: Full Video Story Management (Future)

###### 1. Video Story Management Component
```typescript
interface VideoStoryManager {
    presentationId: string;
    stories: VideoStoryScript[];
    renderJobs: RenderJob[];
    
    // Operations
    createStory(templateId?: string): Promise<VideoStoryScript>;
    editStory(storyId: string, changes: Partial<VideoStoryScript>): Promise<void>;
    renderStory(storyId: string): Promise<RenderJob>;
    
    // Preview
    generatePreview(storyId: string): Promise<PreviewData>;
    updateTimeline(storyId: string, timeline: TimelineData): Promise<void>;
}
```

###### 2. Render Quality Selection
```typescript
interface RenderOptions {
    quality: 'preview' | 'production';
    presentationId: string;
    storyId: string;
}

const VideoRenderComponent: React.FC<RenderOptions> = ({quality, presentationId, storyId}) => {
    const [renderJob, setRenderJob] = useState<RenderJob | null>(null);
    const [status, setStatus] = useState<RenderStatus>('idle');
    
    const handleRender = async () => {
        const job = await apiService.renderVideo(presentationId, storyId, {
            quality, // 'preview' for fast/low-res, 'production' for HD
        });
        setRenderJob(job);
        pollRenderStatus(job.id);
    };
    
    // Note: Preview viewing will be done on Shotstack dashboard
    // We only track render status and provide link to result
};
```

#### Database Design

##### 1. New Entities

```sql
-- Video Story Scripts
CREATE TABLE video_stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    template_id UUID REFERENCES story_templates(id),
    script_data JSONB NOT NULL, -- Stores the complete composition
    duration_seconds NUMERIC(10,2),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT fk_video_story_presentation FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) ON DELETE CASCADE
);

-- Render Jobs
CREATE TABLE render_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_story_id UUID NOT NULL REFERENCES video_stories(id),
    provider VARCHAR(50) NOT NULL,
    provider_job_id VARCHAR(255) UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    progress INTEGER DEFAULT 0,
    output_url TEXT,
    output_metadata JSONB,
    error_message TEXT,
    webhook_url TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_render_job_story FOREIGN KEY (video_story_id) 
        REFERENCES video_stories(id) ON DELETE CASCADE
);

-- Published Assets (tracks assets uploaded to video service)
CREATE TABLE published_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_metadata_id UUID NOT NULL REFERENCES asset_metadata(id),
    video_story_id UUID REFERENCES video_stories(id),
    provider VARCHAR(50) NOT NULL,
    provider_asset_id VARCHAR(255),
    provider_url TEXT NOT NULL,
    publish_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    metadata JSONB,
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_published_asset_metadata FOREIGN KEY (asset_metadata_id) 
        REFERENCES asset_metadata(id) ON DELETE CASCADE
);

-- Story Templates
CREATE TABLE story_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(100),
    template_data JSONB NOT NULL,
    thumbnail_url TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Webhook Events
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    render_job_id UUID REFERENCES render_jobs(id),
    provider VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_render_job FOREIGN KEY (render_job_id) 
        REFERENCES render_jobs(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_video_stories_presentation ON video_stories(presentation_id);
CREATE INDEX idx_video_stories_status ON video_stories(status);
CREATE INDEX idx_render_jobs_story ON render_jobs(video_story_id);
CREATE INDEX idx_render_jobs_status ON render_jobs(status);
CREATE INDEX idx_render_jobs_provider_id ON render_jobs(provider_job_id);
CREATE INDEX idx_published_assets_metadata ON published_assets(asset_metadata_id);
CREATE INDEX idx_published_assets_story ON published_assets(video_story_id);
CREATE INDEX idx_webhook_events_job ON webhook_events(render_job_id);
CREATE INDEX idx_webhook_events_processed ON webhook_events(processed);
```

##### 2. Entity Relationships
- VideoStory -> Presentation (Many-to-One)
- VideoStory -> StoryTemplate (Many-to-One, Optional)
- RenderJob -> VideoStory (Many-to-One)
- PublishedAsset -> AssetMetadata (Many-to-One)
- PublishedAsset -> VideoStory (Many-to-One, Optional)
- RenderStatusCheck -> RenderJob (Many-to-One)

## Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
**Objective**: Establish core infrastructure and data models

#### Tasks:
1. Create database migration scripts for new entities
2. Implement JPA entities and repositories
3. Create VideoProvider interface and factory pattern
4. Implement basic DTOs for API communication
5. Set up configuration for Shotstack API credentials

#### Deliverables:
- Database schema deployed
- Entity classes with repositories
- Provider interface defined
- Basic project structure in place

### Phase 2: Minimalistic Intro Composition with UI (Week 1-2)
**Objective**: Implement minimal Shotstack integration for intro section only with basic UI for testing

#### Backend Tasks:
1. Implement simplified ShotstackClient for basic operations (can mock Shotstack API initially)
2. Create IntroCompositionBuilder for intro-only timeline (8 seconds)
3. Generate composition JSON with intro video + lower thirds + text overlays
4. Upload only intro video asset to Shotstack (when not mocked)
5. Implement polling for render status (no webhooks)
6. Test composition generation logic independently

#### Frontend Tasks:
1. Add "Generate Video Story" button to AI Analysis Panel
2. Create VideoStoryModal component for composition display
3. Implement API service methods for composition and rendering
4. Add TypeScript types for video story entities
5. Handle loading states and status updates
6. Display final video URL when rendering completes

#### Deliverables:
- Minimal Shotstack client (submit render, check status) - can be mocked initially
- Intro-only composition builder (8-second timeline)
- Composition JSON generation and display
- Frontend modal with JSON viewer and render controls
- API integration between frontend and backend
- Single asset upload (intro video from Veo)
- Working render and polling flow (when connected to real Shotstack)
- Downloaded MP4 of intro section

#### Simplified Scope:
- **Timeline**: Only 8 seconds (intro section)
- **Assets**: 
  - 1 intro video (from Google Veo, already generated)
  - 2 lower thirds animations (hardcoded Shotstack assets)
  - Text overlays (title and author from deck analysis)
- **No need for**: Avatar videos, slide images, audio tracks, complex transitions
- **Output**: Single MP4 file of intro section
- **UI**: Minimal modal interface for testing composition generation

### Phase 3: Full Presentation Composition (Week 3-4)
**Objective**: Extend to full presentation with slides and avatars

#### Tasks:
1. Extend ShotstackCompositionBuilder for full timeline
2. Implement multi-asset upload (slides, avatars, audio)
3. Add slide transitions and timing logic
4. Build complete VideoStoryOrchestrationService
5. Implement asset publishing workflow for all assets
6. Add render job management with database tracking

#### Deliverables:
- Full timeline composition builder
- Multi-asset upload and management
- Complete orchestration service
- Database-backed job tracking
- Full presentation video output

### Phase 4: API and Frontend Integration (Week 4-5)
**Objective**: Create REST endpoints and frontend components

#### Tasks:
1. Implement VideoStoryController with all endpoints
2. Create frontend service layer for video story management
3. Build video story management UI components
4. Implement timeline visualization and editor
5. Add video preview capabilities
6. Create render status monitoring UI

#### Deliverables:
- REST API endpoints
- Frontend components
- Timeline editor
- Status monitoring

### Phase 5: Testing and Optimization (Week 5-6)
**Objective**: Ensure reliability and performance

#### Tasks:
1. Comprehensive integration testing
2. Performance optimization for large presentations
3. Error handling and recovery mechanisms
4. Documentation and API specifications
5. Load testing for concurrent renders
6. Security review and hardening

#### Deliverables:
- Test suite with >80% coverage
- Performance benchmarks
- Documentation
- Production-ready system

## Risk Assessment

### Technical Risks

1. **Shotstack API Limitations**
   - Risk: Rate limits or processing constraints
   - Mitigation: Implement queuing system, caching, and batch processing
   - Contingency: Design for multiple provider support from the start

2. **Asset Synchronization**
   - Risk: Assets may expire or become unavailable during rendering
   - Mitigation: Verify all assets before render submission, implement re-upload logic
   - Contingency: Store asset checksums and implement recovery mechanisms

3. **Long Render Times**
   - Risk: Timeouts or user experience issues with long renders
   - Mitigation: Async processing with webhooks, progress tracking, email notifications
   - Contingency: Implement render queue with priority management

4. **Data Consistency**
   - Risk: Inconsistent state between local DB and provider
   - Mitigation: Implement idempotent operations, transaction management, audit logging
   - Contingency: Manual reconciliation tools and admin interfaces

### Performance Implications

1. **Database Load**
   - Large JSONB fields for script_data and metadata
   - Solution: Implement pagination, lazy loading, and consider JSONB indexing

2. **Network Latency**
   - Multiple API calls to external services
   - Solution: Batch operations, parallel processing where possible, implement circuit breakers

3. **Storage Growth**
   - Published assets and render outputs accumulate
   - Solution: Implement retention policies, archive old renders, use CDN for distribution

### Security Considerations

1. **API Key Management**
   - Store Shotstack API keys securely using environment variables or secret management
   - Implement key rotation procedures

2. **Webhook Validation**
   - Verify webhook signatures to prevent unauthorized callbacks
   - Implement request replay protection

3. **Asset Access Control**
   - Ensure proper authorization for asset access
   - Use presigned URLs with expiration

## Alternative Approaches Considered

### 1. Direct FFmpeg Integration
**Pros**: Full control, no external dependencies, cost-effective
**Cons**: Complex implementation, requires video processing expertise, infrastructure overhead
**Decision**: Rejected due to complexity and time constraints

### 2. AWS MediaConvert
**Pros**: Scalable, reliable, AWS ecosystem integration
**Cons**: Complex setup, requires AWS expertise, potentially higher costs
**Decision**: Reserved as potential future provider

### 3. Synchronous Rendering
**Pros**: Simpler implementation, immediate feedback
**Cons**: Poor user experience, timeout issues, doesn't scale
**Decision**: Rejected in favor of async with webhooks

### 4. Client-Side Rendering
**Pros**: No server costs, immediate preview
**Cons**: Performance issues, browser limitations, poor quality
**Decision**: Rejected for final rendering, may use for preview

## Success Metrics

### Performance Benchmarks
- Render submission: < 2 seconds response time
- Asset publishing: < 5 seconds for typical presentation
- Status updates: Real-time via webhooks
- Video generation: < 30 seconds per minute of output video

### Quality Indicators
- Render success rate: > 95%
- Asset upload success rate: > 99%
- Webhook delivery rate: > 99%
- Zero data loss incidents

### Business Metrics
- User adoption rate of video generation feature
- Average renders per user per month
- Time from upload to final video
- User satisfaction scores

## Dependencies and Prerequisites

### Required Tools and Libraries
```xml
<!-- Maven Dependencies -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<!-- spring-boot-starter-web is already included in the project -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
```

### Team Skills Needed
- Spring Boot async processing
- REST API integration
- Video composition concepts
- React/TypeScript for frontend
- PostgreSQL JSONB operations

### Infrastructure Requirements
- No webhook endpoint needed (using polling)
- Increased storage for video outputs
- CDN for video distribution (optional)
- Basic monitoring for POC

## Key Technical Decisions

### Phase 2 Specific Decisions

1. **Minimal Shotstack Integration**
   - **Decision**: Start with intro-only composition (8 seconds)
   - **Rationale**: Reduces complexity, enables quick end-to-end testing, validates integration approach

2. **Single Asset Upload**
   - **Decision**: Upload only the Veo-generated intro video
   - **Rationale**: Minimizes API calls, simplifies error handling, faster implementation

3. **Hardcoded Lower Thirds**
   - **Decision**: Use Shotstack's public assets for lower thirds animations
   - **Rationale**: No need to create/upload custom animations, proven to work from example

4. **Simple Polling Without Webhooks**
   - **Decision**: Poll every 5 seconds for status updates
   - **Rationale**: No webhook endpoint needed, simpler for laptop demo, adequate for POC

5. **Minimal Database Schema**
   - **Decision**: Single render_jobs table without complex relationships
   - **Rationale**: Faster implementation, easier debugging, sufficient for intro-only rendering

### General Architecture Decisions

1. **Factory Pattern for Providers (Phase 3)**
   - **Rationale**: Enables easy addition of new video rendering providers without modifying core logic

2. **JSONB for Composition Storage**
   - **Rationale**: Flexible schema for different provider formats, efficient querying with PostgreSQL

3. **Polling-Based Status Updates**
   - **Rationale**: Simpler infrastructure for POC, no need for public webhook endpoints, easier to demo on laptop

4. **Separate Published Assets Table with Auto-Deletion (Phase 3)**
   - **Rationale**: Tracks provider-specific asset URLs, enables asset reuse, automatic cleanup for cost control and privacy

5. **Async Processing Throughout**
   - **Rationale**: Better user experience, handles long-running operations, prevents timeouts

## Integration Points

### Existing System Integration

1. **PreflightCheckService Enhancement**
   - Add validation for video story readiness
   - Verify all required assets are available

2. **R2AssetService Integration**
   - Reuse existing presigned URL generation
   - Leverage asset metadata management

3. **Avatar Video Integration**
   - Ensure avatar videos include chroma key metadata
   - Coordinate timing with audio tracks

4. **Enhanced Narrative Integration**
   - Use emotional markers for transition selection
   - Leverage timing information for clip duration

### External System Integration

1. **Shotstack API**
   - REST API integration with retry logic using Spring Boot RestClient
   - Polling for status updates (no webhooks)
   - Asset hosting with automatic cleanup after retention period

2. **Cloudflare R2**
   - Continue using for asset storage
   - Generate public URLs for Shotstack access

3. **Monitoring Systems**
   - Integrate with existing logging
   - Add metrics for render performance
   - Alert on failure patterns

## Phase 2 Minimalistic Implementation Details

### Simplified Intro-Only Architecture

#### 1. Minimal Shotstack Client
```java
@Component
@Slf4j
public class ShotstackIntroClient {
    private final RestClient restClient;
    
    @Value("${shotstack.api.key}")
    private String apiKey;
    
    @Value("${shotstack.api.base-url:https://api.shotstack.io/edit/stage}")
    private String baseUrl;
    
    public ShotstackIntroClient() {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
    
    public RenderResponse submitIntroRender(IntroComposition composition) {
        return restClient.post()
            .uri("/render")
            .body(composition.toShotstackEdit())
            .retrieve()
            .body(RenderResponse.class);
    }
    
    public RenderStatus checkRenderStatus(String renderId) {
        return restClient.get()
            .uri("/render/{id}", renderId)
            .retrieve()
            .body(RenderStatus.class);
    }
    
    public AssetUploadResponse uploadIntroVideo(String introVideoUrl) {
        // Simple asset upload from R2 URL to Shotstack
        Map<String, String> request = Map.of(
            "url", introVideoUrl
        );
        
        return restClient.post()
            .uri("/sources")
            .body(request)
            .retrieve()
            .body(AssetUploadResponse.class);
    }
}
```

#### 2. Intro Composition Builder
```java
@Component
@Slf4j
public class IntroCompositionBuilder {
    
    // Hardcoded Shotstack assets for lower thirds
    private static final String LOWER_THIRD_IN = 
        "https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/overlays/lower-third-in-example.mov";
    private static final String LOWER_THIRD_OUT = 
        "https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/overlays/lower-third-out-example.mov";
    private static final String LUMA_PATTERN = 
        "https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/luma-mattes/grids/pattern-3.mp4";
    
    public IntroComposition buildIntroComposition(
            String introVideoUrl,
            String presentationTitle,
            String authorName,
            String organization) {
        
        List<Track> tracks = new ArrayList<>();
        
        // Track 1: Title text overlay
        tracks.add(buildTitleTrack(presentationTitle));
        
        // Track 2: Author text overlay
        tracks.add(buildAuthorTrack(authorName, organization));
        
        // Track 3: Lower third in animation
        tracks.add(buildLowerThirdInTrack());
        
        // Track 4: Lower third out animation
        tracks.add(buildLowerThirdOutTrack());
        
        // Track 5: Intro video with luma transition
        tracks.add(buildIntroVideoTrack(introVideoUrl));
        
        // Build timeline (8 seconds total)
        Timeline timeline = Timeline.builder()
            .tracks(tracks)
            .background("#FFFFFF")
            .build();
        
        // Output settings
        Output output = Output.builder()
            .format("mp4")
            .resolution("hd")
            .fps(25)
            .size(Size.builder()
                .width(1920)
                .height(1080)
                .build())
            .build();
        
        return IntroComposition.builder()
            .timeline(timeline)
            .output(output)
            .build();
    }
    
    private Track buildTitleTrack(String title) {
        HtmlAsset titleAsset = HtmlAsset.builder()
            .type("html")
            .width(640)
            .height(46)
            .html("<p data-html-type=\"text\">" + escapeHtml(title) + "</p>")
            .css("p { color: #ffffff; font-size: 39px; font-family: 'Clear Sans'; text-align: center; }")
            .build();
        
        Clip titleClip = Clip.builder()
            .asset(titleAsset)
            .start(0.7)
            .length(4.3)
            .offset(Offset.builder().x(-0.209).y(-0.317).build())
            .transition(Transition.builder()
                .in("wipeRight")
                .out("wipeLeft")
                .build())
            .position("center")
            .build();
        
        return Track.builder()
            .clips(List.of(titleClip))
            .build();
    }
    
    private Track buildAuthorTrack(String author, String organization) {
        String authorText = String.format("By %s, %s", author, organization);
        
        HtmlAsset authorAsset = HtmlAsset.builder()
            .type("html")
            .width(457)
            .height(46)
            .html("<p data-html-type=\"text\">" + escapeHtml(authorText) + "</p>")
            .css("p { color: #282828; font-size: 26px; font-family: 'Clear Sans'; text-align: center; }")
            .build();
        
        Clip authorClip = Clip.builder()
            .asset(authorAsset)
            .start(0.8)
            .length(4.3)
            .offset(Offset.builder().x(-0.125).y(-0.376).build())
            .transition(Transition.builder()
                .in("wipeLeft")
                .out("wipeRight")
                .build())
            .position("center")
            .build();
        
        return Track.builder()
            .clips(List.of(authorClip))
            .build();
    }
    
    private Track buildIntroVideoTrack(String introVideoUrl) {
        // Main intro video clip
        VideoAsset videoAsset = VideoAsset.builder()
            .type("video")
            .src(introVideoUrl)
            .volume(1)
            .build();
        
        Clip videoClip = Clip.builder()
            .asset(videoAsset)
            .start(0)
            .length(8)
            .scale(1)
            .build();
        
        // Luma matte transition at the end
        LumaAsset lumaAsset = LumaAsset.builder()
            .type("luma")
            .src(LUMA_PATTERN)
            .build();
        
        Clip lumaClip = Clip.builder()
            .asset(lumaAsset)
            .start(6)
            .length(2)
            .build();
        
        return Track.builder()
            .clips(List.of(videoClip, lumaClip))
            .build();
    }
}
```

#### 3. Intro Video Service Integration
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class IntroVideoCompositionService {
    
    private final IntroVideoRepository introVideoRepository;
    private final DeckAnalysisRepository deckAnalysisRepository;
    private final ShotstackIntroClient shotstackClient;
    private final IntroCompositionBuilder compositionBuilder;
    private final RenderJobRepository renderJobRepository;
    
    @Transactional
    public RenderJob renderIntroSection(UUID presentationId) {
        // 1. Get intro video and deck analysis
        IntroVideo introVideo = introVideoRepository
            .findByPresentationIdAndStatus(presentationId, VideoGenerationStatus.COMPLETED)
            .orElseThrow(() -> new ResourceNotFoundException("No completed intro video found"));
        
        DeckAnalysis deckAnalysis = deckAnalysisRepository
            .findByPresentationId(presentationId)
            .orElseThrow(() -> new ResourceNotFoundException("No deck analysis found"));
        
        // 2. Upload intro video to Shotstack
        AssetUploadResponse uploadResponse = shotstackClient
            .uploadIntroVideo(introVideo.getPublishedUrl());
        
        // 3. Build intro composition (8 seconds only)
        IntroComposition composition = compositionBuilder.buildIntroComposition(
            uploadResponse.getAssetUrl(),
            deckAnalysis.getTitle(),
            deckAnalysis.getAuthor(),
            deckAnalysis.getOrganization()
        );
        
        // 4. Submit render
        RenderResponse renderResponse = shotstackClient.submitIntroRender(composition);
        
        // 5. Create and save render job
        RenderJob renderJob = RenderJob.builder()
            .presentationId(presentationId)
            .type(RenderType.INTRO_ONLY)
            .shotstackRenderId(renderResponse.getId())
            .status(RenderStatus.QUEUED)
            .compositionData(composition.toJson())
            .createdAt(LocalDateTime.now())
            .build();
        
        renderJob = renderJobRepository.save(renderJob);
        
        // 6. Start polling for status
        startPolling(renderJob.getId());
        
        return renderJob;
    }
    
    @Async
    private void startPolling(UUID renderJobId) {
        // Simple polling every 5 seconds
        for (int i = 0; i < 60; i++) { // Max 5 minutes
            try {
                Thread.sleep(5000);
                
                RenderJob job = renderJobRepository.findById(renderJobId).orElse(null);
                if (job == null || job.getStatus().isTerminal()) {
                    return;
                }
                
                RenderStatus status = shotstackClient.checkRenderStatus(job.getShotstackRenderId());
                
                job.setStatus(status.getStatus());
                job.setProgress(status.getProgress());
                
                if (status.getStatus() == RenderStatus.DONE) {
                    job.setOutputUrl(status.getUrl());
                    job.setCompletedAt(LocalDateTime.now());
                }
                
                renderJobRepository.save(job);
                
                if (status.getStatus().isTerminal()) {
                    return;
                }
                
            } catch (Exception e) {
                log.error("Error polling render status", e);
            }
        }
    }
}
```

#### 4. Simplified API Endpoint
```java
@RestController
@RequestMapping("/api/video-story")
@RequiredArgsConstructor
@Slf4j
public class IntroVideoCompositionController {
    
    private final IntroVideoCompositionService compositionService;
    private final RenderJobRepository renderJobRepository;
    
    @PostMapping("/presentations/{presentationId}/render-intro")
    public ResponseEntity<RenderJobResponse> renderIntroSection(
            @PathVariable UUID presentationId) {
        
        log.info("Starting intro section render for presentation: {}", presentationId);
        
        RenderJob renderJob = compositionService.renderIntroSection(presentationId);
        
        return ResponseEntity.ok(RenderJobResponse.from(renderJob));
    }
    
    @GetMapping("/render-jobs/{jobId}")
    public ResponseEntity<RenderJobResponse> getRenderStatus(
            @PathVariable UUID jobId) {
        
        RenderJob job = renderJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Render job not found"));
        
        return ResponseEntity.ok(RenderJobResponse.from(job));
    }
}
```

#### 5. Minimal Database Schema
```sql
-- Simplified render_jobs table for Phase 2
CREATE TABLE render_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id),
    type VARCHAR(50) NOT NULL DEFAULT 'INTRO_ONLY',
    shotstack_render_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    progress INTEGER DEFAULT 0,
    output_url TEXT,
    composition_data JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_render_jobs_presentation ON render_jobs(presentation_id);
CREATE INDEX idx_render_jobs_status ON render_jobs(status);
```

## Shotstack-Specific Implementation Details

### Timeline Construction Algorithm
```java
public Timeline buildTimeline(VideoStoryScript script, List<PublishedAsset> assets) {
    double currentTime = 0.0;
    List<Track> tracks = new ArrayList<>();
    
    // Opening sequence (5 seconds)
    if (script.hasIntro()) {
        tracks.add(buildIntroTrack(0, 5.0));
        currentTime = 5.0;
    }
    
    // Process each slide
    for (SlideSegment segment : script.getSegments()) {
        // Background slide image
        Track slideTrack = Track.builder()
            .clips(List.of(
                Clip.builder()
                    .asset(ImageAsset.builder()
                        .src(getAssetUrl(segment.getSlideId(), AssetType.SLIDE_IMAGE))
                        .build())
                    .start(currentTime)
                    .length(segment.getDuration())
                    .transition(determineTransition(segment))
                    .build()
            ))
            .build();
        
        // Avatar video with chroma key
        if (segment.hasAvatar()) {
            Track avatarTrack = Track.builder()
                .clips(List.of(
                    Clip.builder()
                        .asset(VideoAsset.builder()
                            .src(getAssetUrl(segment.getSlideId(), AssetType.SLIDE_AVATAR_VIDEO))
                            .chromaKey(ChromaKey.builder()
                                .color("#F5DEB3") // Wheat color as per example
                                .threshold(15)
                                .halo(50)
                                .build())
                            .build())
                        .start(currentTime + segment.getAvatarDelay())
                        .length(segment.getAvatarDuration())
                        .scale(0.519) // As per example
                        .offset(Offset.builder()
                            .x(0.371)
                            .y(-0.241)
                            .build())
                        .transition(Transition.builder()
                            .in("fade")
                            .out("fade")
                            .build())
                        .build()
                ))
                .build();
            tracks.add(avatarTrack);
        }
        
        tracks.add(slideTrack);
        currentTime += segment.getDuration();
        
        // Add transition pause
        currentTime += 0.5;
    }
    
    // Arrange tracks (top layer first)
    Collections.reverse(tracks);
    
    return Timeline.builder()
        .tracks(tracks)
        .background("#FFFFFF")
        .build();
}
```

### Chroma Key Configuration
- Default color: `#F5DEB3` (wheat) for avatar videos
- Threshold: 15 (configurable based on video quality)
- Halo: 50 (edge softening)
- Alternative: `#00FF00` (green screen) with threshold 20

### Asset Publishing Strategy
1. **R2 to Shotstack Copy**: Copy assets from R2 (permanent storage) to Shotstack (temporary)
2. **Asset Tracking**: Track all published assets in database for cleanup
3. **Auto-Deletion**: Schedule deletion after configurable retention period (e.g., 7 days)
4. **Privacy Protection**: Ensure complete removal from video service after use

### Render Configuration with Quality Settings
```java
public Output buildOutput(OutputSettings settings, RenderQuality quality) {
    String resolution = quality == RenderQuality.PREVIEW ? "sd" : "hd";
    int width = quality == RenderQuality.PREVIEW ? 640 : 1920;
    int height = quality == RenderQuality.PREVIEW ? 360 : 1080;
    
    return Output.builder()
        .format("mp4")
        .resolution(resolution) // 'sd' for preview, 'hd' for production
        .fps(25) // Matches example
        .quality(quality == RenderQuality.PREVIEW ? "medium" : "high")
        .size(Size.builder()
            .width(width)
            .height(height)
            .build())
        .destinations(List.of(
            Destination.builder()
                .provider("shotstack")
                .build()
        ))
        .build();
}

public enum RenderQuality {
    PREVIEW("medium", "sd"),     // Fast, low-res for preview
    PRODUCTION("high", "hd");    // Full quality for final output
    
    private final String value;
    private final String resolution;
}
```

## HTTP Client Implementation with Spring Boot RestClient

### Configuration
```java
@Configuration
public class VideoServiceHttpConfig {
    
    @Bean
    public RestClient shotstackRestClient(
            @Value("${shotstack.api.key}") String apiKey,
            @Value("${shotstack.api.base-url}") String baseUrl) {
        
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .requestInterceptor((request, body, execution) -> {
                // Add request logging
                log.debug("Shotstack API request: {} {}", 
                    request.getMethod(), request.getURI());
                return execution.execute(request, body);
            })
            .build();
    }
}
```

### Client Implementation with Virtual Threads
```java
@Component
public class ShotstackClient {
    private final RestClient restClient;
    private final ExecutorService virtualThreadExecutor;
    
    public ShotstackClient(RestClient shotstackRestClient) {
        this.restClient = shotstackRestClient;
        // Use virtual threads for concurrent operations
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public RenderResponse submitRender(Edit edit, RenderQuality quality) {
        // Add quality parameter to the edit
        edit.getOutput().setQuality(quality.getValue());
        
        return restClient.post()
            .uri("/render")
            .body(edit)
            .retrieve()
            .body(RenderResponse.class);
    }
    
    public RenderStatus getRenderStatus(String renderId) {
        return restClient.get()
            .uri("/render/{id}", renderId)
            .retrieve()
            .body(RenderStatus.class);
    }
    
    // Concurrent asset upload using virtual threads
    public CompletableFuture<List<AssetUploadResult>> uploadAssetsParallel(
            List<AssetUploadRequest> requests) {
        
        List<CompletableFuture<AssetUploadResult>> futures = requests.stream()
            .map(request -> CompletableFuture.supplyAsync(
                () -> uploadAsset(request), virtualThreadExecutor))
            .toList();
            
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
}
```

### Polling Service Implementation
```java
@Service
@Slf4j
public class RenderPollingService {
    
    @Value("${polling.interval.seconds:10}")
    private int pollingInterval;
    
    @Value("${polling.max.attempts:180}")
    private int maxAttempts;
    
    private final ShotstackClient shotstackClient;
    private final RenderJobRepository renderJobRepository;
    private final ScheduledExecutorService scheduler = 
        Executors.newScheduledThreadPool(5);
    
    public void startPolling(UUID renderJobId) {
        scheduler.schedule(() -> pollRenderStatus(renderJobId, 0), 
            pollingInterval, TimeUnit.SECONDS);
    }
    
    private void pollRenderStatus(UUID renderJobId, int attempt) {
        if (attempt >= maxAttempts) {
            log.error("Max polling attempts reached for job: {}", renderJobId);
            markJobFailed(renderJobId, "Polling timeout");
            return;
        }
        
        try {
            RenderJob job = renderJobRepository.findById(renderJobId)
                .orElseThrow();
            
            RenderStatus status = shotstackClient.getRenderStatus(
                job.getProviderJobId());
            
            updateJobStatus(job, status);
            
            if (!status.isTerminal()) {
                // Schedule next poll
                scheduler.schedule(() -> pollRenderStatus(renderJobId, attempt + 1), 
                    pollingInterval, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("Error polling render status for job: {}", renderJobId, e);
            scheduler.schedule(() -> pollRenderStatus(renderJobId, attempt + 1), 
                pollingInterval * 2, TimeUnit.SECONDS); // Backoff on error
        }
    }
}
```

## Asset Cleanup Service

### Scheduled Cleanup Implementation
```java
@Service
@Slf4j
public class AssetCleanupService {
    
    @Value("${asset.cleanup.retention.days:7}")
    private int retentionDays;
    
    @Value("${asset.cleanup.scheduled.enabled:true}")
    private boolean scheduledCleanupEnabled;
    
    private final PublishedAssetRepository publishedAssetRepository;
    private final VideoProviderFactory providerFactory;
    
    @Scheduled(cron = "${asset.cleanup.cron:0 0 2 * * *}") // Daily at 2 AM
    public void cleanupExpiredAssets() {
        if (!scheduledCleanupEnabled) {
            return;
        }
        
        log.info("Starting scheduled asset cleanup");
        
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<PublishedAsset> expiredAssets = publishedAssetRepository
            .findByScheduledDeletionAtBeforeAndDeletedAtIsNull(cutoff);
        
        log.info("Found {} assets scheduled for deletion", expiredAssets.size());
        
        for (PublishedAsset asset : expiredAssets) {
            try {
                deleteAssetFromProvider(asset);
                asset.setDeletedAt(LocalDateTime.now());
                publishedAssetRepository.save(asset);
                log.debug("Deleted asset: {}", asset.getId());
            } catch (Exception e) {
                log.error("Failed to delete asset: {}", asset.getId(), e);
            }
        }
        
        log.info("Asset cleanup completed");
    }
    
    private void deleteAssetFromProvider(PublishedAsset asset) {
        VideoProvider provider = providerFactory.getProvider(
            VideoProviderType.valueOf(asset.getProvider()));
        provider.deleteAsset(asset.getProviderAssetId());
    }
    
    // Manual cleanup for immediate privacy compliance
    public void deleteAssetsForPresentation(UUID presentationId) {
        List<PublishedAsset> assets = publishedAssetRepository
            .findByPresentationIdAndDeletedAtIsNull(presentationId);
        
        for (PublishedAsset asset : assets) {
            deleteAssetFromProvider(asset);
            asset.setDeletedAt(LocalDateTime.now());
        }
        
        publishedAssetRepository.saveAll(assets);
        log.info("Manually deleted {} assets for presentation: {}", 
            assets.size(), presentationId);
    }
}
```

## Error Handling and Resilience Patterns

### Retry Strategy
```java
@Retryable(
    retryFor = {ApiException.class, NetworkException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
public RenderResponse submitRender(VideoComposition composition) {
    // Implementation
}
```

### Circuit Breaker
```java
@CircuitBreaker(
    name = "shotstack-api",
    fallbackMethod = "fallbackRender"
)
public RenderResponse renderWithCircuitBreaker(VideoComposition composition) {
    // Implementation
}
```

### Compensation Logic
- If asset upload fails, mark for retry
- If render fails, preserve composition for retry
- Automatic cleanup of published assets after retention period
- Manual cleanup option for immediate privacy compliance

## Testing Strategy

### Manual Testing Plan
- Complete video generation flow end-to-end
- Error recovery scenarios
- UI interaction testing
- Cross-browser compatibility
- Preview vs production quality comparison
- Asset cleanup verification after retention period
- Polling mechanism verification
- Shotstack API sandbox connection
- Database transaction verification

## Monitoring and Observability

### Key Metrics
- Render job queue depth
- Average render duration
- API call latency
- Error rates by type
- Asset upload success rate

### Logging Strategy
- Structured logging with correlation IDs
- Detailed error logs with context
- Audit trail for all operations
- Performance metrics logging

### Alerting Rules
- Render failure rate > 5%
- API response time > 5 seconds
- Queue depth > 100 jobs
- Webhook processing delays > 1 minute

## Documentation Requirements

### API Documentation
- OpenAPI/Swagger specification
- Request/response examples
- Error code reference
- Rate limit documentation

### Developer Guide
- Architecture overview
- Setup instructions
- Configuration guide
- Troubleshooting guide

### User Documentation
- Video story creation guide
- Template usage instructions
- Best practices for video generation
- FAQ and common issues

## Phase 2 Implementation Priorities

### Core Requirements for Minimal Demo

1. **Prerequisites**:
   - Completed intro video generation via Google Veo (Phase 1)
   - Valid deck analysis with title and author information
   - Shotstack API key for sandbox environment

2. **Essential Components**:
   - `ShotstackIntroClient`: Basic API operations (upload, render, status)
   - `IntroCompositionBuilder`: 8-second timeline with text overlays
   - `IntroVideoCompositionService`: Orchestration logic
   - `IntroVideoCompositionController`: REST endpoints
   - Database migration for `render_jobs` table

3. **Composition Elements**:
   - Intro video (8 seconds, from Veo)
   - Title text overlay (0.7s - 5s)
   - Author text overlay (0.8s - 5.1s)
   - Lower third in animation (0.5s - 4.5s)
   - Lower third out animation (4s - 5.5s)
   - Luma matte transition (6s - 8s)

4. **Success Criteria**:
   - Frontend UI successfully integrated with AI Analysis Panel
   - Modal displays generated composition JSON clearly
   - Composition JSON generation works independently (can be mocked)
   - Successfully upload intro video to Shotstack (when not mocked)
   - Submit render and receive render ID
   - Poll status until completion with UI updates
   - Retrieve and display final MP4 video URL
   - User can review JSON before triggering actual render
   - Error states handled gracefully in UI

### Development Sequence

**Backend (can be developed in parallel with frontend):**
1. Set up Shotstack API credentials (can use mock initially)
2. Create minimal database schema (render_jobs table)
3. Implement basic Shotstack client (with mock option)
4. Build intro composition logic (JSON generation)
5. Create service orchestration
6. Add REST endpoints for composition and rendering
7. Test composition generation independently
8. Connect to real Shotstack API when ready

**Frontend:**
1. Add "Generate Video Story" button to AI Analysis Panel
2. Create VideoStoryModal component with JSON viewer
3. Add TypeScript types for video story entities
4. Implement API service methods in api.ts
5. Add loading and status indicators
6. Test UI flow with mocked backend responses
7. Integrate with real backend endpoints
8. Add error handling and user feedback

**Integration:**
1. Test end-to-end flow with mocked Shotstack
2. Generate and review composition JSON
3. Connect to real Shotstack sandbox
4. Test actual video rendering
5. Verify polling and status updates
6. Handle edge cases and errors

## Conclusion

This updated architecture plan provides a pragmatic, phased approach to implementing video composition capabilities. Phase 2's minimalistic focus on just the intro section (8 seconds) with a basic UI allows for rapid validation of both the composition logic and Shotstack integration while avoiding the complexity of full presentation rendering.

The simplified implementation requires only:
- One asset upload (intro video)
- Basic text overlays from deck analysis
- Hardcoded lower thirds animations
- Simple polling mechanism
- Minimal modal UI for testing

Key advantages of the minimal UI approach:

- **Immediate Feedback**: Users can see the generated composition JSON before committing to render
- **Testing Flexibility**: Backend composition logic can be tested independently with mocked Shotstack API
- **Rapid Iteration**: UI allows quick testing of different composition parameters
- **Cost Efficiency**: Review JSON before triggering actual Shotstack render calls
- **Progressive Enhancement**: Start with mock, then connect to real Shotstack when ready

This approach enables the team to demonstrate end-to-end video composition within 1-2 weeks, providing a solid foundation for Phase 3's expansion to full presentation videos with slides, avatars, and audio. The minimal UI makes it easy to validate the backend logic without building a full video editor interface, while still providing a user-friendly way to test and debug the composition generation.

## Appendix A: Shotstack JSON Template Structure

Based on the analyzed example, a typical Shotstack composition for a presentation video includes:

1. **Opening Title Sequence** (5 seconds)
   - HTML title with animations
   - Author attribution
   - Branded lower-third graphics

2. **Content Sections** (per slide)
   - Background: Slide image (full duration)
   - Foreground: Avatar video with chroma key (partial duration)
   - Transitions: Arrow luma mattes or fades between sections

3. **Audio Track**
   - Concatenated audio from all slides
   - Optional background music
   - Volume normalization

4. **Output Settings**
   - Format: MP4
   - Resolution: 1920x1080 (HD)
   - FPS: 25
   - Codec: H.264 (default)

## Appendix B: Database Migration Script Preview

```sql
-- V1__add_video_story_tables.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE video_story_status AS ENUM ('DRAFT', 'READY', 'RENDERING', 'COMPLETED', 'FAILED');
CREATE TYPE render_job_status AS ENUM ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE publish_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED', 'EXPIRED');

-- Create tables as defined in Database Design section
-- Add foreign key constraints
-- Create indexes for performance
```

## Appendix C: Configuration Properties

```properties
# Shotstack Configuration
shotstack.api.key=${SHOTSTACK_API_KEY}
shotstack.api.base-url=https://api.shotstack.io
shotstack.api.environment=${SHOTSTACK_ENV:stage}
shotstack.api.timeout=30000
shotstack.api.max-retries=3

# some hardcoded asset for the intro section
shotstack.assets.lower-third.in=https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/overlays/lower-third-in-example.mov
shotstack.assets.lower-third.out=https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/overlays/lower-third-out-example.mov
shotstack.assets.luma-matte.partern=https://shotstack-assets.s3.ap-southeast-2.amazonaws.com/luma-mattes/grids/pattern-3.mp4
shotstack.assets.luma-matte.arrow-right=https://templates.shotstack.io/basic/asset/video/luma/double-arrow/double-arrow-right-45.mp4

# Video Story Configuration
video.story.default-fps=25
video.story.default-resolution=hd
video.story.max-duration-minutes=60
video.story.asset-expire-hours=48

# Polling Configuration
polling.interval.seconds=10
polling.max.attempts=180
polling.timeout.minutes=30

# Asset Cleanup Configuration
asset.cleanup.retention.days=7
asset.cleanup.scheduled.enabled=true
asset.cleanup.cron=0 0 2 * * *
```
