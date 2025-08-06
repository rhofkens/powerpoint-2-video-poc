/**
 * Represents a PowerPoint presentation with metadata and processing information.
 */
export interface Presentation {
  /** Unique identifier (UUID) */
  id: string;
  /** Title of the presentation */
  title: string;
  /** Original filename of the uploaded file */
  originalFilename: string;
  /** File size in bytes */
  fileSize: number;
  /** Current processing status of the presentation */
  processingStatus: ProcessingStatus;
  /** Selected narrative style for content generation */
  narrativeStyle?: string;
  /** Deck analysis results */
  deckAnalysis?: DeckAnalysis;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
  /** Array of slides (optional, loaded separately) */
  slides?: Slide[];
}

/**
 * Represents an individual slide within a presentation.
 */
export interface Slide {
  /** Unique identifier (UUID) */
  id: string;
  /** UUID of the parent presentation */
  presentationId: string;
  /** Sequential slide number within presentation */
  slideNumber: number;
  /** Slide title extracted from PowerPoint */
  title?: string;
  /** Slide content extracted from PowerPoint */
  content?: string;
  /** Speaker notes extracted from PowerPoint */
  speakerNotes?: string;
  /** Path to extracted slide image */
  imagePath?: string;
  /** Path to generated audio file */
  audioPath?: string;
  /** Path to generated video file */
  videoPath?: string;
  /** AI-generated narrative text */
  generatedNarrative?: string;
  /** Current processing status of the slide */
  processingStatus: SlideProcessingStatus;
  /** AI analysis of the slide */
  slideAnalysis?: SlideAnalysis;
  /** AI-generated narrative with metadata */
  slideNarrative?: SlideNarrative;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
}

/**
 * Processing status for presentations.
 * Tracks progress through the conversion pipeline.
 */
export type ProcessingStatus = 
  | 'UPLOADED'                   // Initial state after file upload
  | 'PARSING'                    // Extracting slides from PowerPoint
  | 'RENDERING_READY'            // Slides have been rendered and ready for analysis
  | 'ANALYZING'                  // AI is analyzing the presentation content
  | 'INTENT_ANALYSIS_COMPLETE'   // Intent analysis for deck and slides is complete
  | 'NARRATIVE_COMPLETE'         // Narrative generation is complete
  | 'GENERATING_CONTENT'         // Generating narratives and speech content
  | 'COMPLETED'                  // All processing finished
  | 'FAILED';                    // Processing error occurred

/**
 * Processing status for individual slides.
 * Tracks progress through content generation and media creation.
 */
export type SlideProcessingStatus = 
  | 'EXTRACTED'         // Slide extracted from presentation
  | 'ANALYZING'         // AI analyzing slide content
  | 'SPEECH_GENERATING' // Creating audio from narrative
  | 'VIDEO_GENERATING'  // Creating video from image and audio
  | 'COMPLETED'         // All processing finished
  | 'FAILED';           // Processing error occurred

/**
 * Available narrative styles for content generation.
 * Determines the tone and approach of generated narratives.
 */
export type NarrativeStyle = 'business' | 'funny' | 'cynical';

/**
 * Response structure for presentation upload endpoint.
 * Matches the backend PresentationUploadResponseDto.
 */
export interface PresentationUploadResponse {
  /** Unique identifier (UUID) of the uploaded presentation */
  id: string;
  /** Original filename of the uploaded file */
  originalFilename: string;
  /** File size in bytes */
  fileSize: number;
  /** Current processing status */
  status: ProcessingStatus;
  /** ISO 8601 timestamp of upload */
  uploadTimestamp: string;
  /** Number of slides extracted from the presentation */
  slideCount?: number;
}

/**
 * Error response structure from the API.
 * Standard format for all API error responses.
 */
export interface ApiErrorResponse {
  /** Error message */
  message: string;
  /** HTTP status code */
  status: number;
  /** Error code or type */
  error?: string;
  /** Additional error details */
  details?: string;
  /** Timestamp of the error */
  timestamp: string;
}

