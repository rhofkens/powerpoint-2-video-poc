import axios, { AxiosInstance, AxiosProgressEvent, AxiosResponse } from 'axios';
import { 
  Presentation, 
  Slide, 
  PresentationUploadResponse, 
  ApiErrorResponse, 
  UploadProgress,
  PresentationStatus,
  ReRenderRequest,
  DeckAnalysis,
  SlideAnalysis,
  SlideNarrative,
  AnalysisStatusDto,
  AnalysisType,
  AvatarVideo,
  AvatarVideoRequest,
  AvatarVideoResponse,
  AvatarVideoStatusDto
} from '../types/presentation';

import {
  SlideSpeech,
  GenerateSpeechRequest,
  TTSResponse
} from '../types/tts';

import {
  PreflightCheckOptions,
  PreflightCheckResponse
} from '../types/preflight';

import {
  IntroVideo,
  IntroVideoRequest,
  IntroVideoResponse,
  ColorPalette
} from '../types/intro-video';

import {
  VideoStoryRequest,
  VideoStoryResponse
} from '../types/video-story';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

/**
 * Response structure for health check endpoint
 */
interface HealthResponse {
  status: string;
}

/**
 * Custom error class for API-related errors.
 * Extends the standard Error class with HTTP status code information.
 */
class ApiError extends Error {
  constructor(message: string, public status?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Service class for handling all API communication with the backend.
 * Uses Axios for HTTP requests with proper error handling and progress tracking.
 */
class ApiService {
  private axiosInstance: AxiosInstance;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: API_BASE_URL,
      timeout: 60000, // 60 seconds timeout for AI operations
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Request interceptor for logging and request modification
    this.axiosInstance.interceptors.request.use(
      (config) => {
        console.log(`Making ${config.method?.toUpperCase()} request to ${config.url}`);
        return config;
      },
      (error) => {
        console.error('Request interceptor error:', error);
        return Promise.reject(error);
      }
    );

    // Response interceptor for error handling
    this.axiosInstance.interceptors.response.use(
      (response: AxiosResponse) => {
        console.log(`Response received: ${response.status} ${response.statusText}`);
        return response;
      },
      (error) => {
        console.error('Response interceptor error:', error);
        
        if (error.response) {
          // Server responded with error status
          const apiError = new ApiError(
            error.response.data?.message || error.response.statusText || 'Server error',
            error.response.status
          );
          return Promise.reject(apiError);
        } else if (error.request) {
          // Network error
          const apiError = new ApiError('Network error - please check your connection');
          return Promise.reject(apiError);
        } else {
          // Request setup error
          const apiError = new ApiError(error.message || 'Unknown error occurred');
          return Promise.reject(apiError);
        }
      }
    );
  }

  /**
   * Checks the health status of the backend API.
   * 
   * @returns Promise resolving to health status response
   */
  async checkHealth(): Promise<HealthResponse> {
    const response = await this.axiosInstance.get<HealthResponse>('/health');
    return response.data;
  }

  /**
   * Uploads a PowerPoint presentation file to the backend.
   * 
   * @param file - The PowerPoint file to upload
   * @param onProgress - Callback function to track upload progress
   * @returns Promise resolving to upload response
   */
  async uploadPresentation(
    file: File,
    onProgress?: (progress: UploadProgress) => void
  ): Promise<PresentationUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);

    const startTime = Date.now();

