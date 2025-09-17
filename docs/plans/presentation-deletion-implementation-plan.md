# Presentation Deletion Implementation Plan

## Overview
This document outlines the implementation plan for adding a comprehensive presentation deletion feature to the PowerPoint-to-Video POC application. The feature will allow users to delete presentations and all associated resources through a REST API endpoint.

## Current State Analysis

### Database Entities with Presentation Dependencies
Based on the foreign key analysis, the following tables have references to presentations:

1. **slides** - Core slide data (CASCADE via JPA)
2. **deck_analysis** - AI analysis of the deck (CASCADE via JPA)
3. **slide_speeches** - Generated speech for slides
4. **asset_metadata** - R2 storage metadata
5. **avatar_videos** - Avatar video generation records
6. **intro_videos** - Intro video records
7. **video_stories** - Shotstack video compositions
8. **render_jobs** - Rendering job tracking
9. **published_assets** - Published R2 assets

### Additional Related Entities (via slides)
- **slide_analysis** - AI analysis per slide
- **slide_narratives** - Generated narratives per slide
- **slide_images** - Slide image metadata

### External Resources to Clean Up

#### 1. Local File System
- **Presentation file**: `./storage/presentations/{filename}.pptx`
- **Slide images**: `./storage/presentations/{presentationId}/slide_{slideNumber}.png`
- **Placeholder images**: Temporary images during processing

#### 2. Cloudflare R2 Storage
Assets stored in two buckets:
- **video-assets**: Final published assets
- **video-generation**: Temporary generation assets

Asset types to delete:
- PRESENTATION_FILE
- SLIDE_IMAGE
- SLIDE_AUDIO
- SLIDE_VIDEO
- SLIDE_AVATAR_VIDEO
- INTRO_VIDEO
- FINAL_VIDEO

#### 3. Shotstack Resources
- Uploaded source assets
- Rendered videos (handled via R2)

## Implementation Approach

### Architecture Decision
**Selected Approach: Service-Layer Orchestrated Delete**

Reasons:
- Provides full control over deletion order
- Enables cleanup of external resources (files, R2, Shotstack)
- Better error handling and recovery
- Detailed logging for audit trail
- Can be extended with soft delete later if needed

### Components to Modify/Create

#### 1. PresentationController Enhancement
Add DELETE endpoint:
```java
@DeleteMapping("/{id}")
public ResponseEntity<?> deletePresentation(@PathVariable String id)
```

#### 2. PresentationService Enhancement
Extend existing PresentationService with comprehensive deletion logic:
```java
public void deletePresentation(UUID presentationId)
```

#### 3. FileStorageService Enhancement
Add methods for file cleanup:
```java
public void deletePresentationFiles(String filePath)
public void deletePresentationDirectory(UUID presentationId)
```

#### 4. R2AssetService Enhancement
Utilize existing `deleteAsset` method and add batch deletion:
```java
public void deleteAssetsByPresentation(UUID presentationId)
```

#### 5. ShotstackVideoProvider
Utilize existing `deleteAsset` method for Shotstack cleanup

## Detailed Deletion Flow

### Phase 1: Validation
1. Verify presentation exists
2. Check if any active processes are running (rendering, generation)
3. Log deletion request with user context

### Phase 2: External Resource Cleanup
Order is important to prevent orphaned resources:

1. **Stop Active Processes**
   - Cancel any in-progress rendering jobs
   - Stop avatar video generation
   - Halt video story creation

2. **Delete Shotstack Assets**
   - Query AssetMetadata for Shotstack source IDs
   - Call ShotstackVideoProvider.deleteAsset() for each
   - Log failures but continue (non-critical)

3. **Delete R2 Assets**
   - Query all AssetMetadata for presentation
   - For each asset:
     - Delete from R2 using existing deleteAsset method
     - Delete presigned URLs
     - Delete metadata record
   - Use batch processing for efficiency

4. **Delete Local Files**
   - Delete presentation directory: `./storage/presentations/{presentationId}/`
   - Delete original presentation file
   - Clean up any temporary files

### Phase 3: Database Cleanup
Delete in order to respect foreign key constraints:

1. **Delete Child Records** (handled by CASCADE or explicitly)
   - Published assets
   - Render jobs
   - Video stories
   - Intro videos
   - Avatar videos
   - Asset metadata
   - Slide speeches
   - Slide narratives (via slides CASCADE)
   - Slide analysis (via slides CASCADE)
   - Slide images (via slides CASCADE)
   - Slides (CASCADE)
   - Deck analysis (CASCADE)

2. **Delete Presentation**
   - Final deletion of presentation record
   - This triggers CASCADE deletions

### Phase 4: Post-Deletion
1. Log successful deletion
2. Return success response with deletion summary

## Error Handling Strategy

### Transactional Boundaries
- Database operations within transaction
- External resource cleanup before transaction
- Compensating actions on failure

### Failure Scenarios

1. **Shotstack Deletion Failure**
   - Log error
   - Continue with deletion (non-critical)
   - Add to cleanup queue for retry

