# Pre-flight Check Feature Architecture Plan

## Executive Summary
This document outlines the architectural plan for implementing a comprehensive Pre-flight Check feature in the AI Actions tab. The feature will validate the readiness of all slides in a presentation by checking for mandatory components (narrative, audio, avatar video) and their R2 storage publication status. The implementation will provide real-time progress tracking through a modal interface and ensure presentations are fully prepared for video generation.

## Current State Analysis

### Existing Architecture Overview
The application follows a clear separation of concerns with:
- **Frontend**: React + TypeScript with Zustand for state management
- **Backend**: Spring Boot with JPA/Hibernate for persistence
- **Database**: PostgreSQL with comprehensive schema for assets and metadata
- **Storage**: Cloudflare R2 for asset storage with presigned URL management
- **UI Components**: shadcn/ui component library for consistent UI

### Key Components Identified
1. **Frontend Components**:
   - `AIAnalysisPanel.tsx` - Main panel containing AI Actions tab (lines 298-391)
   - Batch operations section within AI Actions tab (lines 314-344)
   - Modal pattern established in `AvatarVideoModal.tsx`
   - API service with comprehensive error handling

2. **Backend Components**:
   - `AIAnalysisController` - Handles batch operations for slides
   - `R2AssetService` - Manages R2 storage operations
   - `SlideAssetController` - Provides asset listing endpoints
   - Entity models: `SlideNarrative`, `SlideSpeech`, `AvatarVideo`, `AssetMetadata`

3. **Database Schema**:
   - `slide_narratives` - Stores narratives with enhanced versions
   - `slide_speeches` - Tracks audio file paths and R2 publication
   - `avatar_videos` - Contains video URLs and published URLs
   - `asset_metadata` - Central asset tracking with R2 status

### Identified Limitations
- No centralized endpoint for comprehensive slide readiness checks
- Missing batch validation for presentation-level asset status
- No progress tracking mechanism for multi-slide validation operations
- Lack of detailed error reporting for missing or unpublished assets

### Dependencies and Constraints
- Must integrate with existing Zustand store pattern
- Should leverage existing modal component patterns
- Must respect existing database schema without breaking changes
- Should utilize existing R2 service infrastructure

## Proposed Architecture

### High-Level Design
```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                             │
├─────────────────────────────────────────────────────────────┤
│  AIAnalysisPanel                                           │
│    ├── AI Actions Tab                                      │
│    │   ├── Batch Operations Section                        │
│    │   │   └── [Pre-flight Check Button]                  │
│    │   └── PreflightCheckModal (New)                      │
│    │       ├── Progress Tracking                          │
│    │       └── Results Display                            │
│    └── Zustand Store Integration                          │
├─────────────────────────────────────────────────────────────┤
│                     API Layer                              │
│    POST /api/presentations/{id}/preflight-check            │
│    GET  /api/presentations/{id}/preflight-status           │
├─────────────────────────────────────────────────────────────┤
│                      Backend                               │
│  PreflightCheckController (New)                           │
│    └── PreflightCheckService (New)                        │
│        ├── SlideValidationService                         │
│        ├── R2AssetVerificationService                     │
│        └── ProgressTrackingService                        │
├─────────────────────────────────────────────────────────────┤
│                     Database                               │
│  Queries across: slide_narratives, slide_speeches,         │
│  avatar_videos, asset_metadata                            │
└─────────────────────────────────────────────────────────────┘
```

### Detailed Technical Specifications

#### Backend Changes

##### 1. New DTOs
```java
// PreflightCheckRequestDto.java
public class PreflightCheckRequestDto {
    private boolean checkEnhancedNarrative; // Optional check
    private boolean forceRefresh; // Force re-check even if cached
}

// PreflightCheckResponseDto.java
public class PreflightCheckResponseDto {
    private UUID presentationId;
    private PreflightStatus overallStatus;
    private List<SlideCheckResult> slideResults;
    private PreflightSummary summary;
    private Instant checkedAt;
}

// SlideCheckResult.java
public class SlideCheckResult {
    private UUID slideId;
    private int slideNumber;
    private String slideTitle;
    private CheckStatus narrativeStatus;
    private CheckStatus enhancedNarrativeStatus;
    private CheckStatus audioStatus;
    private CheckStatus avatarVideoStatus;
    private List<String> issues;
    private Map<String, Object> metadata;
}

// PreflightSummary.java
public class PreflightSummary {
    private int totalSlides;
    private int slidesReady;
    private int slidesMissingNarrative;
    private int slidesMissingAudio;
    private int slidesMissingVideo;
    private int slidesWithUnpublishedAssets;
    private boolean allMandatoryChecksPassed;
}

// CheckStatus enum
public enum CheckStatus {
    PASSED,
    FAILED,
    WARNING,
    NOT_APPLICABLE,
    CHECKING
}

// PreflightStatus enum
public enum PreflightStatus {
    READY,
    INCOMPLETE,
    HAS_WARNINGS,
    CHECKING,
    ERROR
}
```

