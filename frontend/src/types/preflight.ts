/**
 * Type definitions for the Pre-flight Check feature.
 * These types mirror the backend DTOs for presentation validation.
 */

/**
 * Request options for initiating a preflight check
 */
export interface PreflightCheckOptions {
  /** Whether to check for enhanced narrative text (with emotional markers) */
  checkEnhancedNarrative?: boolean;
  /** Force a fresh check even if cached results are available */
  forceRefresh?: boolean;
  /** Whether to check for intro video generation status at the presentation level */
  checkIntroVideo?: boolean;
}

/**
 * Status of an individual check within a slide validation
 */
export type CheckStatus = 'PASSED' | 'FAILED' | 'WARNING' | 'NOT_APPLICABLE' | 'CHECKING' | 'IN_PROGRESS' | 'NOT_FOUND';

/**
 * Overall status of a presentation's preflight check
 */
export type PreflightStatus = 'READY' | 'INCOMPLETE' | 'HAS_WARNINGS' | 'CHECKING' | 'ERROR';

/**
 * Detailed check results for an individual slide
 */
export interface SlideCheckResult {
  /** Unique identifier of the slide */
  slideId: string;
  /** Sequential number of the slide in the presentation */
  slideNumber: number;
  /** Title or heading of the slide */
  slideTitle: string;
  /** Status of the base narrative text */
  narrativeStatus: CheckStatus;
  /** Status of the enhanced narrative text (with emotional markers) */
  enhancedNarrativeStatus: CheckStatus;
  /** Status of the TTS audio generation */
  audioStatus: CheckStatus;
  /** Status of the avatar video generation */
  avatarVideoStatus: CheckStatus;
  /** Status of the slide image rendering and R2 upload */
  imageStatus: CheckStatus;
  /** List of specific issues found during validation */
  issues: string[];
  /** Additional metadata about the check (file paths, URLs, etc.) */
  metadata: Record<string, unknown>;
}

/**
 * Summary statistics for a preflight check
 */
export interface PreflightSummary {
  /** Total number of slides in the presentation */
  totalSlides: number;
  /** Number of slides that are fully ready for video generation */
  slidesReady: number;
  /** Number of slides missing narrative text */
  slidesMissingNarrative: number;
  /** Number of slides missing TTS audio */
  slidesMissingAudio: number;
  /** Number of slides missing avatar video */
  slidesMissingVideo: number;
  /** Number of slides missing rendered images */
  slidesMissingImages: number;
  /** Number of slides missing enhanced narratives (optional warning) */
  slidesMissingEnhancedNarrative: number;
  /** Number of slides with assets not published to R2 */
  slidesWithUnpublishedAssets: number;
  /** Whether all mandatory checks have passed */
  allMandatoryChecksPassed: boolean;
  /** Whether the presentation has an intro video */
  hasIntroVideo?: boolean;
  /** Status of the intro video (if exists) */
  introVideoStatus?: CheckStatus;
  /** Intro video generation status if in progress */
  introVideoGenerationStatus?: string;
  /** URL of the published intro video if available */
  introVideoUrl?: string;
}

/**
 * Result of presentation-level checks
 */
export interface PresentationCheckResult {
  /** Status of the intro video check */
  introVideoStatus: CheckStatus;
  /** ID of the intro video if it exists */
  introVideoId?: string;
  /** Published URL of the intro video if available */
  introVideoUrl?: string;
  /** Current generation status of the intro video */
  generationStatus?: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  /** When the intro video was created */
  createdAt?: string;
  /** When the intro video generation completed */
  completedAt?: string;
  /** Duration of the intro video in seconds */
  durationSeconds?: number;
  /** List of issues found during presentation-level checks */
  issues: string[];
  /** Additional metadata about the presentation checks */
  metadata: Record<string, unknown>;
}

/**
 * Complete response from a preflight check operation
 */
export interface PreflightCheckResponse {
  /** The ID of the presentation that was checked */
  presentationId: string;
  /** Overall status of the presentation readiness */
  overallStatus: PreflightStatus;
  /** Detailed check results for each slide */
  slideResults: SlideCheckResult[];
  /** Summary statistics of the check results */
  summary: PreflightSummary;
  /** Presentation-level check results */
  presentationCheckResult?: PresentationCheckResult;
  /** Timestamp when the check was performed */
  checkedAt: string;
}

/**
 * Helper function to get status color for UI display
 */
export function getStatusColor(status: CheckStatus | PreflightStatus): string {
  switch (status) {
    case 'PASSED':
    case 'READY':
      return 'text-green-600';
    case 'FAILED':
    case 'INCOMPLETE':
    case 'ERROR':
      return 'text-red-600';
    case 'WARNING':
    case 'HAS_WARNINGS':
      return 'text-yellow-600';
    case 'NOT_APPLICABLE':
    case 'NOT_FOUND':
      return 'text-gray-400';
    case 'CHECKING':
    case 'IN_PROGRESS':
      return 'text-blue-600';
    default:
      return 'text-gray-600';
  }
}

/**
 * Helper function to get status icon name for UI display
 */
export function getStatusIcon(status: CheckStatus | PreflightStatus): string {
  switch (status) {
    case 'PASSED':
    case 'READY':
      return 'CheckCircle';
    case 'FAILED':
    case 'INCOMPLETE':
    case 'ERROR':
      return 'XCircle';
    case 'WARNING':
    case 'HAS_WARNINGS':
      return 'AlertTriangle';
    case 'NOT_APPLICABLE':
    case 'NOT_FOUND':
      return 'Minus';
    case 'CHECKING':
    case 'IN_PROGRESS':
      return 'Loader2';
    default:
      return 'Circle';
  }
}