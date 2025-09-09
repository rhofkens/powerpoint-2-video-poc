# Intro Video Preflight Check Integration Plan

## Executive Summary
This plan outlines the implementation strategy for integrating intro video generation status into the existing preflight check system. The intro video check will be a presentation-level check (not slide-level) and will indicate whether an intro video exists and is ready for use.

## Current System Analysis

### Existing Architecture
- **PreflightCheckService**: Currently validates slide-level components (narratives, audio, avatar videos, images)
- **SlideCheckResult**: Per-slide validation results
- **PreflightSummary**: Aggregated statistics for all slides
- **PreflightCheckResponseDto**: Overall response with presentation status

### Key Observations
1. Current system is entirely slide-focused
2. No presentation-level checks exist
3. Status types: READY, INCOMPLETE, HAS_WARNINGS, ERROR
4. Checks verify both existence and R2 publishing status

## Proposed Implementation

### 1. Data Model Changes

#### A. Update PreflightSummary
Add presentation-level fields to track intro video status:

```java
// In PreflightSummary.java
/** Whether the presentation has an intro video */
private boolean hasIntroVideo;

/** Status of the intro video (if exists) */
private CheckStatus introVideoStatus;

/** Intro video generation status if in progress */
private String introVideoGenerationStatus;

/** URL of the published intro video if available */
private String introVideoUrl;
```

#### B. Update PreflightCheckResponseDto
Add presentation-level metadata:

```java
// In PreflightCheckResponseDto.java
/** Presentation-level check results */
private PresentationCheckResult presentationCheckResult;
```

#### C. Create PresentationCheckResult DTO
New class for presentation-level checks:

```java
@Data
@Builder
public class PresentationCheckResult {
    private CheckStatus introVideoStatus;
    private UUID introVideoId;
    private String introVideoUrl;
    private AvatarGenerationStatusType generationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Double durationSeconds;
    private List<String> issues;
    private Map<String, Object> metadata;
}
```

### 2. Service Layer Changes

#### A. Update PreflightCheckService

1. **Add IntroVideoRepository dependency**
```java
private final IntroVideoRepository introVideoRepository;
```

2. **Add presentation-level validation method**
```java
private PresentationCheckResult validatePresentationAssets(
    UUID presentationId,
    boolean checkIntroVideo) {
    
    List<String> issues = new ArrayList<>();
    Map<String, Object> metadata = new HashMap<>();
    CheckStatus introVideoStatus = CheckStatus.NOT_APPLICABLE;
    
    if (checkIntroVideo) {
        // Find the latest intro video for this presentation
        Optional<IntroVideo> introVideo = 
            introVideoRepository.findLatestByPresentationId(presentationId);
        
        if (introVideo.isPresent()) {
            IntroVideo video = introVideo.get();
            metadata.put("introVideoId", video.getId());
            metadata.put("generationStatus", video.getStatus());
            
            switch (video.getStatus()) {
                case COMPLETED:
                    if (video.getPublishedUrl() != null) {
                        introVideoStatus = CheckStatus.PASSED;
                        metadata.put("publishedUrl", video.getPublishedUrl());
                        metadata.put("duration", video.getDurationSeconds());
                    } else {
                        introVideoStatus = CheckStatus.WARNING;
                        issues.add("Intro video generated but not published to R2");
                    }
                    break;
                case PROCESSING:
                case PENDING:
                    introVideoStatus = CheckStatus.IN_PROGRESS;
                    issues.add("Intro video generation in progress");
                    break;
                case FAILED:
                    introVideoStatus = CheckStatus.FAILED;
                    issues.add("Intro video generation failed: " + 
                        video.getErrorMessage());
                    break;
                case CANCELLED:
                    introVideoStatus = CheckStatus.FAILED;
                    issues.add("Intro video generation was cancelled");
                    break;
            }
        } else {
            introVideoStatus = CheckStatus.NOT_FOUND;
            issues.add("No intro video generated for this presentation");
        }
    }
    
    return PresentationCheckResult.builder()
        .introVideoStatus(introVideoStatus)
        .introVideoId(metadata.get("introVideoId"))
        .introVideoUrl(metadata.get("publishedUrl"))
        .generationStatus(metadata.get("generationStatus"))
        .durationSeconds(metadata.get("duration"))
        .issues(issues)
        .metadata(metadata)
        .build();
}
```

3. **Update main runPreflightCheck method**
```java
public PreflightCheckResponseDto runPreflightCheck(
    UUID presentationId, 
    PreflightCheckRequestDto request) {
    
    // ... existing slide validation code ...
    
    // Add presentation-level checks
    boolean checkIntroVideo = request != null && request.isCheckIntroVideo();
    PresentationCheckResult presentationResult = 
        validatePresentationAssets(presentationId, checkIntroVideo);
    
    // Update summary with intro video info
    if (presentationResult != null) {
        summary.setHasIntroVideo(
            presentationResult.getIntroVideoStatus() == CheckStatus.PASSED);
        summary.setIntroVideoStatus(presentationResult.getIntroVideoStatus());
        summary.setIntroVideoUrl(presentationResult.getIntroVideoUrl());
    }
    
    // Determine overall status (consider intro video)
    PreflightStatus overallStatus = 
        determineOverallStatus(summary, slideResults, presentationResult);
    
    // Build response with presentation-level data
    PreflightCheckResponseDto response = PreflightCheckResponseDto.builder()
        .presentationId(presentationId)
        .overallStatus(overallStatus)
        .slideResults(slideResults)
        .presentationCheckResult(presentationResult)
        .summary(summary)
        .checkedAt(Instant.now())
        .build();
}
```