##### 2. New Controller
```java
@RestController
@RequestMapping("/api/presentations")
@RequiredArgsConstructor
@Slf4j
public class PreflightCheckController {
    
    private final PreflightCheckService preflightCheckService;
    
    @PostMapping("/{id}/preflight-check")
    public ResponseEntity<ApiResponse<PreflightCheckResponseDto>> runPreflightCheck(
            @PathVariable UUID id,
            @RequestBody(required = false) PreflightCheckRequestDto request) {
        // Implementation
    }
    
    @GetMapping("/{id}/preflight-status")
    public ResponseEntity<ApiResponse<PreflightCheckResponseDto>> getPreflightStatus(
            @PathVariable UUID id) {
        // Return latest status
    }
}
```

##### 3. New Services
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PreflightCheckService {
    
    private final SlideRepository slideRepository;
    private final SlideNarrativeRepository narrativeRepository;
    private final SlideSpeechRepository speechRepository;
    private final AvatarVideoRepository avatarVideoRepository;
    private final AssetMetadataRepository assetRepository;
    private final R2AssetVerificationService r2VerificationService;
    
    public PreflightCheckResponseDto runPreflightCheck(
            UUID presentationId, 
            PreflightCheckRequestDto request) {
        // Main orchestration logic
        // 1. Fetch all slides
        // 2. For each slide, run validation checks
        // 3. Aggregate results
        // 4. Return results
    }
    
    private SlideCheckResult validateSlide(Slide slide, boolean checkEnhanced) {
        // Validate individual slide components
    }
}

@Service
@RequiredArgsConstructor
public class R2AssetVerificationService {
    
    private final AssetMetadataRepository assetRepository;
    private final R2ClientFactory r2ClientFactory;
    
    public boolean verifyAssetPublished(UUID assetId) {
        // Verify asset exists in R2
    }
    
    public Map<UUID, Boolean> batchVerifyAssets(List<UUID> assetIds) {
        // Batch verification for performance
    }
}
```

##### 4. Repository Methods
```java
// Add to SlideNarrativeRepository
@Query("SELECT sn FROM SlideNarrative sn WHERE sn.slide.presentation.id = :presentationId AND sn.isActive = true")
List<SlideNarrative> findActiveNarrativesByPresentationId(@Param("presentationId") UUID presentationId);

// Add to SlideSpeechRepository
@Query("SELECT ss FROM SlideSpeech ss WHERE ss.presentationId = :presentationId AND ss.isActive = true")
List<SlideSpeech> findActiveSpeechesByPresentationId(@Param("presentationId") UUID presentationId);

// Add to AvatarVideoRepository
@Query("SELECT av FROM AvatarVideo av WHERE av.presentationId = :presentationId ORDER BY av.createdAt DESC")
List<AvatarVideo> findLatestByPresentationId(@Param("presentationId") UUID presentationId);
```

#### Frontend Changes

##### 1. New Modal Component
```typescript
// PreflightCheckModal.tsx
interface PreflightCheckModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  onComplete?: (result: PreflightCheckResponse) => void;
}

export function PreflightCheckModal({
  open,
  onOpenChange,
  presentationId,
  onComplete
}: PreflightCheckModalProps) {
  const [checkStatus, setCheckStatus] = useState<PreflightStatus>('CHECKING');
  const [slideResults, setSlideResults] = useState<SlideCheckResult[]>([]);
  const [summary, setSummary] = useState<PreflightSummary | null>(null);
  const [currentSlide, setCurrentSlide] = useState(0);
  const [error, setError] = useState<string | null>(null);
  
  // Implementation with:
  // - Progress bar showing current slide being checked
  // - Real-time updates via polling or SSE
  // - Detailed results display with icons
  // - Export results functionality
}
```

##### 2. Update AIAnalysisPanel
```typescript
// In AIAnalysisPanel.tsx, add to batch operations section (after line 342):
<Button
  variant="outline"
  onClick={handlePreflightCheck}
  className="justify-start"
  disabled={isRunningPreflightCheck}
>
  {isRunningPreflightCheck ? (
    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
  ) : (
    <CheckCircle className="h-4 w-4 mr-2" />
  )}
  Pre-flight Check
