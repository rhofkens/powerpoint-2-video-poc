import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import { AnalysisStatusDto, AnalysisType, DeckAnalysis } from '../types/presentation';
import { apiService } from '../services/api';

/**
 * Zustand store interface for managing AI analysis state.
 * Handles analysis status tracking, polling, and notifications.
 */
interface AnalysisState {
  /** Current analysis statuses for active operations */
  analysisStatuses: AnalysisStatusDto[];
  /** Deck analysis results by presentation ID */
  deckAnalyses: Record<string, DeckAnalysis | null>;
  /** Active polling intervals by presentation ID */
  pollingIntervals: Record<string, NodeJS.Timeout>;
  /** Loading states for various operations */
  loading: {
    deckAnalysis: Record<string, boolean>;
    statusPolling: Record<string, boolean>;
  };
  /** Selected narrative style for generation */
  narrativeStyle: 'business' | 'funny' | 'cynical';

  // Actions
  /** Sets analysis statuses */
  setAnalysisStatuses: (statuses: AnalysisStatusDto[]) => void;
  /** Sets deck analysis for a presentation */
  setDeckAnalysis: (presentationId: string, analysis: DeckAnalysis | null) => void;
  /** Starts polling for analysis status */
  startPolling: (presentationId: string, analysisType?: string) => void;
  /** Stops polling for a specific presentation */
  stopPolling: (presentationId: string, analysisType?: string) => void;
  /** Stops all polling */
  stopAllPolling: () => void;
  /** Fetches analysis status once */
  fetchAnalysisStatus: (presentationId: string, analysisType?: string) => Promise<void>;
  /** Fetches deck analysis */
  fetchDeckAnalysis: (presentationId: string) => Promise<void>;
  /** Clears analysis data for a presentation */
  clearAnalysisData: (presentationId: string) => void;
  /** Resets the entire store */
  resetStore: () => void;
  /** Sets the narrative style for generation */
  setNarrativeStyle: (style: 'business' | 'funny' | 'cynical') => void;
}

const initialState = {
  analysisStatuses: [],
  deckAnalyses: {},
  pollingIntervals: {},
  loading: {
    deckAnalysis: {},
    statusPolling: {},
  },
  narrativeStyle: 'business' as const,
};

/**
 * Zustand store for managing AI analysis operations.
 * Handles polling, status tracking, and analysis results.
 * 
 * @example
 * ```typescript
 * const { analysisStatuses, startPolling, stopPolling } = useAnalysisStore();
 * ```
 */