2. **R2 Deletion Failure**
   - Log error with asset details
   - Mark asset as "pending deletion"
   - Continue with other assets
   - Fail the overall deletion if >50% fail

3. **Local File Deletion Failure**
   - Log error with file path
   - Continue with deletion
   - Add to manual cleanup log

4. **Database Deletion Failure**
   - Rollback transaction
   - Keep external resources (safer than orphaning DB records)
   - Return error to user

## Implementation Steps

### Step 1: Backend Infrastructure (Priority: High)
1. Enhance FileStorageService with deletion methods
2. Extend AssetMetadataService for batch operations
3. Update PresentationService with orchestration logic
4. Add DELETE endpoint to PresentationController

### Step 2: Error Handling & Logging (Priority: High)
1. Create DeletionResult DTO for tracking
2. Implement comprehensive logging
3. Add deletion audit trail

### Step 3: Testing (Priority: High)
1. Unit tests for each service method
2. Integration tests for full deletion flow
3. Test failure scenarios
4. Performance test with large presentations

### Step 4: Frontend Integration (Priority: Medium)
1. Add delete button to presentation list
2. Confirmation dialog
3. Progress indicator for deletion
4. Error handling and user feedback

### Step 5: Monitoring & Cleanup (Priority: Low)
1. Add metrics for deletion operations
2. Create scheduled job for orphaned resource cleanup
3. Admin dashboard for deletion audit log

## API Specification

### Request
```
DELETE /api/presentations/{id}
```

### Response - Success
```json
{
  "status": "success",
  "message": "Presentation deleted successfully",
  "deletionSummary": {
    "presentationId": "uuid",
    "slidesDeleted": 10,
    "assetsDeleted": 25,
    "storageFreed": 1073741824,
    "deletionTime": "2025-01-09T10:30:00Z"
  }
}
```

### Response - Partial Success
```json
{
  "status": "partial_success",
  "message": "Presentation deleted with some errors",
  "deletionSummary": {
    "presentationId": "uuid",
    "slidesDeleted": 10,
    "assetsDeleted": 20,
    "assetsFailed": 5,
    "errors": [
      "Failed to delete Shotstack asset: abc123",
      "Failed to delete R2 asset: xyz789"
    ]
  }
}
```

### Response - Failure
```json
{
  "status": "error",
  "message": "Failed to delete presentation",
  "error": "Presentation is currently being processed",
  "errorCode": "RESOURCE_BUSY"
}
```

## Security Considerations

1. **Authorization**: Verify user has permission to delete
2. **Rate Limiting**: Prevent bulk deletion attacks
3. **Soft Delete Option**: Consider adding for recovery
4. **Audit Trail**: Log who deleted what and when

## Performance Considerations

1. **Batch Operations**: Delete R2 assets in batches
2. **Async Processing**: Consider async deletion for large presentations
3. **Connection Pooling**: Ensure adequate connections for parallel operations
4. **Timeout Handling**: Set appropriate timeouts for external services

## Migration & Rollback

1. **No Database Migration Required**: Using existing schema
2. **Feature Flag**: Add flag to enable/disable deletion
3. **Rollback Plan**: Disable endpoint if issues arise

## Testing Checklist

- [ ] Delete presentation with no slides
- [ ] Delete presentation with slides but no media
- [ ] Delete presentation with full media (images, audio, video)
- [ ] Delete presentation with active render job
- [ ] Delete presentation with R2 assets
- [ ] Delete presentation with Shotstack assets
- [ ] Concurrent deletion attempts
- [ ] Delete non-existent presentation
- [ ] Network failure during R2 deletion
- [ ] Database constraint violation handling

## Future Enhancements

1. **Soft Delete**: Add `deleted_at` timestamp for recovery
2. **Bulk Delete**: Delete multiple presentations
3. **Scheduled Deletion**: Delete after retention period
4. **Archive Instead**: Move to cold storage instead of delete
5. **Deletion Queue**: Background processing for large deletions
6. **Recovery Window**: 30-day recovery period before permanent deletion

## Estimated Timeline

- Backend Implementation: 4-6 hours
- Testing: 2-3 hours
- Frontend Integration: 2 hours
- Documentation: 1 hour
- **Total: 9-12 hours**

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Orphaned R2 assets | Medium | Scheduled cleanup job |
| Cascade deletion too aggressive | High | Careful testing, soft delete option |
| Performance impact on large presentations | Medium | Async processing option |
| Partial deletion state | High | Comprehensive error handling |
| Lost data from accidental deletion | High | Confirmation dialog, audit log |

## Approval Checklist

- [ ] Architecture approach approved
- [ ] Deletion order confirmed
- [ ] Error handling strategy accepted
- [ ] API specification reviewed
- [ ] Security measures adequate
- [ ] Timeline acceptable
- [ ] Risks understood and mitigated

---

**Document Version**: 1.0
**Created**: 2025-01-09
**Author**: System Architect
**Status**: PENDING APPROVAL