/**
 * Upload progress information.
 * Used to track file upload progress and speed.
 */
export interface UploadProgress {
  /** Percentage of upload completed (0-100) */
  percentage: number;
  /** Upload speed in bytes per second */
  speed: number;
  /** Estimated time remaining in seconds */
  estimatedTime?: number;
  /** Total bytes to upload */
  total: number;
  /** Bytes uploaded so far */
  loaded: number;
}

/**
 * Parsing progress information.
 */
export interface ParsingProgress {
  /** Current parsing status */
  status: string;
  /** Number of slides found */
  slideCount: number;
  /** When parsing started */
  startedAt?: string;
  /** When parsing completed */
  completedAt?: string;
}

/**
 * Rendering progress information.
 */
export interface RenderingProgress {
  /** Current rendering status */
  status: string;
  /** Number of slides completed */
  completedSlides: number;
  /** Total number of slides */
  totalSlides: number;
  /** Renderer being used */
  renderer?: string;
  /** When rendering started */
  startedAt?: string;
  /** When rendering completed */
  completedAt?: string;
}

/**
 * Presentation status response.
 */
export interface PresentationStatus {
  /** Presentation ID */
  id: string;
  /** Parsing progress information */
  parsingProgress: ParsingProgress;
  /** Rendering progress information */
  renderingProgress: RenderingProgress;
}

/**
 * Analysis status tracking.
 */
export interface AnalysisStatusDto {
  /** Presentation ID */
  presentationId: string;
  /** Type of analysis */
  analysisType: AnalysisType;
  /** Current state */
  state: AnalysisState;
  /** Total items to process */
  totalItems: number;
  /** Completed items */
  completedItems: number;
  /** Failed items */
  failedItems: number;
  /** Start time */
  startTime: string;
  /** End time (if completed) */
  endTime?: string;
  /** Error messages */
  errors: string[];
  /** Status message */
  message: string;
}

/**
 * Analysis type enumeration.
 */
export type AnalysisType = 
  | 'DECK_ANALYSIS'
  | 'ALL_SLIDES_ANALYSIS' 
  | 'ALL_NARRATIVES_GENERATION';

/**
 * Analysis state enumeration.
 */
export type AnalysisState = 
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * Re-render request.
 */
export interface ReRenderRequest {
  /** Renderer to use */
  renderer: string;
  /** Force re-render even if already rendered */
  force: boolean;
}

/**
 * Deck analysis result from AI.
 * Contains overall presentation insights.
 */
export interface DeckAnalysis {
  /** Unique identifier (UUID) */
  id: string;
  /** Overall story arc and message */
  overallStory: string;
  /** Communication intent and goals */
  communicationIntent: string;
  /** Key themes in JSON format */
  keyThemes: string;
  /** Target audience description */
  targetAudience: string;
  /** Overall tone of presentation */
  tone: string;
  /** Analysis metadata in JSON format */
  analysisMetadata?: string;
  /** Model used for analysis */
  modelUsed: string;
  /** Prompt version used */
  promptVersion: string;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
}

/**
 * Visual concept types for slide analysis.
 */
