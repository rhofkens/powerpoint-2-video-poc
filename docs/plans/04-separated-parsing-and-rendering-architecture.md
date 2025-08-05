# Separated Parsing and Rendering Architecture Plan

## Overview

This document outlines the architectural changes to separate PowerPoint processing into three independent, asynchronous activities:
1. File Upload & Storage
2. Parsing (metadata extraction)
3. Rendering (image generation)

## Current Problems

- Parsing and rendering are intertwined
- MS Graph renderer uploads files multiple times (once per slide)
- Slow response times during upload
- Cannot show partial progress
- Difficult to switch renderers or re-render

## Proposed Architecture

### Phase 1: File Upload & Storage

**Responsibility**: Accept and store PowerPoint files only

- Accept PowerPoint file upload
- Store file locally in presentation storage
- Create database record with status `UPLOADED`
- Return immediately to user with presentation ID
- Trigger async parsing and rendering jobs
- **No parsing or rendering happens in this phase**

### Phase 2: Asynchronous Parsing

**Responsibility**: Extract metadata and structure from PowerPoint

- Triggered automatically after upload completes
- Runs in separate thread using `@Async`
- Uses Aspose or POI parser to extract:
  - Total slide count
  - For each slide:
    - Slide number
    - Title
    - Speaker notes
    - Text content
    - Embedded images metadata
- Stores all parsed data in database
- Updates presentation status: `UPLOADED` → `PARSING` → `PARSED`
- **No image generation happens in this phase**

### Phase 3: Asynchronous Rendering

**Responsibility**: Generate slide images using configured renderer

- Triggered automatically after upload completes (parallel to parsing)
- Runs in separate thread using `@Async`
- Updates presentation status: `RENDERING` → `COMPLETED`

#### Renderer-Specific Workflows

##### MS Graph Renderer:
1. Upload PowerPoint to SharePoint/OneDrive (once per presentation)
2. Convert entire presentation to PDF (once)
3. Cache PDF data in memory/disk
4. For each slide:
   - Extract slide page from cached PDF
   - Resize to target dimensions
   - Save image to local storage
   - Update slide record with image path
   - Update slide rendering status

##### Local Renderers (Aspose/POI):
1. Load presentation once
2. For each slide:
   - Render slide to image at target resolution
   - Save image to local storage
   - Update slide record with image path
   - Update slide rendering status

## Database Schema Updates

### Presentation Table Additions
```sql
-- Status progression
status ENUM('UPLOADED', 'PARSING', 'PARSED', 'RENDERING', 'COMPLETED', 'FAILED')

-- Timestamps for tracking
parsing_started_at TIMESTAMP
parsing_completed_at TIMESTAMP
rendering_started_at TIMESTAMP
rendering_completed_at TIMESTAMP

-- Rendering information
renderer_used VARCHAR(50)
rendering_error_message TEXT
```

### Slide Table Additions
```sql
-- Image information (nullable until rendered)
image_path VARCHAR(500)
image_generated_at TIMESTAMP
image_width INTEGER
image_height INTEGER

-- Rendering status
rendering_status ENUM('PENDING', 'RENDERING', 'COMPLETED', 'FAILED')
rendering_error_message TEXT
```

## Implementation Components

### 1. PresentationUploadService
**Responsibility**: Handle file uploads only

```java
@Service
public class PresentationUploadService {
    public PresentationUploadResponse uploadPresentation(MultipartFile file) {
        // 1. Validate file
        // 2. Store file locally
        // 3. Create DB record with UPLOADED status
        // 4. Trigger async jobs
        // 5. Return immediately
    }
}
```

### 2. PresentationParsingService
**Responsibility**: Extract metadata asynchronously

```java
@Service
public class PresentationParsingService {
    @Async
    public void parsePresentation(Long presentationId) {
        // 1. Update status to PARSING
        // 2. Load file
        // 3. Extract metadata using parser
        // 4. Save slides to database (without images)
        // 5. Update status to PARSED
    }
}
```

### 3. SlideRenderingService
**Responsibility**: Coordinate rendering workflow

```java
@Service
public class SlideRenderingService {
    @Async
    public void renderPresentation(Long presentationId, String rendererName) {
        // 1. Update status to RENDERING
        // 2. Get appropriate renderer
        // 3. Execute renderer-specific workflow
        // 4. Update slides with image paths
        // 5. Update status to COMPLETED
    }
}
```

