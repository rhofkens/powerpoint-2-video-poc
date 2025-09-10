# Phase 3: Dual-Mode Asset Handling Implementation TODO

## Overview
Implementation tasks for the dual-mode asset handling system that supports both R2 direct URLs and Shotstack asset uploads based on configuration.

## Priority 1: Core Configuration Infrastructure

### 1.1 Configuration Setup
- [ ] Add asset mode configuration to `application.yml`
  - [ ] Define `shotstack.asset.mode` enum (R2_DIRECT, SHOTSTACK_UPLOAD)
  - [ ] Add `shotstack.asset.default-mode` property
  - [ ] Add `shotstack.asset.force-upload-types` list for specific asset types
  - [ ] Add `shotstack.asset.url-validity-buffer-minutes` (default: 30)
  - [ ] Add environment-specific overrides (dev vs prod)

### 1.2 Configuration Classes
- [ ] Create `AssetHandlingMode` enum in `ai.bluefields.ppt2video.config`
- [ ] Create `AssetModeConfiguration` class with mode selection logic
- [ ] Add mode configuration to existing `ShotstackConfig` class
- [ ] Create `AssetModeContext` to track mode per render job

### 1.3 Database Schema Updates
- [ ] Add `asset_handling_mode` column to `render_jobs` table
- [ ] Add `shotstack_asset_urls` table for tracking uploaded assets
  ```sql
  CREATE TABLE shotstack_asset_urls (
    id UUID PRIMARY KEY,
    asset_metadata_id UUID REFERENCES asset_metadata(id),
    render_job_id UUID REFERENCES render_jobs(id),
    shotstack_url TEXT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    deleted_at TIMESTAMP
  );
  ```
- [ ] Create Flyway migration script

## Priority 2: URL Validation and Management

### 2.1 URL Validation Service
- [ ] Create `PresignedUrlValidator` service
  - [ ] Method: `isUrlValid(String url, LocalDateTime expiresAt)`
  - [ ] Method: `getUrlsNeedingRefresh(List<AssetMetadata> assets, int bufferMinutes)`
  - [ ] Method: `validateCompositionUrls(JsonNode composition)`

### 2.2 URL Regeneration Logic
- [ ] Extend `PresignedUrlService` with batch operations
  - [ ] Method: `regenerateExpiredUrls(List<AssetMetadata> assets)`
  - [ ] Method: `ensureUrlsValid(List<AssetMetadata> assets, Duration minValidity)`
  - [ ] Add caching layer to minimize R2 API calls
  - [ ] Implement retry logic with exponential backoff

### 2.3 URL Tracking Repository
- [ ] Create `ShotstackAssetUrlRepository` JPA repository
- [ ] Add methods for tracking Shotstack-uploaded assets
- [ ] Implement cleanup queries for expired assets

## Priority 3: Shotstack Asset Publisher

### 3.1 Core Publisher Service
- [ ] Create `ShotstackAssetPublisher` service class
  - [ ] Method: `uploadAssetToShotstack(AssetMetadata asset)`
  - [ ] Method: `uploadAssetsInBatch(List<AssetMetadata> assets)`
  - [ ] Method: `deleteAssetFromShotstack(String assetId)`
  - [ ] Implement retry mechanism for failed uploads

### 3.2 Shotstack Client Extensions
- [ ] Add asset upload endpoints to `ShotstackClient`
  - [ ] POST `/assets` endpoint integration
  - [ ] GET `/assets/{id}` status checking
  - [ ] DELETE `/assets/{id}` for cleanup
  - [ ] Handle Shotstack rate limiting

### 3.3 Asset Upload Strategy
- [ ] Create `AssetUploadStrategy` interface
  - [ ] Implementation: `DirectR2Strategy` (uses presigned URLs)
  - [ ] Implementation: `ShotstackUploadStrategy` (uploads to Shotstack)
  - [ ] Factory pattern for strategy selection based on mode

## Priority 4: Composition Service Updates

### 4.1 Asset URL Resolution
- [ ] Create `AssetUrlResolver` component
  - [ ] Method: `resolveAssetUrl(AssetMetadata asset, AssetHandlingMode mode)`
  - [ ] Method: `resolveAllAssetUrls(List<AssetMetadata> assets, RenderJob job)`
  - [ ] Handle mixed-mode scenarios (some R2, some Shotstack)

### 4.2 ShotstackCompositionService Updates
- [ ] Modify `buildFullTimeline()` to accept resolved asset URLs
- [ ] Add `prepareAssetUrls()` method before composition building
- [ ] Update `buildIntroVideoTrack()` to use resolved URL
- [ ] Update future slide/avatar track builders for resolved URLs

### 4.3 Composition Validation
- [ ] Add pre-submission validation for all asset URLs
- [ ] Implement fallback logic if URL validation fails
- [ ] Add composition JSON sanitization for URL formats

## Priority 5: Orchestration Service Integration