</Button>

// Add modal component
<PreflightCheckModal
  open={preflightModalOpen}
  onOpenChange={setPreflightModalOpen}
  presentationId={presentationId}
  onComplete={handlePreflightComplete}
/>
```

##### 3. API Service Updates
```typescript
// Add to api.ts
async runPreflightCheck(
  presentationId: string,
  options?: PreflightCheckOptions
): Promise<PreflightCheckResponse> {
  const response = await this.axiosInstance.post(
    `/presentations/${presentationId}/preflight-check`,
    options
  );
  return response.data;
}

async getPreflightStatus(
  presentationId: string
): Promise<PreflightCheckResponse> {
  const response = await this.axiosInstance.get(
    `/presentations/${presentationId}/preflight-status`
  );
  return response.data;
}
```

##### 4. Type Definitions
```typescript
// types/preflight.ts
export interface PreflightCheckOptions {
  checkEnhancedNarrative?: boolean;
  forceRefresh?: boolean;
}

export interface SlideCheckResult {
  slideId: string;
  slideNumber: number;
  slideTitle: string;
  narrativeStatus: CheckStatus;
  enhancedNarrativeStatus: CheckStatus;
  audioStatus: CheckStatus;
  avatarVideoStatus: CheckStatus;
  issues: string[];
  metadata: Record<string, any>;
}

export interface PreflightSummary {
  totalSlides: number;
  slidesReady: number;
  slidesMissingNarrative: number;
  slidesMissingAudio: number;
  slidesMissingVideo: number;
  slidesWithUnpublishedAssets: number;
  allMandatoryChecksPassed: boolean;
}

export interface PreflightCheckResponse {
  presentationId: string;
  overallStatus: PreflightStatus;
  slideResults: SlideCheckResult[];
  summary: PreflightSummary;
  checkedAt: string;
}

