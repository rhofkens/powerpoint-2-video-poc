/**
 * Types for Video Story functionality
 */

// Request DTO for creating a new video story
export interface VideoStoryRequest {
  presentationId: string;
  introVideoId: string;
  title?: string;
  description?: string;
  providerType?: string; // Default is "shotstack"
}

// Response DTO for video story operations
export interface VideoStoryResponse {
  id: string;
  title: string;
  status: VideoStoryStatus;
  renderJobId?: string;
  renderStatus?: RenderJobStatus;
  progress?: number;
  videoUrl?: string;
  duration?: number;
  errorMessage?: string;
  compositionData?: string; // JSON composition for preview
  createdAt: string;
  updatedAt: string;
}

// Video story status enum
export enum VideoStoryStatus {
  DRAFT = 'DRAFT',
  READY = 'READY',
  RENDERING = 'RENDERING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED'
}

// Render job status enum
export enum RenderJobStatus {
  QUEUED = 'QUEUED',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

// Parsed composition structure for visualization
export interface VideoComposition {
  timeline: {
    tracks: Array<{
      clips: Array<{
        asset: {
          type: string;
          src?: string;
          html?: string;
          css?: string;
        };
        start: number;
        length: number;
        position?: string;
        offset?: { x: number; y: number };
        transition?: { in: string; out: string };
      }>;
    }>;
    background?: string;
  };
  output: {
    format: string;
    resolution: string;
    fps: number;
    quality: string;
    size?: { width: number; height: number };
  };
}