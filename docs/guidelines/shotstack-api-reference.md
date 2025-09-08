# ShotStack API Complete Reference for Java Development

## Table of Contents
1. [Overview](#overview)
2. [Authentication and Environments](#authentication-and-environments)
3. [API Endpoints](#api-endpoints)
4. [JSON Schema Reference](#json-schema-reference)
5. [Video Editing Concepts](#video-editing-concepts)
6. [Java Implementation Guide](#java-implementation-guide)
7. [Constraints and Limitations](#constraints-and-limitations)
8. [Best Practices](#best-practices)

## Overview

ShotStack is a cloud-based video editing API that enables programmatic video creation through JSON-based edit specifications. The platform provides RESTful APIs for video rendering, asset management, and AI-powered content generation.

### Key Features
- JSON-based video editing
- Timeline and track-based composition
- Template system with merge fields
- AI asset generation (text-to-speech)
- Global CDN hosting
- Webhook notifications

## Authentication and Environments

### API Environments

**Production Endpoints (`v1`)**
```
https://api.shotstack.io/edit/v1
https://api.shotstack.io/serve/v1
https://api.shotstack.io/ingest/v1
https://api.shotstack.io/create/v1
```

**Sandbox/Development Endpoints (`stage`)**
```
https://api.shotstack.io/edit/stage
https://api.shotstack.io/serve/stage
https://api.shotstack.io/ingest/stage
https://api.shotstack.io/create/stage
```

### Authentication Method
```http
x-api-key: YOUR_API_KEY_HERE
Content-Type: application/json
Accept: application/json
```

**Key Details:**
- Header-based authentication using `x-api-key`
- Separate keys for sandbox and production
- 40-character alphanumeric string format
- Same key works across all API endpoints

## API Endpoints

### Edit API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/render` | Queue and render video/audio/image edits |
| GET | `/render/{id}` | Get render status by ID |
| POST | `/templates` | Create reusable templates |
| GET | `/templates` | List all templates |
| GET | `/templates/{id}` | Retrieve template by ID |
| PUT | `/templates/{id}` | Update existing template |
| DELETE | `/templates/{id}` | Delete template |
| POST | `/templates/render` | Render from template with merge fields |
| GET | `/probe/{url}` | Inspect media assets using FFprobe |
| POST | `/upload` | Get signed URL for direct uploads |

### Serve API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/assets/{id}` | Get asset details by asset ID |
| DELETE | `/assets/{id}` | Delete asset by asset ID |
| GET | `/assets/render/{id}` | Get assets by render ID |
| POST | `/assets` | Transfer assets from public URLs |

### Ingest API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/sources` | Fetch and ingest source files from URLs |
| GET | `/sources` | List all ingested sources |
| GET | `/sources/{id}` | Get source details by ID |
| DELETE | `/sources/{id}` | Delete ingested source |

### Create API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/assets` | Generate assets using AI services |
| GET | `/assets/{id}` | Get generated asset status and details |

## JSON Schema Reference

### Complete Edit Structure

```json
{
  "timeline": {
    "soundtrack": {
      "src": "string",          // Required: URL to mp3 file
      "effect": "string",       // Optional: "fadeIn", "fadeOut", "fadeInFadeOut"
      "volume": "number"        // Optional: 0-1, default 1
    },
    "background": "string",     // Optional: hex color (#000000) or image URL
    "fonts": [
      {
        "src": "string"         // Required: URL to font file (.ttf, .otf)
      }
    ],
    "tracks": [                 // Required: array of track objects
      {
        "clips": [              // Required: array of clip objects
          {
            "asset": {},        // Required: asset object
            "start": "number",  // Required: seconds, "auto", or "end"
            "length": "number", // Required: seconds, "auto", or "end"
            "fit": "string",    // Optional: "contain", "cover", "crop", "none", "fill"
            "scale": "number",  // Optional: 0-1 scale factor
            "position": "string", // Optional: positioning preset
            "offset": {
              "x": "number",    // -1 to 1
              "y": "number"     // -1 to 1
            },
            "transition": {
              "in": "string",   // Transition effect name
              "out": "string"   // Transition effect name
            },
            "effect": "string", // Motion effect name
            "filter": "string", // Filter effect name
            "opacity": "number", // 0-1, default 1
            "transform": {
              "rotate": {
                "angle": "number"
              },
              "skew": {
                "x": "number",
                "y": "number"
              },
              "flip": {
                "horizontal": "boolean",
                "vertical": "boolean"
              }
            }
          }
        ]
      }
    ],
    "cache": "boolean"          // Optional: enable caching, default true
  },
  "output": {
    "format": "string",         // Required: "mp4", "gif", "jpg", "png", "bmp", "mp3"
    "resolution": "string",     // Optional: "preview", "mobile", "sd", "hd", "1080"
    "aspectRatio": "string",    // Optional: "16:9", "9:16", "1:1", "4:5", "4:3"
    "size": {
      "width": "number",
      "height": "number"
    },
    "fps": "number",            // Optional: 12, 15, 25, 30
    "scaleTo": "string",        // Optional: "preview", "low", "medium", "high"
    "quality": "string",        // Optional: "low", "medium", "high"
    "repeat": "boolean",        // Optional: loop video/gif
    "mute": "boolean",          // Optional: remove audio
    "range": {
      "start": "number",
      "length": "number"
    },
    "poster": {
      "capture": "number"       // Time in seconds to capture frame
    },
    "thumbnail": {
      "capture": "number",
      "scale": "number"
    },
    "destinations": [
      {
        "provider": "string",   // "shotstack", "s3", "mux"
        "exclude": "boolean"
      }
    ]
  },
  "merge": [                    // Optional: template merge fields
    {
      "find": "string",
      "replace": "any"
    }
  ],
  "callback": "string",         // Optional: webhook URL
  "disk": "string"              // Optional: "local" or "mount"
}
```

### Asset Types

#### VideoAsset
```json
{
  "type": "video",
  "src": "string",              // Required: URL to video file
  "transcode": "boolean",       // Force preprocessing
  "trim": "number",             // Trim from start in seconds
  "volume": "number",           // 0-1, default 1
  "volumeEffect": "string",     // "fadeIn", "fadeOut", "fadeInFadeOut"
  "speed": "number",            // Playback speed multiplier
  "crop": {
    "top": "number",            // 0-1
    "bottom": "number",         // 0-1
    "left": "number",           // 0-1
    "right": "number"           // 0-1
  },
  "chromaKey": {
    "color": "string",          // Hex color to remove
    "threshold": "number",      // 0-255, sensitivity
    "halo": "number"            // 0-255, edge softening
  }
}
```

#### ImageAsset
```json
{
  "type": "image",
  "src": "string"               // Required: URL to image file
}
```

#### AudioAsset
```json
{
  "type": "audio",
  "src": "string",              // Required: URL to audio file
  "trim": "number",             // Trim from start in seconds
  "volume": "number",           // 0-1, default 1
  "volumeEffect": "string"      // "fadeIn", "fadeOut", "fadeInFadeOut"
}
```

#### TitleAsset
```json
{
  "type": "title",
  "text": "string",             // Required: text content
  "style": "string",            // Required: style preset name
  "color": "string",            // Optional: hex color
  "size": "string",             // "x-small", "small", "medium", "large", "x-large"
  "background": "string",       // Optional: hex color
  "position": "string"          // Optional: positioning preset
}
```

#### HtmlAsset
```json
{
  "type": "html",
  "html": "string",             // Required: HTML content
  "css": "string",              // Optional: CSS styles
  "width": "number",            // Optional: width in pixels
  "height": "number",           // Optional: height in pixels
  "background": "string",       // Optional: hex color with transparency
  "position": "string"          // Optional: positioning preset
}
```

#### LumaAsset
```json
{
  "type": "luma",
  "src": "string"               // Required: URL to luma matte video
}
```

### Effects and Transitions

**Transition Types:**
- Basic: `fade`, `reveal`
- Wipes: `wipeLeft`, `wipeRight`
- Slides: `slideLeft`, `slideRight`, `slideUp`, `slideDown`
- Carousel: `carouselLeft`, `carouselRight`, `carouselUp`, `carouselDown`
- Special: `shuffle`
- Speed Variations: Append `Fast` or `Slow` (e.g., `fadeFast`)

**Motion Effects:**
- Zoom: `zoomIn`, `zoomOut`, `zoomInSlow`, `zoomInFast`
- Pan: `slideLeft`, `slideRight`, `slideUp`, `slideDown`

**Filter Effects:**
- Color: `greyscale`, `monochrome`, `invert`
- Enhancement: `boost`, `contrast`, `darken`, `lighten`
- Blur: `blur`

**Position Presets:**
`top`, `topRight`, `right`, `bottomRight`, `bottom`, `bottomLeft`, `left`, `topLeft`, `center`

### Response Formats

**Render Response:**
```json
{
  "success": true,
  "message": "Created",
  "response": {
    "message": "Render Successfully Queued",
    "id": "2abd5c11-0f3d-4c6d-ba20-235fc9b8e8b7"
  }
}
```

**Render Status Response:**
```json
{
  "success": true,
  "message": "OK",
  "response": {
    "id": "2abd5c11-0f3d-4c6d-ba20-235fc9b8e8b7",
    "owner": "5ca6hu7s9k",
    "plan": "basic",
    "status": "done",
    "url": "https://cdn.shotstack.io/au/v1/5ca6hu7s9k/video.mp4",
    "data": {
      "timeline": {},
      "output": {}
    },
    "created": "2021-06-30T09:42:29.446Z",
    "updated": "2021-06-30T09:42:30.168Z"
  }
}
```

## Video Editing Concepts

### Timeline Structure
- **Timeline**: Represents total video duration in seconds
- **Hierarchical**: Edit → Timeline → Tracks → Clips → Assets
- **Time-based**: All positioning based on seconds from start

### Track System
- **Layering**: Top tracks overlay bottom tracks
- **Z-Order**: First array element = top layer
- **Unlimited Tracks**: No limit on track count
- **Organization**: Logical separation (titles, watermarks, main content)

### Clip Timing
- **Start/Length**: Define when clip appears and duration
- **Smart Values**: "auto" for sequential, "end" for timeline end
- **No Overlap**: Clips on same track cannot overlap

### Composition Rules
- **Alpha Support**: PNG and MOV with transparency
- **Chroma Key**: Green screen removal
- **Luma Mattes**: Custom transparency masks
- **Opacity Control**: 0-1 for all clips

### Rendering Process
1. **Validation**: JSON structure validated
2. **Download**: Assets fetched and cached
3. **Preprocessing**: Video compatibility processing
4. **Rendering**: Timeline processed
5. **Output**: Final file generated

### Render States
`queued` → `fetching` → `rendering` → `done`/`failed`

## Java Implementation Guide

### Maven Dependencies
```xml
<!-- Jackson for JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Java 11+ HttpClient (built-in) -->
<!-- Or alternative HTTP clients -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
</dependency>
```

### Basic Java Client Implementation

```java
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ShotStackClient {
    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    
    public ShotStackClient(String apiKey, boolean useStaging) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .build();
        this.apiKey = apiKey;
        this.baseUrl = useStaging ? 
            "https://api.shotstack.io/edit/stage" : 
            "https://api.shotstack.io/edit/v1";
        this.objectMapper = new ObjectMapper();
    }
    
    public RenderResponse submitRender(Edit edit) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(edit);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/render"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(30))
            .build();
            
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
            
        if (response.statusCode() != 201) {
            throw new RuntimeException("Render failed: " + response.body());
        }
        
        return objectMapper.readValue(response.body(), RenderResponse.class);
    }
    
    public RenderStatus checkStatus(String renderId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/render/" + renderId))
            .header("x-api-key", apiKey)
            .GET()
            .build();
            
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
            
        return objectMapper.readValue(response.body(), RenderStatus.class);
    }
}
```

### Model Classes with Builder Pattern

```java
public class Edit {
    private Timeline timeline;
    private Output output;
    private List<MergeField> merge;
    private String callback;
    
    private Edit(Builder builder) {
        this.timeline = builder.timeline;
        this.output = builder.output;
        this.merge = builder.merge;
        this.callback = builder.callback;
    }
    
    public static class Builder {
        private Timeline timeline;
        private Output output;
        private List<MergeField> merge;
        private String callback;
        
        public Builder timeline(Timeline timeline) {
            this.timeline = Objects.requireNonNull(timeline);
            return this;
        }
        
        public Builder output(Output output) {
            this.output = Objects.requireNonNull(output);
            return this;
        }
        
        public Builder merge(List<MergeField> merge) {
            this.merge = merge;
            return this;
        }
        
        public Builder callback(String callback) {
            this.callback = callback;
            return this;
        }
        
        public Edit build() {
            Objects.requireNonNull(timeline, "Timeline is required");
            Objects.requireNonNull(output, "Output is required");
            return new Edit(this);
        }
    }
    
    // Getters...
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Timeline {
    private List<Track> tracks;
    private Soundtrack soundtrack;
    private String background;
    private List<Font> fonts;
    private Boolean cache;
    
    // Constructors, getters, setters...
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Track {
    private List<Clip> clips;
    
    // Constructors, getters, setters...
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Clip {
    private Asset asset;
    private Object start;  // Can be Number or String ("auto", "end")
    private Object length; // Can be Number or String ("auto", "end")
    private String fit;
    private Double scale;
    private String position;
    private Offset offset;
    private Transition transition;
    private String effect;
    private String filter;
    private Double opacity;
    private Transform transform;
    
    // Constructors, getters, setters...
}

// Asset interface with implementations for each type
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = VideoAsset.class, name = "video"),
    @JsonSubTypes.Type(value = ImageAsset.class, name = "image"),
    @JsonSubTypes.Type(value = AudioAsset.class, name = "audio"),
    @JsonSubTypes.Type(value = TitleAsset.class, name = "title"),
    @JsonSubTypes.Type(value = HtmlAsset.class, name = "html"),
    @JsonSubTypes.Type(value = LumaAsset.class, name = "luma")
})
public abstract class Asset {
    private String type;
    
    // Common asset properties...
}

public class VideoAsset extends Asset {
    private String src;
    private Double trim;
    private Double volume;
    private String volumeEffect;
    private Double speed;
    private Boolean transcode;
    private Crop crop;
    private ChromaKey chromaKey;
    
    public VideoAsset() {
        super.type = "video";
    }
    
    // Getters, setters...
}
```

### Spring Boot Service Implementation

```java
@Service
public class ShotStackService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    
    public ShotStackService(@Value("${shotstack.api-key}") String apiKey,
                           @Value("${shotstack.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Async
    public CompletableFuture<RenderResult> renderVideo(Edit edit) {
        try {
            // Submit render
            RenderResponse response = restClient.post()
                .uri("/render")
                .body(edit)
                .retrieve()
                .body(RenderResponse.class);
            
            // Poll for completion
            String renderId = response.getId();
            RenderStatus status = pollUntilComplete(renderId);
            
            return CompletableFuture.completedFuture(
                new RenderResult(renderId, status.getUrl()));
                
        } catch (Exception e) {
            throw new ShotStackException("Render failed", e);
        }
    }
    
    private RenderStatus pollUntilComplete(String renderId) {
        int maxAttempts = 60;
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            RenderStatus status = restClient.get()
                .uri("/render/{id}", renderId)
                .retrieve()
                .body(RenderStatus.class);
            
            if ("done".equals(status.getStatus())) {
                return status;
            } else if ("failed".equals(status.getStatus())) {
                throw new ShotStackException("Render failed: " + status.getError());
            }
            
            try {
                Thread.sleep(5000); // Wait 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ShotStackException("Polling interrupted", e);
            }
            attempt++;
        }
        
        throw new ShotStackException("Render timeout after " + maxAttempts + " attempts");
    }
}
```

### Example: Creating a Simple Video

```java
public class VideoCreationExample {
    
    public Edit createSimpleVideo() {
        // Create video asset
        VideoAsset videoAsset = new VideoAsset();
        videoAsset.setSrc("https://example.com/video.mp4");
        videoAsset.setTrim(5.0);
        videoAsset.setVolume(0.5);
        
        // Create clip
        Clip videoClip = new Clip();
        videoClip.setAsset(videoAsset);
        videoClip.setStart(0);
        videoClip.setLength(10);
        videoClip.setFit("cover");
        
        // Create title
        TitleAsset titleAsset = new TitleAsset();
        titleAsset.setText("Welcome Video");
        titleAsset.setStyle("minimal");
        titleAsset.setColor("#ffffff");
        titleAsset.setSize("large");
        
        Clip titleClip = new Clip();
        titleClip.setAsset(titleAsset);
        titleClip.setStart(0);
        titleClip.setLength(5);
        titleClip.setTransition(new Transition("fadeIn", "fadeOut"));
        
        // Create tracks
        Track titleTrack = new Track();
        titleTrack.setClips(Arrays.asList(titleClip));
        
        Track videoTrack = new Track();
        videoTrack.setClips(Arrays.asList(videoClip));
        
        // Create timeline
        Timeline timeline = new Timeline();
        timeline.setTracks(Arrays.asList(titleTrack, videoTrack));
        timeline.setBackground("#000000");
        
        // Create output
        Output output = new Output();
        output.setFormat("mp4");
        output.setResolution("hd");
        output.setFps(30);
        
        // Build edit
        return new Edit.Builder()
            .timeline(timeline)
            .output(output)
            .build();
    }
}
```

## Constraints and Limitations

### Rate Limits
- **Production**: 10 requests per second
- **Sandbox**: 1 request per second, 5,000 requests/month
- **Rendering Speed**: ~20 seconds per minute of video

### File Size Limits
- **Source Footage**: 10GB total limit
- **Combined Video Assets**: 512MB maximum
- **Maximum Render Duration**: 3 hours
- **Individual Asset**: Must be publicly accessible

### Processing Constraints
- **Sandbox**: Maximum 10-minute renders (watermarked)
- **Production**: Up to 3-hour renders
- **Concurrent Processing**: Supported
- **Variable Frame Rate**: Not recommended (causes glitches)

### Technical Requirements
- **Asset URLs**: Must be HTTPS and publicly accessible
- **Webhook Response**: Must respond within 10 seconds
- **Webhook Retries**: Up to 10 attempts with exponential backoff
- **Audio Format**: MP3 only for soundtracks
- **Font Formats**: TTF, OTF only

### Credit System
- **Video**: 1 credit per minute of output
- **Images**: 0.1 credits per image
- **Text-to-Speech**: 0.1 credits per 100 characters
- **Storage**: 1 credit per 10GB per month
- **Bandwidth**: 1 credit per 10GB

## Best Practices

### Performance Optimization
1. **Use Webhooks**: Prefer webhooks over polling for status updates
2. **Asset Optimization**: Pre-optimize assets before upload
3. **Template Reuse**: Use templates for repeated patterns
4. **Batch Operations**: Group operations when possible
5. **Caching**: Enable caching for frequently used assets

### Error Handling
```java
public class ShotStackErrorHandler {
    
    public void handleApiError(int statusCode, String response) {
        switch (statusCode) {
            case 400:
                throw new ValidationException("Invalid request: " + response);
            case 401:
                throw new AuthenticationException("Invalid API key");
            case 404:
                throw new NotFoundException("Resource not found");
            case 429:
                throw new RateLimitException("Rate limit exceeded");
            case 500:
                throw new ServerException("Server error: " + response);
            default:
                throw new ShotStackException("Unknown error: " + response);
        }
    }
}
```

### JSON Validation
```java
public class EditValidator {
    
    public void validate(Edit edit) {
        Objects.requireNonNull(edit.getTimeline(), "Timeline required");
        Objects.requireNonNull(edit.getOutput(), "Output required");
        
        if (edit.getTimeline().getTracks().isEmpty()) {
            throw new ValidationException("At least one track required");
        }
        
        for (Track track : edit.getTimeline().getTracks()) {
            if (track.getClips().isEmpty()) {
                throw new ValidationException("Track must contain clips");
            }
            validateClips(track.getClips());
        }
    }
    
    private void validateClips(List<Clip> clips) {
        for (Clip clip : clips) {
            Objects.requireNonNull(clip.getAsset(), "Asset required");
            Objects.requireNonNull(clip.getStart(), "Start time required");
            Objects.requireNonNull(clip.getLength(), "Length required");
            
            // Additional validation...
        }
    }
}
```

### Webhook Implementation
```java
@RestController
@RequestMapping("/webhooks")
public class ShotStackWebhookController {
    
    @PostMapping("/shotstack")
    public ResponseEntity<Void> handleWebhook(@RequestBody WebhookPayload payload) {
        // Process webhook within 10 seconds
        CompletableFuture.runAsync(() -> processWebhook(payload));
        
        // Immediate response
        return ResponseEntity.ok().build();
    }
    
    private void processWebhook(WebhookPayload payload) {
        if ("done".equals(payload.getStatus())) {
            // Handle successful render
            saveVideoUrl(payload.getId(), payload.getUrl());
        } else if ("failed".equals(payload.getStatus())) {
            // Handle failed render
            logRenderFailure(payload.getId(), payload.getError());
        }
    }
}
```

### Configuration Management
```yaml
# application.yml
shotstack:
  api-key: ${SHOTSTACK_API_KEY}
  base-url: ${SHOTSTACK_BASE_URL:https://api.shotstack.io/edit/stage}
  staging: ${SHOTSTACK_STAGING:true}
  connect-timeout: 10s
  read-timeout: 30s
  max-retries: 3
  poll-interval: 5s
  max-poll-attempts: 60
```

## Summary

This comprehensive reference provides all necessary information for implementing ShotStack API integration in Java applications. The API offers powerful video editing capabilities through a RESTful interface with JSON-based edit specifications. Key considerations include proper error handling, rate limit management, and efficient asset processing. The template system enables scalable content generation, while webhook support provides efficient render status updates.

For production use, ensure proper authentication, implement robust error handling, optimize asset formats, and monitor credit usage. The platform's limitations are designed to ensure system stability while providing flexibility for various use cases from simple video generation to complex, multi-track compositions with effects and transitions.