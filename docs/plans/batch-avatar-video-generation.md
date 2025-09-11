# Batch Avatar Video Generation Architecture Plan

## Executive Summary
This plan outlines the implementation of a "Generate All Avatar Videos" feature that allows batch generation of avatar videos for all slides in a presentation. The feature will follow existing patterns from the batch narrative generation system, utilize Virtual Threads for concurrent processing, provide real-time progress monitoring, and integrate seamlessly with the current UI/UX patterns in the AI Actions tab.

## Current State Analysis

### Existing Architecture Overview
1. **Batch Processing Pattern**: The system already implements batch processing for:
   - Slide Analysis (`BatchSlideAnalysisOrchestrator`)
   - Narrative Generation (`BatchNarrativeOrchestrator`)
   - Audio Generation (sequential processing in frontend)

2. **Avatar Video Infrastructure**:
   - `AvatarVideoService`: Single video generation orchestration
   - `AvatarVideoMonitorService`: Async monitoring with auto-publishing to R2
   - `AvatarProviderFactory`: Provider abstraction (HeyGen)
   - Database entity tracking with status management

3. **Progress Monitoring**:
   - `AnalysisStatusService`: Centralized progress tracking
   - WebSocket/polling-based frontend updates
   - Zustand store for state management

### Identified Limitations
- No batch avatar video generation capability
- Avatar videos must be generated individually per slide
- No unified progress tracking for avatar video batch operations
- Missing UI for batch avatar video operations

### Dependencies and Constraints
- HeyGen API rate limits and concurrent request handling
- Virtual Thread executor already configured
- R2 storage auto-publishing mechanism in place
- Existing progress monitoring infrastructure

## Proposed Architecture

### High-Level Design
```
Frontend                    Backend                         External Services
    │                           │                                  │
    ├── AI Actions Tab          ├── BatchAvatarVideoOrchestrator │
    │   └── Generate Button     │   ├── Concurrent Processing    ├── HeyGen API
    │                           │   ├── Semaphore Control        │
    ├── Modal Component         │   └── Progress Tracking        │
    │   ├── Progress Bar        │                                │
    │   ├── Slide Status List   ├── AvatarVideoService          ├── R2 Storage
    │   └── Regenerate Options  │   └── Single Video Generation  │
    │                           │                                │
    └── Zustand Store          ├── AvatarVideoMonitorService    │
        └── Progress Updates    │   └── Auto-publishing          │
                               │                                │
                               └── AnalysisStatusService        │
                                   └── Progress Broadcasting    │
```

### Detailed Technical Specifications

#### Backend Changes

##### 1. New Service: `BatchAvatarVideoOrchestrator`
```java
package ai.bluefields.ppt2video.service.avatar;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAvatarVideoOrchestrator {
    
    private final SlideRepository slideRepository;
    private final AvatarVideoService avatarVideoService;
    private final AvatarVideoRepository avatarVideoRepository;
    private final AnalysisStatusService analysisStatusService;
    
    @Autowired
    private AsyncTaskExecutor virtualThreadExecutor;
    
    @Value("${app.avatar.batch.max-concurrent:5}")
    private int maxConcurrentGenerations;
    
    @Value("${app.avatar.batch.timeout-per-video-seconds:180}")
    private int timeoutPerVideoSeconds;
    
    @Async("virtualThreadExecutor")
    public void generateAllAvatarVideos(UUID presentationId, BatchAvatarVideoRequest request) {
        // Implementation following BatchNarrativeOrchestrator pattern
        // with concurrent processing using Semaphore
        // and progress tracking via AnalysisStatusService
    }
}
```

##### 2. New DTO: `BatchAvatarVideoRequest`
```java
public class BatchAvatarVideoRequest {
    private String avatarId;  // Optional, defaults to narrative-based selection
    private String backgroundColor;  // Optional, defaults to "#FFFFFF"
    private boolean regenerateExisting;  // Whether to regenerate existing videos
    private List<UUID> selectedSlideIds;  // Optional, null means all slides
}
```

