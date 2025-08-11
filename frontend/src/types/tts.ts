/**
 * Character-level timestamp for TTS.
 */
export interface SpeechTimestamp {
  /** Character value */
  character: string;
  /** Start time in seconds */
  startTime: number;
  /** End time in seconds */
  endTime: number;
}

/**
 * Generated speech data for a slide.
 */
export interface SlideSpeech {
  /** Unique identifier (UUID) */
  id: string;
  /** Presentation ID */
  presentationId: string;
  /** Slide ID */
  slideId: string;
  /** Slide narrative ID */
  slideNarrativeId: string;
  /** Path to audio file */
  audioFilePath: string;
  /** Voice ID used */
  voiceId: string;
  /** Voice style (business, funny, cynical) */
  voiceStyle?: string;
  /** Model used for generation */
  modelUsed: string;
  /** Duration in seconds */
  durationSeconds?: number;
  /** Character-level timestamps */
  timestamps?: SpeechTimestamp[];
  /** ElevenLabs request ID for stitching */
  requestId?: string;
  /** Audio output format */
  outputFormat?: string;
  /** Generation metadata */
  generationMetadata?: Record<string, unknown>;
  /** Whether this is the active speech */
  isActive: boolean;
  /** Whether transition phrase was included */
  transitionIncluded?: boolean;
  /** Reason why transition was skipped */
  transitionSkippedReason?: string;
  /** ISO 8601 timestamp of creation */
  createdAt: string;
  /** ISO 8601 timestamp of last update */
  updatedAt: string;
}

/**
 * Request to generate speech for a slide.
 */
export interface GenerateSpeechRequest {
  /** Presentation ID */
  presentationId: string;
  /** Slide ID */
  slideId: string;
  /** Narrative style */
  narrativeStyle?: string;
  /** Force regeneration */
  forceRegenerate?: boolean;
}

/**
 * TTS service response wrapper.
 */
export interface TTSResponse<T> {
  /** Success indicator */
  success: boolean;
  /** Response data */
  data?: T;
  /** Error message if failed */
  error?: string;
  /** Additional message */
  message?: string;
}