    const response = await this.axiosInstance.post<PresentationUploadResponse>(
      '/presentations/upload',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent: AxiosProgressEvent) => {
          if (progressEvent.total && onProgress) {
            const currentTime = Date.now();
            const timeElapsed = (currentTime - startTime) / 1000; // seconds
            const loaded = progressEvent.loaded;
            const total = progressEvent.total;
            const percentage = Math.round((loaded / total) * 100);
            
            // Calculate speed (bytes per second)
            const speed = timeElapsed > 0 ? loaded / timeElapsed : 0;
            
            // Estimate remaining time
            const remainingBytes = total - loaded;
            const estimatedTime = speed > 0 ? remainingBytes / speed : undefined;

            const progressInfo: UploadProgress = {
              percentage,
              speed,
              estimatedTime,
              total,
              loaded,
            };

            onProgress(progressInfo);
          }
        },
      }
    );

    return response.data;
  }

  /**
   * Retrieves all presentations from the backend.
   * 
   * @returns Promise resolving to array of presentations
   */
  async getPresentations(): Promise<Presentation[]> {
    const response = await this.axiosInstance.get<Presentation[]>('/presentations');
    return response.data;
  }

  /**
   * Retrieves a specific presentation by ID.
   * 
   * @param id - The UUID of the presentation
   * @returns Promise resolving to the presentation data
   */
  async getPresentation(id: string): Promise<Presentation> {
    const response = await this.axiosInstance.get<Presentation>(`/presentations/${id}`);
    return response.data;
  }

  /**
   * Deletes a presentation by ID.
   * 
   * @param id - The UUID of the presentation to delete
   * @returns Promise resolving when deletion is complete
   */
  async deletePresentation(id: string): Promise<void> {
    await this.axiosInstance.delete(`/presentations/${id}`);
  }

  /**
   * Retrieves all slides for a specific presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to array of slides
   */
  async getSlides(presentationId: string): Promise<Slide[]> {
    const response = await this.axiosInstance.get<Slide[]>(`/presentations/${presentationId}/slides`);
    return response.data;
  }

  /**
   * Retrieves a specific slide by ID.
   * 
   * @param slideId - The UUID of the slide
   * @returns Promise resolving to the slide data
   */
  async getSlide(slideId: string): Promise<Slide> {
    const response = await this.axiosInstance.get<Slide>(`/slides/${slideId}`);
    return response.data;
  }

  /**
   * Gets the processing status of a presentation.
   * 
   * @param id - The UUID of the presentation
   * @returns Promise resolving to the status information
   */
  async getPresentationStatus(id: string): Promise<PresentationStatus> {
    const response = await this.axiosInstance.get<PresentationStatus>(`/presentations/${id}/status`);
    return response.data;
  }

  /**
   * Triggers re-rendering of a presentation.
   * 
   * @param id - The UUID of the presentation
   * @param renderer - The renderer to use (e.g., 'MSGRAPH', 'ASPOSE', 'POI')
   * @param force - Whether to force re-render even if already rendered
   * @returns Promise resolving when re-render is initiated
   */
  async reRenderPresentation(id: string, renderer: string, force: boolean = false): Promise<void> {
    const request: ReRenderRequest = { renderer, force };
    await this.axiosInstance.post(`/presentations/${id}/render`, request);
  }

  /**
   * Gets the status of available renderers.
   * 
   * @returns Promise resolving to renderer status information
   */
  async getRendererStatus(): Promise<Array<{ name: string; available: boolean }>> {
    const response = await this.axiosInstance.get<Array<{ name: string; available: boolean }>>('/renderers/status');
    return response.data;
  }

  /**
   * Triggers AI analysis of an entire presentation deck.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to deck analysis result
   */
  async analyzeDeck(presentationId: string): Promise<DeckAnalysis> {
    const response = await this.axiosInstance.post<{ success: boolean; data: DeckAnalysis; message: string }>(
      `/presentations/${presentationId}/analyze-deck`,
      {},
      { timeout: 90000 } // 90 seconds timeout for deck analysis
    );
    return response.data.data;
  }

  /**
   * Gets the deck analysis for a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to deck analysis if available
   */
  async getDeckAnalysis(presentationId: string): Promise<DeckAnalysis | null> {
    try {
      const response = await this.axiosInstance.get<{ success: boolean; data: DeckAnalysis; message: string }>(
        `/presentations/${presentationId}/analysis`
      );
      return response.data.data;
    } catch (error) {
      if ((error as ApiError).status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Triggers AI analysis of a specific slide.
   * 
   * @param slideId - The UUID of the slide
   * @param force - Whether to force re-analysis even if analysis exists
   * @returns Promise resolving to slide analysis result
   */
  async analyzeSlide(slideId: string, force: boolean = false): Promise<SlideAnalysis> {
    const response = await this.axiosInstance.post<{ success: boolean; data: SlideAnalysis; message: string }>(
      `/slides/${slideId}/analyze${force ? '?force=true' : ''}`
    );
    return response.data.data;
  }

  /**
   * Gets the analysis for a slide.
   * 
   * @param slideId - The UUID of the slide
   * @returns Promise resolving to slide analysis if available
   */
  async getSlideAnalysis(slideId: string): Promise<SlideAnalysis | null> {
    try {
      const response = await this.axiosInstance.get<{ success: boolean; data: SlideAnalysis; message: string }>(
        `/slides/${slideId}/analysis`
      );
      return response.data.data;
    } catch (error) {
      if ((error as ApiError).status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Generates narrative for a slide.
   * 
   * @param slideId - The UUID of the slide
   * @param style - The narrative style ('business', 'funny', 'cynical')
   * @returns Promise resolving to generated narrative
   */
  async generateNarrative(slideId: string, style: string = 'business'): Promise<SlideNarrative> {
    const response = await this.axiosInstance.post<{ success: boolean; data: SlideNarrative; message: string }>(
      `/slides/${slideId}/generate-narrative`,
      { style }
    );
    return response.data.data;
  }

  /**
   * Enhances a slide's narrative with emotional markers for TTS.
   * 
   * @param slideId - The UUID of the slide
   * @param force - Whether to force re-enhancement even if already enhanced
   * @returns Promise resolving to enhanced narrative
   */
  async enhanceSlideNarrative(slideId: string, force: boolean = false): Promise<SlideNarrative> {
    const response = await this.axiosInstance.post<{ success: boolean; data: SlideNarrative; message: string }>(
      `/ai/slides/${slideId}/enhance-narrative${force ? '?force=true' : ''}`,
      {},
      { timeout: 120000 } // 120 seconds timeout for emotional enhancement (AI can be slow)
    );
    return response.data.data;
  }

  /**
   * Shortens an existing narrative by a specified percentage.
   * 
   * @param narrativeId - The UUID of the narrative to shorten
   * @param reductionPercentage - The percentage to reduce (25-75)
   * @returns Promise resolving to shortened narrative response
   */
  async shortenNarrative(narrativeId: string, reductionPercentage: number): Promise<{
    originalText: string;
    shortenedText: string;
    originalWordCount: number;
    newWordCount: number;
    actualReduction: number;
    narrativeId: string;
    message: string;
  }> {
    const response = await this.axiosInstance.post<{ 
      success: boolean; 
      data: {
        originalText: string;
        shortenedText: string;
        originalWordCount: number;
        newWordCount: number;
        actualReduction: number;
        narrativeId: string;
        message: string;
      }; 
      message: string 
    }>(
      `/narratives/${narrativeId}/shorten?reductionPercentage=${reductionPercentage}`,
      {},
      { timeout: 60000 } // 60 seconds timeout for narrative shortening
    );
    return response.data.data;
  }

  /**
   * Gets the narrative for a slide.
   * 
   * @param slideId - The UUID of the slide
   * @returns Promise resolving to slide narrative if available
   */
  async getSlideNarrative(slideId: string): Promise<SlideNarrative | null> {
    try {
      const response = await this.axiosInstance.get<{ success: boolean; data: SlideNarrative; message: string }>(
        `/slides/${slideId}/narrative`
      );
      return response.data.data;
    } catch (error) {
      if ((error as ApiError).status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Triggers AI analysis for all slides in a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving when analysis is initiated
   */
  async analyzeAllSlides(presentationId: string): Promise<void> {
    await this.axiosInstance.post(`/presentations/${presentationId}/analyze-all-slides`);
  }

  /**
   * Generates narratives for all slides in a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @param style - The narrative style ('business', 'funny', 'cynical')
   * @returns Promise resolving when generation is initiated
   */
  async generateAllNarratives(presentationId: string, style: string = 'business'): Promise<void> {
    console.log('[ApiService] Generating all narratives for:', presentationId, 'style:', style);
    const response = await this.axiosInstance.post(`/presentations/${presentationId}/generate-all-narratives`, { style });
    console.log('[ApiService] Generate all narratives response:', response.data);
  }

  /**
   * Gets the complete narrative for all slides in a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to a list of all slide narratives
   */
  async getCompleteNarrative(presentationId: string): Promise<SlideNarrative[]> {
    const response = await this.axiosInstance.get<{ success: boolean; data: SlideNarrative[]; message: string }>(
      `/presentations/${presentationId}/complete-narrative`
    );
    return response.data.data || [];
  }

  /**
   * Gets the status of ongoing AI analysis operations.
   * 
   * @param presentationId - The UUID of the presentation
   * @param type - Optional analysis type filter
   * @returns Promise resolving to analysis status information
   */
  async getAnalysisStatus(presentationId: string, type?: AnalysisType): Promise<AnalysisStatusDto[]> {
    const params = type ? { type } : {};
    console.log('[ApiService] Getting analysis status for:', presentationId, 'type:', type);
    const response = await this.axiosInstance.get<{ success: boolean; data: AnalysisStatusDto[]; message: string }>(
      `/presentations/${presentationId}/analysis-status`,
      { params }
    );
    console.log('[ApiService] Analysis status response:', response.data);
    return response.data.data;
  }

  /**
   * Generates speech for a slide using Text-to-Speech.
   * 
   * @param slideId - The UUID of the slide
   * @param request - The speech generation request parameters
   * @returns Promise resolving to generated speech data
   */
  async generateSpeech(slideId: string, request: Partial<GenerateSpeechRequest>): Promise<SlideSpeech> {
    console.log('[ApiService] Generating speech for slide:', slideId, 'with params:', request);
    const response = await this.axiosInstance.post<TTSResponse<SlideSpeech>>(
      `/slides/${slideId}/generate-speech`,
      request
    );
    
    if (!response.data.success || !response.data.data) {
      throw new ApiError(response.data.error || 'Failed to generate speech');
    }
    
    console.log('[ApiService] Speech generation response:', response.data);
    return response.data.data;
  }

  /**
   * Gets the speech data for a slide.
   * 
   * @param slideId - The UUID of the slide
   * @returns Promise resolving to speech data if available
   */
  async getSlideSpeech(slideId: string): Promise<SlideSpeech | null> {
    try {
      const response = await this.axiosInstance.get<TTSResponse<SlideSpeech>>(
        `/slides/${slideId}/speech`
      );
      
      if (!response.data.success) {
        return null;
      }
      
      return response.data.data || null;
    } catch (error) {
      if ((error as ApiError).status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Gets all speeches for a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to list of speeches
   */
  async getPresentationSpeeches(presentationId: string): Promise<SlideSpeech[]> {
    const response = await this.axiosInstance.get<TTSResponse<SlideSpeech[]>>(
      `/presentations/${presentationId}/speeches`
    );
    
    if (!response.data.success) {
      throw new ApiError(response.data.error || 'Failed to get speeches');
    }
    
    return response.data.data || [];
  }

  /**
   * Gets the URL for a speech audio file.
   * 
   * @param speechId - The UUID of the speech
   * @returns The URL for the audio file
   */
  getSpeechAudioUrl(speechId: string): string {
    return `${API_BASE_URL}/speeches/${speechId}/audio`;
  }

  /**
   * Generate an avatar video for a slide.
   * 
   * @param request - The avatar video generation request
   * @returns The avatar video response
   */
  async generateAvatarVideo(request: AvatarVideoRequest): Promise<AvatarVideoResponse> {
    const response = await this.axiosInstance.post<{
      success: boolean;
      data: AvatarVideoResponse;
      message?: string;
      error?: { description?: string };
    }>('/avatar-videos/generate', request);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to generate avatar video');
    }
    
    return response.data.data;
  }

  /**
   * Get the status of an avatar video generation job.
   * 
   * @param avatarVideoId - The UUID of the avatar video
   * @returns The avatar video status
   */
  async getAvatarVideoStatus(avatarVideoId: string): Promise<AvatarVideoStatusDto> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data: AvatarVideoStatusDto;
      message?: string;
      error?: { description?: string };
    }>(`/avatar-videos/${avatarVideoId}/status`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to get avatar video status');
    }
    
    return response.data.data;
  }

  /**
   * Get all avatar videos for a slide.
   * 
   * @param slideId - The UUID of the slide
   * @returns List of avatar videos
   */
  async getSlideAvatarVideos(slideId: string): Promise<AvatarVideoResponse[]> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data: AvatarVideoResponse[];
      message?: string;
      error?: { description?: string };
    }>(`/avatar-videos/slide/${slideId}`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to get slide avatar videos');
    }
    
    return response.data.data || [];
  }

  /**
   * Publish a completed avatar video to R2 storage.
   * 
   * @param avatarVideoId - The UUID of the avatar video
   * @returns The published asset information
   */
  async publishAvatarVideo(avatarVideoId: string): Promise<{ url: string }> {
    const response = await this.axiosInstance.post<{
      success: boolean;
      data: { url: string };
      message?: string;
      error?: { description?: string };
    }>(`/avatar-videos/${avatarVideoId}/publish`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to publish avatar video');
    }
    
    return response.data.data;
  }

  /**
   * Run a comprehensive preflight check on a presentation.
   * Validates that all slides have required components (narrative, audio, video).
   * 
   * @param presentationId - The UUID of the presentation
   * @param options - Optional check configuration
   * @returns Detailed check results including status for each slide
   */
  async runPreflightCheck(
    presentationId: string,
    options?: PreflightCheckOptions
  ): Promise<PreflightCheckResponse> {
    const response = await this.axiosInstance.post<{
      success: boolean;
      data: PreflightCheckResponse;
      message?: string;
      error?: { description?: string };
    }>(`/presentations/${presentationId}/preflight-check`, options || {});
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to run preflight check');
    }
    
    return response.data.data;
  }

  /**
   * Get the latest preflight check status for a presentation.
   * Returns cached results if available and still valid.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns The latest check results if available
   */
  async getPreflightStatus(presentationId: string): Promise<PreflightCheckResponse | null> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data?: PreflightCheckResponse;
      message?: string;
      error?: { description?: string };
    }>(`/presentations/${presentationId}/preflight-status`);
    
    if (!response.data.success) {
      // No cached status available
      return null;
    }
    
    return response.data.data || null;
  }

  // ============= Intro Video API methods =============

  /**
   * Generate an intro video for a presentation.
   * 
   * @param request - The intro video generation request
   * @returns The intro video generation response
   */
  async generateIntroVideo(request: IntroVideoRequest): Promise<IntroVideoResponse> {
    const response = await this.axiosInstance.post<{
      success: boolean;
      data: IntroVideoResponse;
      message?: string;
      error?: { description?: string };
    }>('/intro-videos/generate', request);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to generate intro video');
    }
    
    return response.data.data;
  }

  /**
   * Get the status of an intro video generation.
   * 
   * @param introVideoId - The UUID of the intro video
   * @returns The intro video status
   */
  async getIntroVideoStatus(introVideoId: string): Promise<IntroVideoResponse> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data: IntroVideoResponse;
      message?: string;
      error?: { description?: string };
    }>(`/intro-videos/${introVideoId}/status`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to get intro video status');
    }
    
    return response.data.data;
  }

  /**
   * Get the latest intro video for a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns The intro video if exists
   */
  async getIntroVideo(presentationId: string): Promise<IntroVideo | null> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data: IntroVideo;
      message?: string;
      error?: { description?: string };
    }>(`/intro-videos/presentation/${presentationId}`);
    
    if (!response.data.success) {
      return null;
    }
    
    return response.data.data;
  }

  /**
   * Cancel an intro video generation.
   * 
   * @param introVideoId - The UUID of the intro video
   */
  async cancelIntroVideoGeneration(introVideoId: string): Promise<void> {
    const response = await this.axiosInstance.post<{
      success: boolean;
      message?: string;
      error?: { description?: string };
    }>(`/intro-videos/${introVideoId}/cancel`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to cancel intro video generation');
    }
  }

  /**
   * Extract dominant colors from the first slide of a presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns The extracted color palette
   */
  async extractSlideColors(presentationId: string): Promise<ColorPalette> {
    const response = await this.axiosInstance.get<{
      success: boolean;
      data: ColorPalette;
      message?: string;
      error?: { description?: string };
    }>(`/intro-videos/presentation/${presentationId}/colors`);
    
    if (!response.data.success) {
      throw new ApiError(response.data.error?.description || response.data.message || 'Failed to extract colors');
    }
    
    return response.data.data;
  }

  // ============================================
  // Video Story Methods
  // ============================================

  /**
   * Create a new video story composition.
   * This generates the JSON composition without rendering.
   * 
   * @param request - The video story creation request
   * @returns The created video story with composition data
   */
  async createVideoStory(request: VideoStoryRequest): Promise<VideoStoryResponse> {
    const response = await this.axiosInstance.post<VideoStoryResponse>(
      '/video-stories', 
      request,
      { timeout: 600000 } // 10 minutes timeout for asset publishing (can upload many large video files to Shotstack)
    );
    return response.data;
  }

  /**
   * Get a video story with its composition.
   * 
   * @param videoStoryId - The UUID of the video story
   * @returns The video story with composition data
   */
  async getVideoStory(videoStoryId: string): Promise<VideoStoryResponse> {
    const response = await this.axiosInstance.get<VideoStoryResponse>(`/video-stories/${videoStoryId}`);
    return response.data;
  }

  /**
   * Render an existing video story.
   * Submits the composition for actual video rendering.
   * 
   * @param videoStoryId - The UUID of the video story to render
   * @returns The render job status
   */
  async renderVideoStory(videoStoryId: string): Promise<VideoStoryResponse> {
    const response = await this.axiosInstance.post<VideoStoryResponse>(`/video-stories/${videoStoryId}/render`);
    return response.data;
  }

  /**
   * Check the render status of a video story.
   * 
   * @param videoStoryId - The UUID of the video story
   * @returns The current render status
   */
  async checkVideoStoryRenderStatus(videoStoryId: string): Promise<VideoStoryResponse> {
    const response = await this.axiosInstance.get<VideoStoryResponse>(`/video-stories/${videoStoryId}/render/status`);
    return response.data;
  }

  /**
   * Poll for video story render completion.
   * 
   * @param videoStoryId - The UUID of the video story
   * @param onProgress - Callback for progress updates
   * @param pollInterval - Polling interval in milliseconds (default: 5000)
   * @returns Promise that resolves when rendering is complete or fails
   */
  async pollVideoStoryRenderStatus(
    videoStoryId: string,
    onProgress?: (response: VideoStoryResponse) => void,
    pollInterval: number = 5000
  ): Promise<VideoStoryResponse> {
    return new Promise((resolve, reject) => {
      const poll = async () => {
        try {
          const response = await this.checkVideoStoryRenderStatus(videoStoryId);
          
          if (onProgress) {
            onProgress(response);
          }

          if (response.renderStatus === 'COMPLETED') {
            resolve(response);
          } else if (response.renderStatus === 'FAILED' || response.renderStatus === 'CANCELLED') {
            reject(new ApiError(response.errorMessage || 'Video rendering failed'));
          } else {
            // Continue polling
            setTimeout(poll, pollInterval);
          }
        } catch (error) {
          reject(error);
        }
      };

      poll();
    });
  }
}

export const apiService = new ApiService();
export { ApiError };