/**
 * Core UI state properties managed by the UI store.
 */
export interface UIState {
  /** Global loading indicator */
  isLoading: boolean;
  /** Global error message */
  error: string | null;
  /** File upload progress (0-100) */
  uploadProgress: number;
  /** Currently active view/screen */
  currentView: CurrentView;
  /** ID of the currently selected slide */
  selectedSlideId: string | null;
}

/**
 * Available views/screens in the application.
 * Controls which main component is displayed.
 */
export type CurrentView = 'upload' | 'slides' | 'processing' | 'result';

/**
 * Map of component-specific loading states.
 * Keys are component identifiers, values are loading status.
 */
export interface LoadingState {
  [key: string]: boolean;
}

/**
 * Map of component-specific error messages.
 * Keys are component identifiers, values are error messages or null.
 */
export interface ErrorState {
  [key: string]: string | null;
}