### 5.1 VideoStoryOrchestrationService Updates
- [ ] Add `AssetModeConfiguration` dependency injection
- [ ] Modify `renderVideoStory()` to determine asset mode
  - [ ] Check configuration for default mode
  - [ ] Check if override specified in request
  - [ ] Log selected mode for debugging

### 5.2 Asset Preparation Pipeline
- [ ] Create `prepareAssetsForRender()` method
  - [ ] Collect all required assets for composition
  - [ ] Validate/regenerate R2 URLs if in R2_DIRECT mode
  - [ ] Upload to Shotstack if in SHOTSTACK_UPLOAD mode
  - [ ] Return map of asset ID to resolved URL

### 5.3 Error Handling
- [ ] Add comprehensive error handling for URL expiry
- [ ] Implement fallback from R2_DIRECT to SHOTSTACK_UPLOAD on failure
- [ ] Add detailed logging for asset resolution process

## Priority 6: Testing Support

### 6.1 Testing Configuration
- [ ] Add test profile with SHOTSTACK_UPLOAD as default
- [ ] Create mock Shotstack asset endpoint for unit tests
- [ ] Add integration test for both asset modes

### 6.2 Preview Support
- [ ] Add `generatePreviewComposition()` method that forces SHOTSTACK_UPLOAD
- [ ] Create endpoint for Shotstack Studio preview URL generation
- [ ] Add UI button to open composition in Shotstack Studio

### 6.3 Debugging Tools
- [ ] Add endpoint to inspect resolved asset URLs for a render job
- [ ] Create admin endpoint to switch modes for existing video story
- [ ] Add metrics for asset upload success/failure rates

## Priority 7: Cleanup and Optimization

### 7.1 Asset Cleanup Service
- [ ] Extend `AssetCleanupService` for Shotstack assets
  - [ ] Schedule deletion of Shotstack assets after retention period
  - [ ] Track and clean orphaned Shotstack uploads
  - [ ] Add manual cleanup trigger for immediate deletion

### 7.2 Cost Optimization
- [ ] Implement asset deduplication for Shotstack uploads
- [ ] Add caching for frequently used assets (lower thirds, etc.)
- [ ] Create cost tracking metrics for both modes

### 7.3 Performance Optimization
- [ ] Implement parallel asset upload for SHOTSTACK_UPLOAD mode
- [ ] Add connection pooling for Shotstack API calls
- [ ] Optimize URL validation batch operations

## Priority 8: Frontend Integration

### 8.1 Configuration UI
- [ ] Add asset mode selector in video story modal (admin only)
- [ ] Display current mode in render status
- [ ] Show cost estimate based on selected mode

### 8.2 Preview Integration
- [ ] Add "Preview in Shotstack Studio" button when in upload mode
- [ ] Show warning when preview unavailable in R2_DIRECT mode
- [ ] Add tooltip explaining mode differences

### 8.3 Status Reporting
- [ ] Display asset preparation progress
- [ ] Show URL validation status
- [ ] Report asset upload progress for SHOTSTACK_UPLOAD mode

## Implementation Order

### Week 1: Foundation
1. Configuration infrastructure (Priority 1)
2. Database schema updates
3. URL validation service (Priority 2.1)
4. Basic Shotstack asset publisher (Priority 3.1)

### Week 2: Core Implementation
1. URL regeneration logic (Priority 2.2)
2. Asset upload strategies (Priority 3.3)
3. Composition service updates (Priority 4)
4. Orchestration integration (Priority 5)

### Week 3: Testing and Optimization
1. Testing support (Priority 6)
2. Cleanup services (Priority 7.1)
3. Performance optimization (Priority 7.3)
4. Frontend integration (Priority 8)

### Week 4: Polish and Documentation
1. Error handling improvements
2. Cost optimization features
3. Documentation updates
4. Integration testing

## Success Criteria

- [ ] R2_DIRECT mode works without uploading assets to Shotstack
- [ ] SHOTSTACK_UPLOAD mode enables preview in Shotstack Studio
- [ ] Automatic URL regeneration prevents expired URL errors
- [ ] Configuration allows easy switching between modes
- [ ] Both modes produce identical video output
- [ ] Asset cleanup works for both R2 and Shotstack assets
- [ ] Clear logging shows which mode is being used and why
- [ ] Frontend can trigger either mode based on user needs

## Risk Mitigation

1. **URL Expiry During Render**: Implement generous buffer time (30+ minutes)
2. **Shotstack Rate Limits**: Add request queuing and backoff
3. **Upload Failures**: Implement retry with exponential backoff
4. **Mixed Mode Complexity**: Start with all-or-nothing approach, add per-asset mode later
5. **Cost Overruns**: Add spending alerts and automatic mode switching based on budget

## Notes

- Start with intro video only, then extend to slides and avatars
- Consider caching Shotstack upload URLs for frequently used assets
- Monitor R2 API usage to ensure we don't hit rate limits
- Document mode selection logic clearly for operations team
- Add feature flag to disable SHOTSTACK_UPLOAD mode in production if needed