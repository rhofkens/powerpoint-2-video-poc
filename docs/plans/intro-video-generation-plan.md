# Intro Video Generation Architecture Plan

## Executive Summary

This plan outlines the implementation of an intro video generation feature for the PowerPoint to Video POC application using Google's Veo model. The feature will generate abstract intro videos (5-10 seconds) that express the general intention of the presentation, incorporating dominant colors from the first slide and abstract background music. The implementation follows existing architectural patterns, particularly mirroring the avatar video generation architecture, while introducing new capabilities for color analysis and Veo API integration.

## Current State Analysis

### Existing Architecture Overview
- **Avatar Video Generation**: Complete implementation using HeyGen provider with async processing, status monitoring, and R2 storage
- **AI Analysis Infrastructure**: DeckAnalysisService provides presentation-level analysis including story arc, intent, themes, audience, and tone
- **R2 Storage Integration**: Established patterns for asset storage, presigned URLs, and metadata management
- **Frontend Patterns**: Modal-based video generation with progress tracking and polling mechanisms
- **Database Schema**: Existing video metadata tracking structure (avatar_videos table)

### Identified Limitations
- No color extraction capability from slide images
- No integration with Google's Veo API
- No intro video specific metadata storage
- Limited prompt engineering for video generation

### Dependencies and Constraints
- Google Veo API requires authentication via Gemini API key
- Video generation takes 11 seconds to 6 minutes
- Generated videos stored for only 2 days on Google's servers
- Videos are watermarked with SynthID
- Regional restrictions may apply

## Proposed Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend (React/TypeScript)              │
├─────────────────────────────────────────────────────────────┤
│  AIAnalysisPanel  │  IntroVideoModal  │  VideoProgressView  │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    REST API Layer                            │
├─────────────────────────────────────────────────────────────┤
│              IntroVideoController                            │
└─────────────────────────────────────────────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
┌─────────────────────────┐    ┌─────────────────────────────┐
│  IntroVideoService      │    │  VeoVideoMonitorService     │
├─────────────────────────┤    ├─────────────────────────────┤
│ - Generate intro video  │    │ - Poll generation status    │
│ - Extract colors        │    │ - Update video metadata     │
│ - Build Veo prompt      │    │ - Trigger R2 upload         │
└─────────────────────────┘    └─────────────────────────────┘
            │                               │
            ▼                               ▼