export type CheckStatus = 'PASSED' | 'FAILED' | 'WARNING' | 'NOT_APPLICABLE' | 'CHECKING';
export type PreflightStatus = 'READY' | 'INCOMPLETE' | 'HAS_WARNINGS' | 'CHECKING' | 'ERROR';
```

#### Database Design
No schema changes required. The feature will leverage existing tables through optimized queries:

```sql
-- Example optimized query for batch validation
WITH slide_data AS (
  SELECT 
    s.id as slide_id,
    s.slide_number,
    s.title,
    sn.id as narrative_id,
    sn.enhanced_narrative_text,
    ss.id as speech_id,
    ss.audio_file_path,
    av.id as avatar_video_id,
    av.published_url as video_published_url,
    am_audio.upload_status as audio_upload_status,
    am_video.upload_status as video_upload_status
  FROM slides s
  LEFT JOIN slide_narratives sn ON s.id = sn.slide_id AND sn.is_active = true
  LEFT JOIN slide_speeches ss ON s.id = ss.slide_id AND ss.is_active = true
  LEFT JOIN avatar_videos av ON s.id = av.slide_id AND av.status = 'COMPLETED'
  LEFT JOIN asset_metadata am_audio ON am_audio.slide_id = s.id AND am_audio.asset_type = 'SLIDE_AUDIO'
  LEFT JOIN asset_metadata am_video ON am_video.slide_id = s.id AND am_video.asset_type = 'SLIDE_AVATAR_VIDEO'
  WHERE s.presentation_id = ?
)
SELECT * FROM slide_data ORDER BY slide_number;
```

## Implementation Roadmap

### Phase 1: Backend Foundation (2-3 days)
- Create DTOs and entity mappings
- Implement PreflightCheckService with basic validation logic
- Create PreflightCheckController with endpoints
- Add repository methods for efficient data fetching
- Implement R2AssetVerificationService
- Add comprehensive unit tests
- Estimated effort: 16-24 hours

### Phase 2: Frontend Implementation (2-3 days)
- Create PreflightCheckModal component
- Implement progress tracking UI
- Add results display with detailed breakdown
- Integrate with AIAnalysisPanel
- Update API service with new endpoints
- Add type definitions
- Implement error handling and retry logic
- Estimated effort: 16-24 hours

### Phase 3: Manual Testing & Integration (1-2 days)
- Manual testing of all check scenarios
- Test with various presentation sizes
- Verify R2 publication status checks work correctly
- Implement real-time progress updates
- Manual user acceptance testing
- Documentation updates
- Estimated effort: 8-16 hours

### Phase 4: Enhancements (Optional, 1 day)
- Add export functionality for check results
- Implement scheduled pre-flight checks
- Add email notifications for check failures
- Create dashboard for historical check results
- Estimated effort: 8 hours

## Risk Assessment

### Technical Risks
1. **Performance Impact**
   - Risk: Checking large presentations (100+ slides) may be slow
   - Mitigation: Implement batch queries and pagination
   - Consider async processing with progress streaming

2. **R2 API Rate Limits**
   - Risk: Batch verification might hit R2 API limits
   - Mitigation: Implement request batching and throttling
   - Add appropriate delays between requests if needed

3. **Database Query Performance**
   - Risk: Complex joins might impact database performance
   - Mitigation: Add appropriate indexes, use query optimization
   - Consider materialized views for frequently accessed data

### Performance Implications
- Expected check time: 50-100ms per slide
- Memory usage: Minimal, results streamed to frontend
- Network overhead: Single batch request, progress via polling/SSE

### Security Considerations
- Ensure proper authorization for pre-flight checks
- Validate presentation ownership before checks
- Sanitize error messages to prevent information leakage
- Implement rate limiting on check endpoints

## Alternative Approaches Considered

### 1. Client-Side Validation
- **Approach**: Fetch all data to frontend and validate there
- **Rejected because**: 
  - Large data transfer overhead
  - R2 verification requires backend access
  - Security concerns with exposing all asset metadata

### 2. Synchronous Batch Processing
- **Approach**: Process all slides synchronously in single request
- **Rejected because**:
  - Poor user experience with long wait times
  - Risk of timeouts for large presentations
  - No progress visibility

### 3. Event-Driven Architecture
- **Approach**: Use message queue for async processing
- **Rejected because**:
  - Adds complexity for relatively simple feature
  - Requires additional infrastructure
  - Overkill for current scale requirements

## Success Metrics

### Performance Benchmarks
- Pre-flight check completion: < 5 seconds for 50 slides
- Modal load time: < 200ms
- Progress update frequency: Every 500ms

### Quality Indicators
- Zero false positives in validation
- 100% accuracy in R2 publication status
- Clear, actionable error messages
- Comprehensive test coverage (> 80%)

### Business Metrics
- Reduction in video generation failures: Target 90% reduction
- User satisfaction: Quick identification of issues
- Time saved: 5-10 minutes per presentation preparation
- Support ticket reduction: 50% fewer asset-related issues

## Dependencies and Prerequisites

### Required Tools or Libraries
- No new major dependencies required
- Existing libraries sufficient for implementation

### Team Skills Needed
- Java/Spring Boot expertise for backend
- React/TypeScript skills for frontend
- Understanding of R2/S3 APIs
- PostgreSQL query optimization knowledge

### Infrastructure Requirements
- No additional infrastructure required
- Existing R2 storage and PostgreSQL sufficient

## Monitoring and Observability

### Logging Requirements
- Log all pre-flight check initiations with presentation ID
- Track individual check failures with detailed context
- Monitor R2 verification response times

### Metrics to Track
- Average check duration by presentation size
- Most common failure types
- R2 verification success rate
- Feature usage frequency

### Alerting Thresholds
- Check duration > 30 seconds
- R2 verification failure rate > 5%
- Database query time > 1 second
- Error rate > 1%

## Migration Strategy

### Rollout Plan
1. Deploy backend changes with feature flag
2. Test with internal presentations
3. Gradual rollout to beta users
4. Monitor metrics and gather feedback
5. Full production release

### Rollback Strategy
- Feature flag to disable pre-flight checks
- Endpoints return "feature unavailable" if issues
- No database migrations to rollback
- Frontend gracefully handles missing endpoint

## Documentation Requirements

### API Documentation
- OpenAPI/Swagger spec for new endpoints
- Request/response examples
- Error code documentation
- Rate limiting information

### User Documentation
- User guide for pre-flight check feature
- Troubleshooting guide for common issues
- Best practices for presentation preparation
- Video tutorial for feature usage

### Developer Documentation
- Architecture decision records
- Code comments for complex logic
- Integration guide for future enhancements
- Performance tuning guidelines

## Conclusion

The Pre-flight Check feature will provide crucial validation capabilities to ensure presentations are fully prepared for video generation. By leveraging existing infrastructure and following established patterns, the implementation minimizes risk while delivering significant value. The phased approach allows for iterative development and testing, ensuring a robust and reliable feature that enhances the overall user experience.

The architecture prioritizes performance, maintainability, and user experience while remaining flexible for future enhancements. With comprehensive error handling, progress tracking, and detailed reporting, users will have complete visibility into their presentation's readiness status.