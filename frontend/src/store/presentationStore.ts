import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import { Presentation, Slide, ProcessingStatus, SlideProcessingStatus } from '../types/presentation';

/**
 * Zustand store interface for managing presentation state.
 * Handles presentations, slides, and their processing statuses.
 */
interface PresentationState {
  /** Array of all loaded presentations */
  presentations: Presentation[];
  /** Currently selected/active presentation */
  currentPresentation: Presentation | null;
  /** Slides belonging to the current presentation */
  currentSlides: Slide[];
  /** Map of entity IDs to their processing status */
  processingStatus: Record<string, ProcessingStatus | SlideProcessingStatus>;
  
  // Actions
  /** Sets the currently active presentation */
  setCurrentPresentation: (presentation: Presentation | null) => void;
  /** Adds a new presentation to the store */
  addPresentation: (presentation: Presentation) => void;
  /** Updates an existing presentation with partial data */
  updatePresentation: (id: string, updates: Partial<Presentation>) => void;
  /** Removes a presentation from the store */
  removePresentation: (id: string) => void;
  /** Sets the slides for the current presentation */
  setSlides: (slides: Slide[]) => void;
  /** Updates a specific slide with partial data */
  updateSlide: (slideId: string, updates: Partial<Slide>) => void;
  /** Sets the processing status for a presentation or slide */
  setProcessingStatus: (id: string, status: ProcessingStatus | SlideProcessingStatus) => void;
  /** Resets the store to initial state */
  resetStore: () => void;
}

const initialState = {
  presentations: [],
  currentPresentation: null,
  currentSlides: [],
  processingStatus: {},
};

/**
 * Zustand store for managing presentation-related state.
 * Uses devtools middleware for Redux DevTools integration.
 * 
 * @example
 * ```typescript
 * const { currentPresentation, setCurrentPresentation } = usePresentationStore();
 * ```
 */
export const usePresentationStore = create<PresentationState>()(
  devtools(
    (set) => ({
      ...initialState,
      
      setCurrentPresentation: (presentation) =>
        set({ currentPresentation: presentation }, false, 'setCurrentPresentation'),
        
      addPresentation: (presentation) =>
        set((state) => ({
          presentations: [...state.presentations, presentation],
        }), false, 'addPresentation'),
        
      updatePresentation: (id, updates) =>
        set((state) => {
          const updatedPresentations = state.presentations.map((p) =>
            p.id === id ? { ...p, ...updates } : p
          );
          
          const updatedCurrentPresentation = 
            state.currentPresentation?.id === id 
              ? { ...state.currentPresentation, ...updates }
              : state.currentPresentation;
              
          return {
            presentations: updatedPresentations,
            currentPresentation: updatedCurrentPresentation,
          };
        }, false, 'updatePresentation'),
        
      removePresentation: (id) =>
        set((state) => ({
          presentations: state.presentations.filter((p) => p.id !== id),
          currentPresentation: 
            state.currentPresentation?.id === id ? null : state.currentPresentation,
        }), false, 'removePresentation'),
        
      setSlides: (slides) =>
        set({ currentSlides: slides }, false, 'setSlides'),
        
      updateSlide: (slideId, updates) =>
        set((state) => ({
          currentSlides: state.currentSlides.map((slide) =>
            slide.id === slideId ? { ...slide, ...updates } : slide
          ),
        }), false, 'updateSlide'),
        
      setProcessingStatus: (id, status) =>
        set((state) => ({
          processingStatus: { ...state.processingStatus, [id]: status },
        }), false, 'setProcessingStatus'),
        
      resetStore: () =>
        set(initialState, false, 'resetStore'),
    }),
    {
      name: 'presentation-store',
    }
  )
);