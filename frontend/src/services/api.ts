import axios, { AxiosInstance, AxiosProgressEvent, AxiosResponse } from 'axios';
import { 
  Presentation, 
  Slide, 
  PresentationUploadResponse, 
  ApiErrorResponse, 
  UploadProgress,
  PresentationStatus,
  ReRenderRequest
} from '../types/presentation';

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
      timeout: 30000, // 30 seconds timeout
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
}

export const apiService = new ApiService();
export { ApiError };