### 4. RenderingStrategy Interface
**Responsibility**: Define renderer contract

```java
public interface RenderingStrategy {
    void prepareForRendering(File presentationFile);
    RenderedSlide renderSlide(int slideNumber, int width, int height);
    void cleanup();
}
```

### 5. MSGraphRenderingStrategy
**Responsibility**: Handle MS Graph-specific rendering

```java
@Component
public class MSGraphRenderingStrategy implements RenderingStrategy {
    private String uploadedFileId;
    private byte[] cachedPdfData;
    
    @Override
    public void prepareForRendering(File presentationFile) {
        // 1. Upload to SharePoint (once)
        // 2. Convert to PDF (once)
        // 3. Cache PDF data
    }
    
    @Override
    public RenderedSlide renderSlide(int slideNumber, int width, int height) {
        // Extract single slide from cached PDF
    }
}
```

## API Endpoints

### Upload Endpoint
```
POST /api/presentations/upload
Content-Type: multipart/form-data

Response (immediate):
{
    "id": "123",
    "status": "UPLOADED",
    "message": "File uploaded successfully. Processing started."
}
```

### Status Endpoint
```
GET /api/presentations/{id}/status

Response:
{
    "id": "123",
    "status": "RENDERING",
    "parsingProgress": {
        "status": "COMPLETED",
        "slideCount": 10
    },
    "renderingProgress": {
        "status": "IN_PROGRESS",
        "completedSlides": 3,
        "totalSlides": 10,
        "renderer": "MSGRAPH"
    }
}
```

### Re-render Endpoint
```
POST /api/presentations/{id}/render
{
    "renderer": "MSGRAPH",
    "force": true
}
```

### Get Slide Image
```
GET /api/presentations/{id}/slides/{slideNum}/image

Returns:
- Rendered image if available
- 404 if not yet rendered
- Placeholder image (optional)
```

## Benefits

1. **Fast Upload Response**: User gets immediate feedback
2. **Progressive Loading**: UI can show slides as they become available
3. **Independent Processing**: Parsing and rendering don't block each other
4. **Efficient MS Graph Usage**: Single upload and PDF conversion per presentation
5. **Flexibility**: Easy to switch renderers or re-render
6. **Resilience**: Can retry failed operations independently
7. **Scalability**: Can process multiple presentations in parallel

## Migration Strategy

1. Add new database columns (nullable)
2. Implement new services alongside existing code
3. Add feature flag to switch between old/new architecture
4. Migrate existing presentations
5. Remove old code

## Virtual Threads Configuration

Since we're using Java 24, we'll leverage virtual threads for all async operations:

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "virtualThreadExecutor")
    public AsyncTaskExecutor virtualThreadExecutor() {
        // Creates a new virtual thread for each task
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

All `@Async` methods will use virtual threads:

```java
@Async("virtualThreadExecutor")
public void parsePresentation(Long presentationId) {
    // I/O-bound parsing work - perfect for virtual threads
}

@Async("virtualThreadExecutor")
public void renderPresentation(Long presentationId) {
    // I/O-bound rendering work - perfect for virtual threads
}
```

Benefits of virtual threads for our use case:
- No thread pool sizing concerns
- Can handle thousands of concurrent presentations
- Perfect for I/O-bound operations (DB, file system, network)
- Simplified configuration

## Configuration

```properties
# Rendering configuration
app.rendering.default-renderer=MSGRAPH
app.rendering.image-format=PNG
app.rendering.default-width=1920
app.rendering.default-height=1080

# MS Graph specific
app.msgraph.pdf-cache-enabled=true
app.msgraph.pdf-cache-ttl=3600

# Virtual thread monitoring
management.metrics.enable.jvm.threads=true
```

## Success Metrics

- Upload response time < 2 seconds
- Parsing completion < 10 seconds for 50-slide presentation
- Rendering throughput > 2 slides/second
- MS Graph API calls reduced by 90%
- Zero duplicate file uploads

## Next Steps

1. Review and approve this plan
2. Create database migrations
3. Implement async job infrastructure
4. Build new services
5. Update API endpoints
6. Test with various renderers
7. Deploy with feature flag