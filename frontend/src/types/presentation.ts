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
  | 'UPLOADED'    // Initial state after file upload
  | 'PARSING'     // Extracting slides from PowerPoint
  | 'PARSED'      // Parsing completed successfully
  | 'RENDERING'   // Generating slide images
  | 'COMPLETED'   // All processing finished
  | 'FAILED';     // Processing error occurred

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
 * Re-render request.
 */
export interface ReRenderRequest {
  /** Renderer to use */
  renderer: string;
  /** Force re-render even if already rendered */
  force: boolean;
}