##### 3. New Analysis Type
Add to `AnalysisType` enum:
```java
ALL_AVATAR_VIDEOS_GENERATION("All Avatar Videos Generation")
```

##### 4. New Controller Endpoint
```java
@PostMapping("/presentations/{id}/generate-all-avatar-videos")
public ResponseEntity<ApiResponse<BatchAvatarVideoResponse>> generateAllAvatarVideos(
    @PathVariable UUID id,
    @RequestBody BatchAvatarVideoRequest request
)
```

##### 5. Progress Tracking Integration
- Utilize existing `AnalysisStatusService` for progress updates
- Track: total slides, completed, failed, skipped (existing videos)
- Real-time status updates through existing WebSocket infrastructure

#### Frontend Changes

##### 1. New Modal Component: `BatchAvatarVideoModal.tsx`
```typescript
interface BatchAvatarVideoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  onComplete?: (result: BatchAvatarVideoResponse) => void;
}

// Component structure:
// - Modal header with title and description
// - Options section (avatar selection, regenerate toggle)
// - Existing videos list with regenerate buttons
// - Progress tracking during generation
// - Real-time status updates per slide
```

##### 2. API Service Extension
```typescript
// In api.ts
async generateAllAvatarVideos(
  presentationId: string, 
  options: BatchAvatarVideoOptions
): Promise<void> {
  return this.axiosInstance.post(
    `/presentations/${presentationId}/generate-all-avatar-videos`,
    options
  );
}

async getAvatarVideoStatus(presentationId: string): Promise<AvatarVideoStatus[]> {
  // Get status of all avatar videos for presentation
}
```

##### 3. Store Integration
Extend existing `analysisStore` to handle `ALL_AVATAR_VIDEOS_GENERATION` status type:
- No changes needed to store structure
- Automatic support through existing polling mechanism

##### 4. AI Actions Tab Update
Add button after "Generate All Audio":
```tsx
<Button
  variant="outline"
  onClick={() => setBatchAvatarVideoModalOpen(true)}
  className="justify-start"
  disabled={isGeneratingAvatarVideos}
>
  {isGeneratingAvatarVideos ? (
    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
  ) : (
    <Video className="h-4 w-4 mr-2" />
  )}
  Generate All Avatar Videos
</Button>
```

#### Database Design

##### Schema Modifications
No schema changes required. Existing `avatar_videos` table sufficient with:
- Status tracking per video
- R2 asset references
- Provider metadata storage

##### Migration Strategy
No migration needed - leveraging existing infrastructure

##### Performance Optimizations
- Batch fetch slides with narratives and audio status
- Use database connection pooling for concurrent operations
- Implement efficient bulk status updates

## Implementation Roadmap

### Phase 1: Backend Foundation (2-3 days)
- Create `BatchAvatarVideoOrchestrator` service
- Implement concurrent processing with Virtual Threads
- Add progress tracking integration
- Create REST endpoint and DTOs
- Unit tests for orchestrator service

**Acceptance Criteria:**
- Service can process multiple slides concurrently
- Progress is tracked in AnalysisStatusService
- Respects configurable concurrency limits
- Handles errors gracefully per slide

### Phase 2: Frontend Implementation (2-3 days)
- Create `BatchAvatarVideoModal` component
- Implement progress tracking UI
- Add regeneration options for existing videos
- Integrate with existing store and polling
- Update AI Actions tab with new button

**Acceptance Criteria:**
- Modal displays current video status
- Progress updates in real-time
- Can regenerate specific videos
- Proper error handling and user feedback

### Phase 3: Integration & Testing (1-2 days)
- End-to-end testing with various presentation sizes
- Performance testing with concurrent generation
- Error scenario testing
- Documentation updates

**Acceptance Criteria:**
- Successfully generates videos for 10+ slides concurrently
- Handles API failures gracefully
- Progress tracking accurate and responsive
- No memory leaks or resource exhaustion

## Risk Assessment

### Technical Risks
1. **HeyGen API Rate Limits**
   - Mitigation: Configurable concurrency limits, exponential backoff
   
2. **Memory Usage with Concurrent Processing**
   - Mitigation: Virtual Threads, limited concurrent operations via Semaphore
   
