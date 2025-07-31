import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import { UIState, CurrentView, LoadingState, ErrorState } from '../types/ui';

/**
 * Zustand store interface for managing UI state.
 * Handles loading states, errors, view navigation, and upload progress.
 */
interface UIStoreState extends UIState {
  /** Map of component-specific loading states */
  loadingStates: LoadingState;
  /** Map of component-specific error states */
  errorStates: ErrorState;
  
  // Actions
  /** Sets the global loading state */
  setLoading: (isLoading: boolean) => void;
  /** Sets the global error message */
  setError: (error: string | null) => void;
  /** Updates the file upload progress percentage */
  setUploadProgress: (progress: number) => void;
  /** Changes the current view/screen */
  setCurrentView: (view: CurrentView) => void;
  /** Sets the currently selected slide ID */
  setSelectedSlideId: (slideId: string | null) => void;
  /** Sets loading state for a specific component/item */
  setItemLoading: (key: string, isLoading: boolean) => void;
  /** Sets error state for a specific component/item */
  setItemError: (key: string, error: string | null) => void;
  /** Clears all error states (global and item-specific) */
  clearAllErrors: () => void;
  /** Resets the entire UI state to initial values */
  resetUI: () => void;
}

const initialState: UIState = {
  isLoading: false,
  error: null,
  uploadProgress: 0,
  currentView: 'upload',
  selectedSlideId: null,
};

/**
 * Zustand store for managing UI-related state.
 * Uses devtools middleware for Redux DevTools integration.
 * 
 * @example
 * ```typescript
 * const { isLoading, setLoading, currentView } = useUIStore();
 * ```
 */
export const useUIStore = create<UIStoreState>()(
  devtools(
    (set) => ({
      ...initialState,
      loadingStates: {},
      errorStates: {},
      
      setLoading: (isLoading) =>
        set({ isLoading }, false, 'setLoading'),
        
      setError: (error) =>
        set({ error }, false, 'setError'),
        
      setUploadProgress: (uploadProgress) =>
        set({ uploadProgress }, false, 'setUploadProgress'),
        
      setCurrentView: (currentView) =>
        set({ currentView }, false, 'setCurrentView'),
        
      setSelectedSlideId: (selectedSlideId) =>
        set({ selectedSlideId }, false, 'setSelectedSlideId'),
        
      setItemLoading: (key, isLoading) =>
        set((state) => ({
          loadingStates: { ...state.loadingStates, [key]: isLoading },
        }), false, 'setItemLoading'),
        
      setItemError: (key, error) =>
        set((state) => ({
          errorStates: { ...state.errorStates, [key]: error },
        }), false, 'setItemError'),
        
      clearAllErrors: () =>
        set({ error: null, errorStates: {} }, false, 'clearAllErrors'),
        
      resetUI: () =>
        set({ ...initialState, loadingStates: {}, errorStates: {} }, false, 'resetUI'),
    }),
    {
      name: 'ui-store',
    }
  )
);