4. **Update status determination logic**
```java
private PreflightStatus determineOverallStatus(
    PreflightSummary summary,
    List<SlideCheckResult> results,
    PresentationCheckResult presentationResult) {
    
    // Check slide-level readiness
    boolean slidesReady = summary.isAllMandatoryChecksPassed();
    
    // Check presentation-level readiness
    boolean introVideoReady = presentationResult == null || 
        presentationResult.getIntroVideoStatus() == CheckStatus.PASSED ||
        presentationResult.getIntroVideoStatus() == CheckStatus.NOT_APPLICABLE;
    
    boolean hasIntroVideoWarning = presentationResult != null &&
        (presentationResult.getIntroVideoStatus() == CheckStatus.WARNING ||
         presentationResult.getIntroVideoStatus() == CheckStatus.IN_PROGRESS);
    
    if (slidesReady && introVideoReady) {
        if (summary.getSlidesWithUnpublishedAssets() > 0 || hasIntroVideoWarning) {
            return PreflightStatus.HAS_WARNINGS;
        }
        return PreflightStatus.READY;
    }
    
    return PreflightStatus.INCOMPLETE;
}
```

### 3. Repository Layer Changes

#### A. Add method to IntroVideoRepository
```java
@Query("SELECT iv FROM IntroVideo iv WHERE iv.presentationId = :presentationId " +
       "ORDER BY iv.createdAt DESC LIMIT 1")
Optional<IntroVideo> findLatestByPresentationId(@Param("presentationId") UUID presentationId);
```

### 4. DTO Changes

#### A. Update PreflightCheckRequestDto
```java
// Add field to control intro video checking
private boolean checkIntroVideo = true;
```

#### B. Add CheckStatus enum values
```java
public enum CheckStatus {
    PASSED,
    FAILED,
    WARNING,
    NOT_APPLICABLE,
    IN_PROGRESS,    // New: for ongoing generation
    NOT_FOUND       // New: for missing resources
}
```

### 5. Frontend Integration

#### A. Update PreflightCheckPanel.tsx
1. Display intro video status in the summary section
2. Show generation progress if IN_PROGRESS
3. Add button to generate intro video if NOT_FOUND
4. Display video preview if PASSED

#### B. Update API service types
```typescript
interface PreflightSummary {
  // ... existing fields ...
  hasIntroVideo: boolean;
  introVideoStatus: CheckStatus;
  introVideoGenerationStatus?: string;
  introVideoUrl?: string;
}

interface PresentationCheckResult {
  introVideoStatus: CheckStatus;
  introVideoId?: string;
  introVideoUrl?: string;
  generationStatus?: string;
  durationSeconds?: number;
  issues: string[];
  metadata: Record<string, any>;
}

interface PreflightCheckResponse {
  // ... existing fields ...
  presentationCheckResult?: PresentationCheckResult;
}
```

### 6. REST API Changes

#### A. Update PreflightController endpoint
No changes needed - existing endpoint will return enhanced response

#### B. Optional: Add dedicated intro video check endpoint
```java
@GetMapping("/{presentationId}/intro-video-status")
public ResponseEntity<PresentationCheckResult> checkIntroVideoStatus(
    @PathVariable UUID presentationId) {
    // Quick check just for intro video
}
```

## Implementation Steps

### Phase 1: Backend Data Model (30 mins)
1. Create PresentationCheckResult DTO
2. Update PreflightSummary with intro video fields
3. Update PreflightCheckResponseDto
4. Add CheckStatus enum values

### Phase 2: Repository Layer (15 mins)
1. Add findLatestByPresentationId to IntroVideoRepository
2. Test query execution

### Phase 3: Service Layer (45 mins)
1. Add IntroVideoRepository dependency to PreflightCheckService
2. Implement validatePresentationAssets method
3. Update runPreflightCheck to include presentation checks
4. Update determineOverallStatus logic
5. Update PreflightCheckRequestDto

### Phase 4: Frontend Types (20 mins)
1. Update TypeScript interfaces
2. Update API service types

### Phase 5: Frontend UI (30 mins)
1. Update PreflightCheckPanel to display intro video status
2. Add visual indicators for different states
3. Add actions for missing/failed intro videos

### Phase 6: Testing (30 mins)
1. Test with presentations having no intro video
2. Test with intro video in various states (pending, processing, completed, failed)
3. Test R2 publishing status checks
4. Verify frontend displays all states correctly

## Success Criteria

1. **Visibility**: Preflight check clearly shows intro video status
2. **Accuracy**: Status correctly reflects database state
3. **Actionable**: Users can identify and resolve intro video issues
4. **Performance**: No significant impact on preflight check speed
5. **Consistency**: Intro video checks follow same patterns as slide checks

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Performance impact of additional DB queries | Use efficient queries, consider caching |
| Confusion between slide and presentation checks | Clear UI separation and labeling |
| Handling multiple intro videos per presentation | Always use latest, clear indication in UI |
| Stale status during generation | Consider real-time updates via polling |

## Future Enhancements

1. **Real-time Updates**: WebSocket support for live generation status
2. **Batch Operations**: Generate intro videos for multiple presentations
3. **Quality Checks**: Validate intro video content matches presentation
4. **Caching**: Cache intro video status for performance
5. **History**: Show history of intro video generation attempts

## Conclusion

This implementation adds comprehensive intro video checking to the preflight system while maintaining backward compatibility and following existing patterns. The changes are modular and can be implemented incrementally with minimal risk to existing functionality.