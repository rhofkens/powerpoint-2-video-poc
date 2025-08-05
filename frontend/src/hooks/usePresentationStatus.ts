import { useEffect, useState, useRef, useCallback } from 'react';
import { apiService } from '../services/api';
import { PresentationStatus, ProcessingStatus } from '../types/presentation';
import { usePresentationStore } from '../store/presentationStore';
import { useToast } from './use-toast';

interface UsePresentationStatusOptions {
  /** Polling interval in milliseconds */
  interval?: number;
  /** Whether to enable polling */
  enabled?: boolean;
}

/**
 * Custom hook for polling presentation status.
 * Automatically polls for status updates when a presentation is being processed.
 * 
 * @param presentationId - The ID of the presentation to monitor
 * @param options - Configuration options
 * @returns Status information and control functions
 */
export function usePresentationStatus(
  presentationId: string | null,
  options: UsePresentationStatusOptions = {}
) {
  const { interval = 2000, enabled = true } = options;
  
  const [status, setStatus] = useState<PresentationStatus | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const { updatePresentation, setProcessingStatus } = usePresentationStore();
  const { toast } = useToast();
  
  const fetchStatus = useCallback(async () => {
    if (!presentationId) return;
    
    try {
      setIsLoading(true);
      setError(null);
      
      const statusData = await apiService.getPresentationStatus(presentationId);
      setStatus(statusData);
      
      // Determine overall status based on parsing and rendering progress
      let overallStatus: ProcessingStatus = 'UPLOADED';
      
      if (statusData.parsingProgress.status === 'FAILED' || 
          statusData.renderingProgress.status === 'FAILED') {
        overallStatus = 'FAILED';
      } else if (statusData.renderingProgress.status === 'COMPLETED') {
        overallStatus = 'COMPLETED';
      } else if (statusData.renderingProgress.status === 'RENDERING' || 
                 statusData.renderingProgress.status === 'IN_PROGRESS') {
        overallStatus = 'RENDERING';
      } else if (statusData.parsingProgress.status === 'COMPLETED' || 
                 statusData.parsingProgress.status === 'PARSED') {
        overallStatus = 'PARSED';
      } else if (statusData.parsingProgress.status === 'PARSING' || 
                 statusData.parsingProgress.status === 'IN_PROGRESS') {
        overallStatus = 'PARSING';
      }
      
      // Update the store with the new status
      updatePresentation(presentationId, { processingStatus: overallStatus });
      setProcessingStatus(presentationId, overallStatus);
      
      // Stop polling if processing is complete or failed
      if (overallStatus === 'COMPLETED' || overallStatus === 'FAILED') {
        stopPolling();
        
        // Show toast notification
        if (overallStatus === 'COMPLETED') {
          toast({
            title: "Processing Complete",
            description: "Your presentation has been successfully processed.",
            variant: "default",
          });
        } else {
          toast({
            title: "Processing Failed",
            description: "There was an error processing your presentation.",
            variant: "destructive",
          });
        }
      }
      
    } catch (err) {
      console.error('Error fetching presentation status:', err);
      setError(err instanceof Error ? err : new Error('Failed to fetch status'));
    } finally {
      setIsLoading(false);
    }
  }, [presentationId, updatePresentation, setProcessingStatus, toast]);
  
  const startPolling = useCallback(() => {
    if (!enabled || !presentationId) return;
    
    // Initial fetch
    fetchStatus();
    
    // Set up polling interval
    intervalRef.current = setInterval(fetchStatus, interval);
  }, [enabled, presentationId, fetchStatus, interval]);
  
  const stopPolling = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);
  
  const refetch = useCallback(() => {
    return fetchStatus();
  }, [fetchStatus]);
  
  useEffect(() => {
    startPolling();
    
    return () => {
      stopPolling();
    };
  }, [startPolling, stopPolling]);
  
  return {
    status,
    isLoading,
    error,
    refetch,
    startPolling,
    stopPolling,
  };
}