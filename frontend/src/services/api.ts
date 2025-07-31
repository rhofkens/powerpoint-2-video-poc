import { Presentation, Slide } from '../types/presentation';

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
 * Provides methods for presentations, slides, and health checks.
 */
class ApiService {
  /**
   * Generic request method for making HTTP requests to the API.
   * Handles error responses, parsing, and common headers.
   * 
   * @template T - The expected response type
   * @param endpoint - The API endpoint path (e.g., '/presentations')
   * @param options - Additional fetch options (method, headers, body, etc.)
   * @returns Promise resolving to the parsed response data
   * @throws {ApiError} When the request fails or returns non-OK status
   */
  private async request<T>(
    endpoint: string, 
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;
    
    const config: RequestInit = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    };

    try {
      const response = await fetch(url, config);

      if (!response.ok) {
        const errorText = await response.text();
        throw new ApiError(
          `HTTP ${response.status}: ${errorText || response.statusText}`,
          response.status
        );
      }

      // Handle empty responses
      if (response.status === 204) {
        return {} as T;
      }

      const data = await response.json();
      return data;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      
      // Network or parsing errors
      throw new ApiError(
        error instanceof Error ? error.message : 'Unknown error occurred'
      );
    }
  }

  /**
   * Checks the health status of the backend API.
   * 
   * @returns Promise resolving to health status response
   */
  async checkHealth(): Promise<HealthResponse> {
    return this.request<HealthResponse>('/health');
  }

  /**
   * Retrieves all presentations from the backend.
   * 
   * @returns Promise resolving to array of presentations
   */
  async getPresentations(): Promise<Presentation[]> {
    return this.request<Presentation[]>('/presentations');
  }

  /**
   * Retrieves a specific presentation by ID.
   * 
   * @param id - The UUID of the presentation
   * @returns Promise resolving to the presentation data
   */
  async getPresentation(id: string): Promise<Presentation> {
    return this.request<Presentation>(`/presentations/${id}`);
  }

  /**
   * Deletes a presentation by ID.
   * 
   * @param id - The UUID of the presentation to delete
   * @returns Promise resolving when deletion is complete
   */
  async deletePresentation(id: string): Promise<void> {
    return this.request<void>(`/presentations/${id}`, {
      method: 'DELETE',
    });
  }

  /**
   * Retrieves all slides for a specific presentation.
   * 
   * @param presentationId - The UUID of the presentation
   * @returns Promise resolving to array of slides
   */
  async getSlides(presentationId: string): Promise<Slide[]> {
    return this.request<Slide[]>(`/presentations/${presentationId}/slides`);
  }

  /**
   * Retrieves a specific slide by ID.
   * 
   * @param slideId - The UUID of the slide
   * @returns Promise resolving to the slide data
   */
  async getSlide(slideId: string): Promise<Slide> {
    return this.request<Slide>(`/slides/${slideId}`);
  }
}

export const apiService = new ApiService();
export { ApiError };