export const useAnalysisStore = create<AnalysisState>()(
  devtools(
    (set, get) => ({
      ...initialState,

      setAnalysisStatuses: (statuses) =>
        set({ analysisStatuses: statuses }, false, 'setAnalysisStatuses'),

      setDeckAnalysis: (presentationId, analysis) =>
        set((state) => ({
          deckAnalyses: { ...state.deckAnalyses, [presentationId]: analysis },
        }), false, 'setDeckAnalysis'),

      fetchAnalysisStatus: async (presentationId, analysisType) => {
        console.log('[AnalysisStore] Fetching analysis status for:', presentationId, 'type:', analysisType);
        
        // Set loading state
        set((state) => ({
          loading: {
            ...state.loading,
            statusPolling: { ...state.loading.statusPolling, [presentationId]: true },
          },
        }), false, 'setStatusPollingLoading');

        try {
          const newStatuses = await apiService.getAnalysisStatus(presentationId, analysisType as AnalysisType);
          console.log('[AnalysisStore] Received statuses from API:', newStatuses);
          
          // Update statuses - merge with existing statuses for other presentations
          set((state) => {
            // Remove old statuses for this presentation and add new ones
            const otherStatuses = state.analysisStatuses.filter(s => s.presentationId !== presentationId);
            const updatedStatuses = [...otherStatuses, ...newStatuses];
            console.log('[AnalysisStore] Updated statuses in store:', updatedStatuses);
            return { analysisStatuses: updatedStatuses };
          }, false, 'updateAnalysisStatuses');
          
          // Check if we should stop polling
          const hasRunning = newStatuses.some(s => 
            s.presentationId === presentationId &&
            (s.state === 'IN_PROGRESS' || s.state === 'PENDING')
          );
          
          console.log('[AnalysisStore] Has running analyses:', hasRunning);
          
          if (!hasRunning) {
            console.log('[AnalysisStore] No running analyses, stopping polling');
            get().stopPolling(presentationId, analysisType);
          }
          
        } catch (error) {
          console.error('Failed to fetch analysis status:', error);
        } finally {
          // Clear loading state
          set((state) => ({
            loading: {
              ...state.loading,
              statusPolling: { ...state.loading.statusPolling, [presentationId]: false },
            },
          }), false, 'clearStatusPollingLoading');
        }
      },

      fetchDeckAnalysis: async (presentationId) => {
        // Set loading state
        set((state) => ({
          loading: {
            ...state.loading,
            deckAnalysis: { ...state.loading.deckAnalysis, [presentationId]: true },
          },
        }), false, 'setDeckAnalysisLoading');

        try {
          const analysis = await apiService.getDeckAnalysis(presentationId);
          get().setDeckAnalysis(presentationId, analysis);
        } catch (error) {
          // Only log error if it's not a 404 (which is expected when no analysis exists yet)
          const errorStatus = (error as { status?: number })?.status;
          if (errorStatus !== 404) {
            console.error('Failed to fetch deck analysis:', error);
          }
          get().setDeckAnalysis(presentationId, null);
        } finally {
          // Clear loading state
          set((state) => ({
            loading: {
              ...state.loading,
              deckAnalysis: { ...state.loading.deckAnalysis, [presentationId]: false },
            },
          }), false, 'clearDeckAnalysisLoading');
        }
      },

      startPolling: (presentationId, analysisType) => {
        const state = get();
        
        // Create a unique key for polling (presentationId + optional type)
        const pollingKey = analysisType ? `${presentationId}_${analysisType}` : presentationId;
        
        // Don't start if already polling
        if (state.pollingIntervals[pollingKey]) {
          return;
        }

        // Initial fetch
        state.fetchAnalysisStatus(presentationId, analysisType);

        // Set up interval
        const interval = setInterval(() => {
          // Use get() to get fresh state each time
          get().fetchAnalysisStatus(presentationId, analysisType);
        }, 5000); // Poll every 5 seconds

        // Store interval reference
        set((state) => ({
          pollingIntervals: { ...state.pollingIntervals, [pollingKey]: interval },
        }), false, 'startPolling');
      },

      stopPolling: (presentationId, analysisType) => {
        const state = get();
        const pollingKey = analysisType ? `${presentationId}_${analysisType}` : presentationId;
        const interval = state.pollingIntervals[pollingKey];
        
        if (interval) {
          clearInterval(interval);
          
          // Remove interval from store
          set((state) => {
            const { [pollingKey]: removed, ...rest } = state.pollingIntervals;
            return { pollingIntervals: rest };
          }, false, 'stopPolling');
        }
      },

      stopAllPolling: () => {
        const state = get();
        
        // Clear all intervals
        Object.values(state.pollingIntervals).forEach(interval => {
          clearInterval(interval);
        });
        
        // Clear from store
        set({ pollingIntervals: {} }, false, 'stopAllPolling');
      },

      clearAnalysisData: (presentationId) => {
        set((state) => {
          const { [presentationId]: removedDeck, ...restDeck } = state.deckAnalyses;
          const { [presentationId]: removedInterval, ...restIntervals } = state.pollingIntervals;
          
          // Clear interval if exists
          if (removedInterval) {
            clearInterval(removedInterval);
          }
          
          return {
            deckAnalyses: restDeck,
            pollingIntervals: restIntervals,
            analysisStatuses: state.analysisStatuses.filter(s => s.presentationId !== presentationId),
          };
        }, false, 'clearAnalysisData');
      },

      resetStore: () => {
        // Stop all polling before resetting
        get().stopAllPolling();
        set(initialState, false, 'resetStore');
      },

      setNarrativeStyle: (style) => 
        set({ narrativeStyle: style }, false, 'setNarrativeStyle'),
    }),
    {
      name: 'analysis-store',
    }
  )
);