export type VisualConceptType = 
  // Structural Concepts
  | 'TIMELINE'
  | 'PROCESS_FLOW'
  | 'HIERARCHY'
  | 'MATRIX'
  | 'CYCLE'
  // Comparison Concepts
  | 'COMPARISON_TABLE'
  | 'PROS_CONS'
  | 'BEFORE_AFTER'
  | 'VENN_DIAGRAM'
  // Data Visualization
  | 'BAR_CHART'
  | 'LINE_CHART'
  | 'PIE_CHART'
  | 'SCATTER_PLOT'
  | 'GAUGE_CHART'
  | 'HEATMAP'
  // Business Concepts
  | 'SWOT_ANALYSIS'
  | 'BUSINESS_MODEL'
  | 'ROADMAP'
  | 'KPI_DASHBOARD'
  | 'FUNNEL'
  | 'CUSTOMER_JOURNEY'
  // Informational Concepts
  | 'BULLET_LIST'
  | 'DEFINITION'
  | 'QUOTE'
  | 'STATISTICS'
  | 'ICON_GRID'
  | 'INFOGRAPHIC'
  // Relational Concepts
  | 'MIND_MAP'
  | 'NETWORK_DIAGRAM'
  | 'FLOWCHART'
  | 'DEPENDENCY_MAP'
  // Specialized Concepts
  | 'GANTT_CHART'
  | 'RISK_MATRIX'
  | 'PRICING_TABLE'
  | 'TEAM_STRUCTURE'
  | 'CALL_TO_ACTION'
  | 'AGENDA'
  | 'SUMMARY'
  | 'QUESTION_SLIDE';

/**
 * Position of visual concept on slide.
 */
export interface ConceptPosition {
  /** Vertical position */
  vertical: 'TOP' | 'CENTER' | 'BOTTOM';
  /** Horizontal position */
  horizontal: 'LEFT' | 'CENTER' | 'RIGHT';
}

/**
 * Visual concept in a slide with enhanced metadata.
 */
export interface VisualConcept {
  /** Type of visual concept */
  type: VisualConceptType;
  /** Confidence level of detection */
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  /** Position on slide */
  position: ConceptPosition;
  /** Visual prominence */
  prominence: 'PRIMARY' | 'SECONDARY' | 'SUPPORTING';
  /** Concept-specific details */
  details: Record<string, unknown>;
}

/**
 * Slide analysis result from AI.
 * Contains individual slide insights.
 */
export interface SlideAnalysis {
  /** Unique identifier (UUID) */
  id: string;
  /** General message of the slide */
  generalMessage: string;
  /** Visual concepts in JSON format */
  visualConcepts: string;
  /** Key points in JSON format */
  keyPoints: string;
  /** Data insights if any */
  dataInsights?: string;
  /** Transition context */
  transitionContext: string;
  /** Emphasis level */
  emphasisLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  /** Analysis metadata in JSON format */
  analysisMetadata?: string;
  /** Model used for analysis */
  modelUsed: string;
  /** Prompt version used */
  promptVersion: string;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
}

/**
 * Emotion indicator for narrative.
 */
export interface EmotionIndicator {
  /** Starting word index */
  startWord: number;
  /** Ending word index */
  endWord: number;
  /** Emotion type */
  emotion: string;
}

/**
 * Avatar rendering instructions.
 */
export interface AvatarInstructions {
  /** Primary emotion for the slide */
  primaryEmotion: string;
  /** Gesture intensity level */
  gestureIntensity: 'LOW' | 'MEDIUM' | 'HIGH';
  /** Facial expression description */
  facialExpression: string;
}

/**
 * Speech markers for narrative delivery.
 */
export interface SpeechMarkers {
  /** Pause locations */
  pauses: Array<{
    afterWord: number;
    duration: 'short' | 'medium' | 'long';
  }>;
  /** Words to emphasize */
  emphasis: string[];
}

/**
 * Slide narrative generated by AI.
 */
export interface SlideNarrative {
  /** Unique identifier (UUID) */
  id: string;
  /** The narrative text */
  narrativeText: string;
  /** Emotion indicators in JSON format */
  emotionIndicators: string;
  /** Avatar instructions in JSON format */
  avatarInstructions: string;
  /** Speech markers in JSON format */
  speechMarkers: string;
  /** Duration in seconds */
  durationSeconds?: number;
  /** Transition phrase to next slide */
  transitionPhrase?: string;
  /** Emphasis words in JSON format */
  emphasisWords: string;
  /** Version number */
  version: number;
  /** Whether this is the active narrative */
  isActive: boolean;
  /** Generation metadata in JSON format */
  generationMetadata?: string;
  /** Model used for generation */
  modelUsed: string;
  /** Prompt version used */
  promptVersion: string;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
}