3. **Database Connection Pool Exhaustion**
   - Mitigation: Proper transaction management, connection pooling configuration

4. **Frontend State Synchronization**
   - Mitigation: Use existing polling mechanism, proper error boundaries

### Performance Implications
- Expected processing time: 30-60 seconds per video
- With 5 concurrent: ~12 videos per minute
- Network bandwidth for video downloads: ~10-50MB per video

### Security Considerations
- Validate presentation ownership before batch operations
- Rate limit batch requests per user/session
- Secure storage of temporary video files
- Proper cleanup of failed operations

## Alternative Approaches Considered

### 1. Queue-Based Processing
- **Approach**: Use message queue (RabbitMQ/Kafka) for video generation tasks
- **Rejected because**: Over-engineering for current scale, adds infrastructure complexity

### 2. Sequential Processing
- **Approach**: Process videos one at a time like current audio generation
- **Rejected because**: Too slow for typical presentations (10+ slides)

### 3. External Worker Service
- **Approach**: Separate microservice for video processing
- **Rejected because**: Adds deployment complexity, current monolith handles load well

## Success Metrics

### Performance Benchmarks
- Concurrent processing of 5 videos simultaneously
- 95% success rate for video generation
- Progress updates within 2 seconds of status change
- Total processing time < 5 minutes for 20 slides

### Quality Indicators
- No data corruption or lost videos
- Accurate progress tracking (±5% accuracy)
- Graceful error recovery
- Clear user feedback at all stages

### Business Metrics
- Reduced time to generate full presentation videos
- Increased user engagement with batch features
- Reduced support tickets for video generation

## Dependencies and Prerequisites

### Required Tools or Libraries
- Existing dependencies sufficient
- No new libraries required

### Team Skills Needed
- Java Spring Boot expertise
- React/TypeScript frontend development
- Understanding of concurrent programming
- HeyGen API familiarity

### Infrastructure Requirements
- Sufficient JVM heap for Virtual Threads
- Database connection pool size: min 10, max 30
- R2 storage capacity for video files
- Network bandwidth for video transfers

## Monitoring and Observability

### Logging Requirements
- Start/completion of batch operations
- Individual video generation status
- Error details with stack traces
- Performance metrics (time per video)

### Metrics to Track
- Videos generated per minute
- Success/failure rates
- Average generation time
- Concurrent operations count
- API rate limit usage

### Alerting Thresholds
- Failure rate > 20%
- Generation time > 5 minutes per video
- Database connection pool > 80% utilized
- Memory usage > 85% of heap

## Rollback Strategy

### Phase 1 Rollback
- Remove new service class
- No database changes to revert

### Phase 2 Rollback
- Feature flag to hide UI button
- Existing single-video generation remains functional

### Phase 3 Rollback
- Full revert via git
- No data migration required
- Existing videos remain accessible

## Implementation Notes

### Best Practices to Follow
1. Use existing patterns from `BatchNarrativeOrchestrator`
2. Leverage Virtual Thread executor configuration
3. Implement proper resource cleanup in finally blocks
4. Use structured logging for debugging
5. Follow existing error handling patterns

### Common Pitfalls to Avoid
1. Don't hold database transactions during API calls
2. Avoid blocking operations in Virtual Threads
3. Don't store large video data in memory
4. Prevent duplicate video generation for same slide
5. Handle partial failures gracefully

### Testing Considerations
1. Mock HeyGen API for unit tests
2. Test with various presentation sizes (1, 10, 50 slides)
3. Simulate API failures and timeouts
4. Verify proper cleanup of resources
5. Test concurrent operations limits

## Conclusion

This architecture provides a robust, scalable solution for batch avatar video generation that:
- Follows established patterns in the codebase
- Utilizes modern Java concurrency features (Virtual Threads)
- Provides excellent user experience with real-time progress
- Handles errors gracefully with proper recovery
- Scales efficiently with configurable limits
- Integrates seamlessly with existing infrastructure

The implementation can be completed in approximately 5-8 days with proper testing and documentation.