# Video Story Service Architecture Plan

## Executive Summary

This plan outlines the architecture for implementing a comprehensive Video Story Service that will orchestrate the composition and rendering of final MP4 videos from previously generated presentation assets (slide images, avatar videos, audio files). The service follows a factory pattern to support multiple video rendering backends, with Shotstack.io as the initial implementation. The design emphasizes extensibility, async processing, robust error handling, and seamless integration with the existing PowerPoint-to-Video POC application architecture.

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

##### 1. Core Service Interface
```java
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

##### 1. Video Story Management Component
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

##### 2. Render Quality Selection
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

### Phase 2: Shotstack Integration (Week 2-3)
**Objective**: Implement Shotstack provider with core functionality

#### Tasks:
1. Implement ShotstackClient with retry logic and error handling
2. Create ShotstackVideoProvider implementing VideoProvider interface
3. Build ShotstackCompositionBuilder for timeline generation
4. Implement asset upload/hosting logic for Shotstack
5. Add polling mechanism for render status
6. Test connection with Shotstack sandbox

#### Deliverables:
- Working Shotstack provider
- Asset upload capability
- Basic composition generation
- Webhook processing

### Phase 3: Service Layer Implementation (Week 3-4)
**Objective**: Build orchestration service and business logic

#### Tasks:
1. Implement VideoStoryOrchestrationService
2. Create asset publishing workflow
3. Build story script generation from presentation data
4. Implement render job management and status tracking
5. Add caching layer for frequently accessed data
6. Implement asset cleanup scheduling

#### Deliverables:
- Complete service layer
- Asset publishing workflow
- Script generation logic
- Job tracking system

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

### 1. Factory Pattern for Providers
**Rationale**: Enables easy addition of new video rendering providers without modifying core logic

### 2. JSONB for Composition Storage
**Rationale**: Flexible schema for different provider formats, efficient querying with PostgreSQL

### 3. Polling-Based Status Updates
**Rationale**: Simpler infrastructure for POC, no need for public webhook endpoints, easier to demo on laptop

### 4. Separate Published Assets Table with Auto-Deletion
**Rationale**: Tracks provider-specific asset URLs, enables asset reuse, automatic cleanup for cost control and privacy

### 5. Async Processing Throughout
**Rationale**: Better user experience, handles long-running operations, prevents timeouts

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

## Conclusion

This architecture provides a robust, scalable foundation for video story composition and rendering. The factory pattern ensures extensibility for future providers, while the async processing model handles long-running operations efficiently. The integration with Shotstack provides professional video rendering capabilities while maintaining flexibility to adapt to changing requirements.

The phased implementation approach allows for incremental delivery of value while managing technical risk. By following this plan, the team can deliver a production-ready video generation system that seamlessly integrates with the existing PowerPoint-to-Video POC application.

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