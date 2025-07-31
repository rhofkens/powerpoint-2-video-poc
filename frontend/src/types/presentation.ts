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
  | 'UPLOADED'           // Initial state after file upload
  | 'PARSING'           // Extracting slides from PowerPoint
  | 'ANALYZING'         // AI analyzing presentation content
  | 'GENERATING_CONTENT' // Creating narratives and media
  | 'COMPLETED'         // All processing finished
  | 'FAILED';           // Processing error occurred

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