// Types for intro video generation feature

export interface IntroVideoRequest {
  presentationId: string;
  regenerate?: boolean;
  aspectRatio?: string;
  resolution?: string;
}

export interface ColorPalette {
  dominantColors: string[];  // Hex color codes
  primaryColor: string | null;
  secondaryColor: string | null;
  accentColor: string | null;
  brightness: number;  // 0.0 to 1.0
  saturation: number;  // 0.0 to 1.0
  colorScheme: string; // e.g., "monochromatic", "complementary", "analogous"
}

export type VideoGenerationStatus = 
  | 'PENDING' 
  | 'PROCESSING' 
  | 'COMPLETED' 
  | 'FAILED' 
  | 'CANCELLED';

export interface IntroVideo {
  id: string;
  presentationId: string;
  veoGenerationId: string;
  status: VideoGenerationStatus;
  promptText: string;
  negativePrompt: string;
  colorPalette: ColorPalette | null;
  veoModelVersion: string;
  aspectRatio: string;
  resolution: string;
  durationSeconds: number | null;
  googleVideoUrl: string | null;
  publishedUrl: string | null;
  r2AssetId: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type IntroVideoResponse = IntroVideo;

export interface IntroVideoStatusResponse {
  id: string;
  status: VideoGenerationStatus;
  publishedUrl: string | null;
  errorMessage: string | null;
  progress?: number;
}