┌─────────────────────────┐    ┌─────────────────────────────┐
│  VeoApiService          │    │  ColorExtractionService     │
├─────────────────────────┤    ├─────────────────────────────┤
│ - Call Veo API          │    │ - Extract dominant colors   │
│ - Handle auth           │    │ - Create color palette      │
│ - Parse responses       │    │ - Generate color scheme     │
└─────────────────────────┘    └─────────────────────────────┘
            │                               │
            ▼                               ▼
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
├─────────────────────────────────────────────────────────────┤
│  IntroVideoRepository  │  DeckAnalysisRepo  │  SlideRepo    │
└─────────────────────────────────────────────────────────────┘
```

### Detailed Technical Specifications

#### Backend Changes

##### 1. New Entity: IntroVideo
```java
@Entity
@Table(name = "intro_videos")
public class IntroVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;
    
    @Column(name = "veo_generation_id")
    private String veoGenerationId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VideoGenerationStatus status;
    
    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;
    
    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;
    
    @Column(name = "color_palette", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String colorPalette;
    
    @Column(name = "veo_model_version")
    private String veoModelVersion;
    
    @Column(name = "aspect_ratio")
    private String aspectRatio;
    
    @Column(name = "resolution")
    private String resolution;
    
    @Column(name = "duration_seconds")
    private Double durationSeconds;
    
    @Column(name = "google_video_url", columnDefinition = "TEXT")
    private String googleVideoUrl;
    
    @Column(name = "published_url", columnDefinition = "TEXT")
    private String publishedUrl;
    
    @ManyToOne
    @JoinColumn(name = "r2_asset_id")
    private AssetMetadata r2Asset;
    
    @Column(name = "generation_metadata", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String generationMetadata;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

##### 2. Service Layer Implementation

**IntroVideoService.java**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class IntroVideoService {
    private final IntroVideoRepository introVideoRepository;
    private final DeckAnalysisService deckAnalysisService;
    private final ColorExtractionService colorExtractionService;
    private final VeoApiService veoApiService;
    private final VeoPromptBuilder veoPromptBuilder;
    private final VeoVideoMonitorService monitorService;
    private final R2AssetService r2AssetService;
    private final SlideRepository slideRepository;
    
    @Transactional
    public IntroVideoResponse generateIntroVideo(IntroVideoRequest request) {
        // 1. Validate presentation exists and has deck analysis
        DeckAnalysis deckAnalysis = validateAndGetDeckAnalysis(request.getPresentationId());
        
        // 2. Get first slide and extract colors
        Slide firstSlide = getFirstSlide(request.getPresentationId());
        ColorPalette colorPalette = colorExtractionService.extractColors(firstSlide);
        
        // 3. Build Veo prompt using deck analysis and colors
        VeoPrompt veoPrompt = veoPromptBuilder.buildPrompt(deckAnalysis, colorPalette);
        
        // 4. Create IntroVideo entity
        IntroVideo introVideo = createIntroVideoEntity(request, veoPrompt, colorPalette);
        
        // 5. Call Veo API to start generation
        VeoGenerationResponse veoResponse = veoApiService.generateVideo(veoPrompt);
        
        // 6. Update entity with Veo response
        introVideo.setVeoGenerationId(veoResponse.getGenerationId());
        introVideo.setStatus(VideoGenerationStatus.PROCESSING);
        introVideo = introVideoRepository.save(introVideo);
        
        // 7. Start async monitoring
        monitorService.monitorVideoGeneration(introVideo.getId(), veoResponse.getGenerationId());
        
        return convertToResponse(introVideo);
    }
}
```

**ColorExtractionService.java**
```java
@Service
@Slf4j
public class ColorExtractionService {
    
    public ColorPalette extractColors(Slide slide) {
        // Use Java Advanced Imaging API or Apache Commons Imaging
        BufferedImage image = loadSlideImage(slide);
        
        // Extract dominant colors using k-means clustering
        List<Color> dominantColors = performKMeansClustering(image, 5);
        
        // Generate color scheme (primary, secondary, accent)
        ColorScheme colorScheme = generateColorScheme(dominantColors);
        
        // Calculate color statistics
        ColorStatistics stats = calculateColorStatistics(image);
        
        return ColorPalette.builder()
            .dominantColors(dominantColors)
            .colorScheme(colorScheme)
            .statistics(stats)
            .brightness(calculateAverageBrightness(image))
            .saturation(calculateAverageSaturation(image))
            .build();
    }
    
    private List<Color> performKMeansClustering(BufferedImage image, int k) {
        // Implementation using efficient k-means algorithm
        // Sample pixels for performance
        List<int[]> pixels = samplePixels(image, 10000);
        
        // Initialize centroids
        List<int[]> centroids = initializeCentroids(pixels, k);
        
        // Iterate until convergence
        for (int i = 0; i < 50; i++) {
            Map<Integer, List<int[]>> clusters = assignToClusters(pixels, centroids);
            List<int[]> newCentroids = recalculateCentroids(clusters);
            
            if (hasConverged(centroids, newCentroids)) {
                break;
            }
            centroids = newCentroids;
        }
        
        return centroids.stream()
            .map(rgb -> new Color(rgb[0], rgb[1], rgb[2]))
            .collect(Collectors.toList());
    }
}
```

**VeoApiService.java**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class VeoApiService {
    
    @Value("${google.gemini.api.key}")
    private String apiKey;
    
    @Value("${google.veo.model:veo-3.0-generate-001}")
    private String veoModel;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public VeoGenerationResponse generateVideo(VeoPrompt prompt) {
        String url = buildApiUrl();
        
        Map<String, Object> request = Map.of(
            "prompt", prompt.getPromptText(),
            "negativePrompt", prompt.getNegativePrompt(),
            "aspectRatio", prompt.getAspectRatio(),
            "resolution", prompt.getResolution(),
            "personGeneration", false
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class
            );
            
            return parseVeoResponse(response.getBody());
        } catch (Exception e) {
            log.error("Failed to call Veo API", e);
            throw new ProcessingException("Veo API call failed: " + e.getMessage());
        }
    }
    
    public VeoVideoStatus getVideoStatus(String generationId) {
        String url = buildStatusUrl(generationId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, Map.class
        );
        
        return parseStatusResponse(response.getBody());
    }
}
```

**VeoPromptBuilder.java**
```java
@Service
@Slf4j
public class VeoPromptBuilder {
    
    // Concise prompt template following Google Veo best practices (under 1024 tokens)
    private static final String PROMPT_TEMPLATE = """
        Subject: Abstract geometric shapes and flowing particles representing {topic}, morphing visualizations, light trails
        
        Action: Shapes transforming, particles flowing in patterns, smooth transitions, pulsating rhythms expressing {intent}
        
        Style: Modern motion graphics, abstract corporate style, minimalist {tone} aesthetic
        
        Camera: Wide aerial establishing shot, smooth dolly in, orbital motion, depth transitions
        
        Composition: Wide to medium shot, rule of thirds, layered depth, cinematic framing
        
        Focus: Shallow depth of field, soft bokeh, selective focus shifts
        
        Ambiance: {colors} color palette, {tone} lighting for {audience}, volumetric effects
        
        Visual themes: Abstract {themes} representations, flowing energy patterns
        
        Audio: Ambient {audioMood} electronic soundtrack, no dialogue
        
        8 seconds, HD 1920x1080, 16:9 widescreen, professional quality
        """;
    
    // Concise negative prompt following Google's guidelines
    private static final String NEGATIVE_PROMPT_TEMPLATE = """
        Negative prompt: text, words, logos, human faces, dialogue, literal office scenes, stock footage, low quality, pixelation, static images, cliché business imagery
        """;
    
    public VeoPrompt buildPrompt(DeckAnalysis analysis, ColorPalette colorPalette) {
        // Extract key information
        String topic = extractTopic(analysis);
        String intent = analysis.getCommunicationIntent();
        String tone = analysis.getTone();
        String audience = analysis.getTargetAudience();
        List<String> themes = parseThemes(analysis.getKeyThemes());
        
        // Format colors
        String colorDescription = formatColorDescription(colorPalette);
        
        // Determine audio mood based on tone
        String audioMood = determineAudioMood(tone);
        
        // Build prompt
        String promptText = PROMPT_TEMPLATE
            .replace("{topic}", topic)
            .replace("{intent}", intent)
            .replace("{colors}", colorDescription)
            .replace("{tone}", tone)
            .replace("{audience}", audience)
            .replace("{themes}", formatThemes(themes))
            .replace("{audioMood}", audioMood);
        
        return VeoPrompt.builder()
            .promptText(promptText.trim())
            .negativePrompt(NEGATIVE_PROMPT_TEMPLATE.trim())
            .aspectRatio("16:9")
            .resolution("1080p")
            .build();
    }
    
    private String formatColorDescription(ColorPalette palette) {
        List<String> colorDescriptions = palette.getDominantColors().stream()
            .map(this::describeColor)
            .limit(3)
            .collect(Collectors.toList());
        
        return String.join(", ", colorDescriptions) + 
               ". Overall brightness: " + describeBrightness(palette.getBrightness()) +
               ", saturation: " + describeSaturation(palette.getSaturation());
    }
    
    private String describeColor(Color color) {
        // Convert RGB to descriptive names
        // e.g., "deep blue", "warm orange", "soft green"
        return ColorNameResolver.getDescriptiveName(color);
    }
}
```

##### 3. Monitoring Service

**VeoVideoMonitorService.java**
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class VeoVideoMonitorService {
    
    private final IntroVideoRepository introVideoRepository;
    private final VeoApiService veoApiService;
    private final R2AssetService r2AssetService;
    private final TaskScheduler taskScheduler;
    
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAX_WAIT_TIME = Duration.ofMinutes(10);
    
    @Async
    public void monitorVideoGeneration(UUID introVideoId, String veoGenerationId) {
        log.info("Starting monitoring for intro video: {}, Veo ID: {}", introVideoId, veoGenerationId);
        
        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(
            () -> checkAndUpdateStatus(introVideoId, veoGenerationId),
            Instant.now().plus(INITIAL_DELAY),
            POLL_INTERVAL
        );
        
        // Schedule timeout cancellation
        taskScheduler.schedule(
            () -> handleTimeout(introVideoId, future),
            Instant.now().plus(MAX_WAIT_TIME)
        );
    }
    
    private void checkAndUpdateStatus(UUID introVideoId, String veoGenerationId) {
        try {
            IntroVideo introVideo = introVideoRepository.findById(introVideoId)
                .orElseThrow(() -> new ResourceNotFoundException("Intro video not found"));
            
            // Check if already completed or failed
            if (isTerminalState(introVideo.getStatus())) {
                return;
            }
            
            // Get status from Veo API
            VeoVideoStatus status = veoApiService.getVideoStatus(veoGenerationId);
            
            // Update entity
            introVideo.setStatus(mapVeoStatus(status.getState()));
            
            if (status.getState() == VeoState.COMPLETED) {
                handleCompletion(introVideo, status);
            } else if (status.getState() == VeoState.FAILED) {
                handleFailure(introVideo, status);
            }
            
            introVideoRepository.save(introVideo);
            
        } catch (Exception e) {
            log.error("Error checking video status for {}", introVideoId, e);
        }
    }
    
    private void handleCompletion(IntroVideo introVideo, VeoVideoStatus status) {
        introVideo.setGoogleVideoUrl(status.getVideoUrl());
        introVideo.setCompletedAt(LocalDateTime.now());
        introVideo.setDurationSeconds(8.0); // Veo generates 8-second videos
        
        // Download and publish to R2
        publishToR2(introVideo);
    }
    
    @Async
    private void publishToR2(IntroVideo introVideo) {
        try {
            // Download video from Google
            byte[] videoData = downloadVideo(introVideo.getGoogleVideoUrl());
            
            // Save to temporary file
            Path tempFile = Files.createTempFile("intro_video_", ".mp4");
            Files.write(tempFile, videoData);
            
            // Publish to R2
            AssetDto asset = r2AssetService.publishAsset(
                introVideo.getPresentation().getId(),
                null, // No slide ID for intro videos
                AssetType.INTRO_VIDEO,
                tempFile
            );
            
            // Update intro video with R2 URL
            introVideo.setPublishedUrl(asset.getDownloadUrl());
            introVideo.setR2Asset(assetMetadataService.getAsset(asset.getId()).orElse(null));
            introVideoRepository.save(introVideo);
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
        } catch (Exception e) {
            log.error("Failed to publish intro video to R2", e);
            introVideo.setStatus(VideoGenerationStatus.FAILED);
            introVideo.setErrorMessage("Failed to publish video: " + e.getMessage());
            introVideoRepository.save(introVideo);
        }
    }
}
```

#### Frontend Changes

##### 1. IntroVideoModal Component

```typescript
// IntroVideoModal.tsx
import { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { useToast } from '@/hooks/use-toast';
import { apiService } from '@/services/api';
import { IntroVideo, IntroVideoRequest, VideoGenerationStatus } from '@/types/intro-video';
import { Video, Loader2, Sparkles, AlertCircle, CheckCircle, Palette } from 'lucide-react';

interface IntroVideoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  deckAnalysis: DeckAnalysis;
  onVideoGenerated?: (video: IntroVideo) => void;
}

export function IntroVideoModal({
  open,
  onOpenChange,
  presentationId,
  deckAnalysis,
  onVideoGenerated
}: IntroVideoModalProps) {
  const [isGenerating, setIsGenerating] = useState(false);
  const [currentVideo, setCurrentVideo] = useState<IntroVideo | null>(null);
  const [status, setStatus] = useState<VideoGenerationStatus | null>(null);
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [extractedColors, setExtractedColors] = useState<string[]>([]);
  const { toast } = useToast();
  
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (open) {
      loadExistingIntroVideo();
      extractColors();
    }
    
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
  }, [open, presentationId]);

  const loadExistingIntroVideo = async () => {
    try {
      const video = await apiService.getIntroVideo(presentationId);
      if (video) {
        setCurrentVideo(video);
        setStatus(video.status);
        
        if (video.status === 'PROCESSING' || video.status === 'PENDING') {
          setIsGenerating(true);
          startPolling(video.id);
        }
      }
    } catch (error) {
      console.log('No existing intro video found');
    }
  };

  const extractColors = async () => {
    try {
      const colors = await apiService.extractSlideColors(presentationId);
      setExtractedColors(colors.dominantColors);
    } catch (error) {
      console.error('Failed to extract colors:', error);
    }
  };

  const handleGenerate = async () => {
    setIsGenerating(true);
    setErrorMessage(null);
    setProgress(0);
    
    try {
      const request: IntroVideoRequest = {
        presentationId,
        regenerate: currentVideo !== null
      };
      
      const response = await apiService.generateIntroVideo(request);
      setCurrentVideo(response);
      setStatus(response.status);
      
      // Start polling for status updates
      startPolling(response.id);
      
      // Animate progress
      animateProgress();
      
      toast({
        title: "Video Generation Started",
        description: "Your intro video is being generated. This may take a few minutes."
      });
      
    } catch (error: any) {
      setIsGenerating(false);
      setErrorMessage(error.message || 'Failed to generate intro video');
      toast({
        title: "Generation Failed",
        description: error.message || 'Failed to start video generation',
        variant: "destructive"
      });
    }
  };

  const startPolling = (videoId: string) => {
    pollingIntervalRef.current = setInterval(async () => {
      try {
        const status = await apiService.getIntroVideoStatus(videoId);
        setStatus(status.status);
        
        if (status.status === 'COMPLETED') {
          setIsGenerating(false);
          setProgress(100);
          stopPolling();
          
          // Load the completed video
          const video = await apiService.getIntroVideo(presentationId);
          setCurrentVideo(video);
          
          toast({
            title: "Video Ready!",
            description: "Your intro video has been generated successfully."
          });
          
          if (onVideoGenerated) {
            onVideoGenerated(video);
          }
          
        } else if (status.status === 'FAILED') {
          setIsGenerating(false);
          setErrorMessage(status.errorMessage || 'Video generation failed');
          stopPolling();
          
          toast({
            title: "Generation Failed",
            description: status.errorMessage || 'Video generation failed',
            variant: "destructive"
          });
        }
      } catch (error) {
        console.error('Polling error:', error);
      }
    }, 5000);
  };

  const stopPolling = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  };

  const animateProgress = () => {
    let currentProgress = 0;
    const interval = setInterval(() => {
      currentProgress += Math.random() * 2;
      if (currentProgress >= 90) {
        clearInterval(interval);
        setProgress(90);
      } else {
        setProgress(currentProgress);
      }
    }, 1000);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="w-5 h-5" />
            Generate Intro Video
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Deck Analysis Summary */}
          <Card>
            <CardHeader>
              <CardTitle className="text-sm">Presentation Analysis</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              <div className="grid grid-cols-2 gap-2 text-sm">
                <div>
                  <span className="text-muted-foreground">Tone:</span>
                  <Badge variant="secondary" className="ml-2">{deckAnalysis.tone}</Badge>
                </div>
                <div>
                  <span className="text-muted-foreground">Audience:</span>
                  <Badge variant="secondary" className="ml-2">{deckAnalysis.targetAudience}</Badge>
                </div>
              </div>
              <div className="text-sm">
                <span className="text-muted-foreground">Intent:</span>
                <p className="mt-1">{deckAnalysis.communicationIntent}</p>
              </div>
            </CardContent>
          </Card>

          {/* Color Palette */}
          {extractedColors.length > 0 && (
            <div className="space-y-2">
              <Label className="flex items-center gap-2">
                <Palette className="w-4 h-4" />
                Extracted Color Palette
              </Label>
              <div className="flex gap-2">
                {extractedColors.map((color, index) => (
                  <div
                    key={index}
                    className="w-12 h-12 rounded border-2 border-gray-200"
                    style={{ backgroundColor: color }}
                    title={color}
                  />
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                These colors will be used to maintain visual consistency with your presentation
              </p>
            </div>
          )}

          {/* Generation Status */}
          {isGenerating && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Generating Video...</span>
                <Badge variant="secondary">
                  {status === 'PROCESSING' ? 'Processing' : 'Pending'}
                </Badge>
              </div>
              <Progress value={progress} className="h-2" />
              <p className="text-xs text-muted-foreground">
                This may take 1-6 minutes. You can close this dialog and check back later.
              </p>
            </div>
          )}

          {/* Error Message */}
          {errorMessage && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          )}

          {/* Video Preview */}
          {currentVideo?.publishedUrl && !isGenerating && (
            <div className="space-y-2">
              <Label>Generated Intro Video</Label>
              <video
                src={currentVideo.publishedUrl}
                controls
                className="w-full rounded-lg border"
                style={{ maxHeight: '300px' }}
              />
              <div className="flex items-center justify-between text-sm text-muted-foreground">
                <span>Duration: 8 seconds</span>
                <span>Resolution: {currentVideo.resolution}</span>
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isGenerating}
            >
              Close
            </Button>
            <Button
              onClick={handleGenerate}
              disabled={isGenerating || !deckAnalysis}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Generating...
                </>
              ) : currentVideo ? (
                'Regenerate Video'
              ) : (
                'Generate Intro Video'
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

##### 2. AI Analysis Panel Integration

```typescript
// Add to AIAnalysisPanel.tsx
const [introVideoModalOpen, setIntroVideoModalOpen] = useState(false);

// In the JSX, add button to AI Analysis tab
<TabsContent value="analysis" className="space-y-4">
  {/* Existing deck analysis content */}
  
  {deckAnalysis && (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg flex items-center justify-between">
          <span>Intro Video Generation</span>
          <Button
            onClick={() => setIntroVideoModalOpen(true)}
            size="sm"
            variant="default"
          >
            <Sparkles className="mr-2 h-4 w-4" />
            Generate Intro Video
          </Button>
        </CardTitle>
        <CardDescription>
          Create an AI-generated intro video based on your presentation's theme and colors
        </CardDescription>
      </CardHeader>
    </Card>
  )}
  
  {/* Intro Video Modal */}
  <IntroVideoModal
    open={introVideoModalOpen}
    onOpenChange={setIntroVideoModalOpen}
    presentationId={presentationId}
    deckAnalysis={deckAnalysis}
  />
</TabsContent>
```

#### Database Design

##### Migration Script: V20__Create_intro_videos_table.sql

```sql
-- Create intro_videos table for tracking Veo-generated intro videos
CREATE TABLE intro_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL,
    veo_generation_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    prompt_text TEXT,
    negative_prompt TEXT,
    color_palette JSONB,
    veo_model_version VARCHAR(50),
    aspect_ratio VARCHAR(20),
    resolution VARCHAR(20),
    duration_seconds DOUBLE PRECISION,
    google_video_url TEXT,
    published_url TEXT,
    r2_asset_id UUID,
    generation_metadata JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_intro_video_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_intro_video_asset 
        FOREIGN KEY (r2_asset_id) 
        REFERENCES asset_metadata(id) 
        ON DELETE SET NULL
);

-- Create indexes
CREATE INDEX idx_intro_videos_presentation ON intro_videos(presentation_id);
CREATE INDEX idx_intro_videos_status ON intro_videos(status);
CREATE INDEX idx_intro_videos_created ON intro_videos(created_at);

-- Add comment
COMMENT ON TABLE intro_videos IS 'Tracks AI-generated intro videos using Google Veo API';
```

## Implementation Roadmap

### Phase 1: Foundation (3-4 days)

**Tasks and Deliverables:**
1. Create database migration for intro_videos table
2. Implement IntroVideo entity and repository
3. Set up Google Veo API configuration
4. Implement VeoApiService with Spring Boot REST client
5. Create basic DTOs and request/response objects

**Acceptance Criteria:**
- Database schema deployed successfully
- Veo API connection tested with sample request
- Basic entity CRUD operations working

### Phase 2: Core Implementation (5-6 days)

**Tasks and Deliverables:**
1. Implement ColorExtractionService with k-means clustering
2. Build VeoPromptBuilder with template system
3. Create IntroVideoService with generation logic
4. Implement VeoVideoMonitorService with async polling
5. Add R2 storage integration for generated videos
6. Create REST API endpoints in IntroVideoController

**Acceptance Criteria:**
- Color extraction produces accurate dominant colors
- Prompts generated include all required elements
- Video generation request successfully submitted to Veo
- Monitoring service properly tracks generation status
- Videos successfully stored in R2 after generation

### Phase 3: Frontend Integration (3-4 days)

**Tasks and Deliverables:**
1. Create IntroVideoModal component
2. Add intro video button to AI Analysis panel
3. Implement progress tracking and status updates
4. Integrate existing video player component
5. Create color palette display component
6. Add error handling and retry logic

**Acceptance Criteria:**
- Modal displays correctly with all UI elements
- Progress updates shown during generation
- Generated video plays correctly in preview
- Color palette accurately displayed
- Error states handled gracefully

### Phase 4: Testing & Polish (2-3 days)

**Tasks and Deliverables:**
1. Manual testing with various presentation types
2. Verify error handling and retry logic
3. UI/UX polish and responsiveness
4. Performance optimization for color extraction
5. Add monitoring and logging
6. Documentation updates

**Acceptance Criteria:**
- All manual test scenarios pass
- Error handling works correctly
- No performance degradation
- Complete API documentation
- User-facing documentation updated

## Risk Assessment

### Technical Risks

1. **Veo API Rate Limits**
   - Risk: API throttling during high usage
   - Mitigation: Implement request queuing and backoff strategies
   - Implement caching for regeneration requests

2. **Long Generation Times**
   - Risk: User frustration with 1-6 minute wait times
   - Mitigation: Clear progress indicators, background processing, email notifications option

3. **Color Extraction Performance**
   - Risk: Processing large images may be slow
   - Mitigation: Downsample images before processing, implement caching, use parallel processing

4. **Video Storage Costs**
   - Risk: R2 storage costs may increase significantly
   - Mitigation: Implement retention policies, compress videos, provide deletion options

### Performance Implications

- Color extraction adds ~2-3 seconds to generation request
- Veo API calls are async, no blocking on main thread
- Polling mechanism uses exponential backoff to reduce load
- R2 upload happens asynchronously after generation

### Security Considerations

1. **API Key Management**
   - Store Google API key in secure vault
   - Implement key rotation mechanism
   - Add request signing for additional security

2. **Video Content Validation**
   - Implement content scanning before R2 storage
   - Add watermark detection validation
   - Ensure GDPR compliance for generated content

3. **Rate Limiting**
   - Implement per-user generation limits
   - Add cooldown periods between requests
   - Monitor for abuse patterns

## Alternative Approaches Considered

1. **Direct Veo Integration vs. Provider Pattern**
   - Rejected: Provider pattern adds unnecessary complexity for single provider
   - Chosen: Direct integration for simplicity and faster implementation

2. **Real-time vs. Batch Color Extraction**
   - Rejected: Batch processing would delay user experience
   - Chosen: Real-time extraction with caching for better UX

3. **Synchronous vs. Asynchronous Generation**
   - Rejected: Synchronous would block user for minutes
   - Chosen: Asynchronous with polling matches existing patterns

4. **Fixed vs. Dynamic Prompts**
   - Rejected: Fixed prompts limit creativity
   - Chosen: Dynamic prompt building for better personalization

## Success Metrics

### Performance Benchmarks
- Color extraction completes in < 3 seconds
- Video generation initiated in < 5 seconds
- Status polling interval: 5-10 seconds
- R2 upload completes in < 30 seconds

### Quality Indicators
- 90% of generated videos match presentation theme
- Color accuracy > 85% compared to source slide
- User satisfaction rating > 4/5
- Generation success rate > 95%

### Business Metrics
- Feature adoption rate > 60% of active users
- Average regeneration rate < 20%
- Support tickets < 5% of generations
- Cost per generation < $0.10

## Dependencies and Prerequisites

### Required Tools and Libraries

1. **Java Dependencies:**
   - Apache Commons Imaging 1.0-alpha3 (for color extraction)
   - Spring Boot REST Client (already included)
   - Spring Retry (already included)

2. **Frontend Dependencies:**
   - color-thief-react 2.1.0 (for color palette display)
   - Existing video player component (already in codebase)

### Team Skills Needed
- Experience with REST APIs and async processing
- Knowledge of color theory and image processing
- Understanding of video formats
- Spring Boot development experience

### Infrastructure Requirements
1. **API Access:**
   - Google Gemini API key with Veo access
   - Sufficient API quota allocation

2. **Storage:**
   - Additional R2 bucket space (~100GB)
   - CDN configuration for video delivery

3. **Monitoring:**
   - APM tools for async job tracking
   - Log aggregation for debugging
   - Metrics dashboard for usage tracking

## Monitoring and Observability

### Key Metrics to Track
1. **Generation Metrics:**
   - Total generations per day
   - Average generation time
   - Success/failure rates
   - Retry attempts

2. **Performance Metrics:**
   - Color extraction duration
   - API response times
   - R2 upload speeds
   - Frontend loading times

3. **Business Metrics:**
   - Feature usage by user segment
   - Regeneration patterns
   - User engagement with generated videos

### Logging Strategy
```java
// Structured logging for traceability
log.info("Intro video generation started", Map.of(
    "presentationId", presentationId,
    "userId", userId,
    "colorCount", extractedColors.size(),
    "promptLength", prompt.length()
));
```

### Alert Configuration
1. Generation failure rate > 10%
2. Average generation time > 7 minutes
3. API quota usage > 80%
4. R2 storage usage > 90%

## Rollback Strategy

### Phase-based Rollback
1. **Feature Flag Control:**
   ```properties
   feature.intro-video.enabled=true
   feature.intro-video.color-extraction=true
   feature.intro-video.veo-api=true
   ```

2. **Database Rollback:**
   ```sql
   -- Rollback migration
   DROP TABLE IF EXISTS intro_videos CASCADE;
   ```

3. **Service Degradation:**
   - Disable intro video button in UI
   - Return cached videos if available
   - Fallback to static intro templates

### Data Recovery
1. Maintain video backup in Google for 48 hours
2. Implement soft delete for 30 days
3. Transaction log for generation requests

## Conclusion

This architectural plan provides a comprehensive blueprint for implementing intro video generation using Google's Veo model. The design leverages existing patterns from the avatar video system while introducing new capabilities for color analysis and creative prompt generation. The phased implementation approach ensures manageable delivery milestones while maintaining system stability and performance.

The solution balances technical sophistication with practical constraints, providing a production-ready feature suitable for POC demonstration while laying the groundwork for future enhancements such as custom duration options, style variations, and template-based generation.