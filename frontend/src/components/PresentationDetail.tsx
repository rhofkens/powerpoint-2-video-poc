import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ProcessingProgress } from './ProcessingProgress';
import { SlidesGrid } from './SlidesGrid';
import { AIAnalysisPanel } from './AIAnalysisPanel';
import { usePresentationStatus } from '@/hooks/usePresentationStatus';
import { usePresentationStore } from '@/store/presentationStore';
import { ArrowLeft, FileText, Calendar, HardDrive } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

interface PresentationDetailProps {
  onBack: () => void;
}

export function PresentationDetail({ onBack }: PresentationDetailProps) {
  const { currentPresentation, setCurrentPresentation } = usePresentationStore();
  const { toast } = useToast();
  const [refreshKey, setRefreshKey] = useState(0);
  
  // Use the polling hook for status updates
  const { status, error } = usePresentationStatus(
    currentPresentation?.id || null,
    {
      enabled: !!currentPresentation && 
               currentPresentation.processingStatus !== 'COMPLETED' && 
               currentPresentation.processingStatus !== 'FAILED',
      onStatusChange: (newStatus) => {
        // Update the current presentation with new status
        if (currentPresentation && newStatus) {
          const oldStatus = currentPresentation.processingStatus;
          const newProcessingStatus = newStatus.parsingProgress.status === 'FAILED' || 
                            newStatus.renderingProgress.status === 'FAILED' 
                            ? 'FAILED' 
                            : newStatus.renderingProgress.status === 'COMPLETED' 
                            ? 'COMPLETED' 
                            : newStatus.parsingProgress.status === 'PARSING' 
                            ? 'PARSING' 
                            : newStatus.parsingProgress.status === 'PARSED' && newStatus.renderingProgress.status === 'RENDERING'
                            ? 'RENDERING'
                            : newStatus.parsingProgress.status === 'PARSED'
                            ? 'PARSED'
                            : 'UPLOADED';
          
          setCurrentPresentation({
            ...currentPresentation,
            processingStatus: newProcessingStatus
          });
          
          // If status changed to COMPLETED, trigger a refresh
          if (oldStatus !== 'COMPLETED' && newProcessingStatus === 'COMPLETED') {
            toast({
              title: "Processing Complete",
              description: "Your presentation has been fully processed!"
            });
            setRefreshKey(prev => prev + 1);
          }
        }
      }
    }
  );

  if (!currentPresentation) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <p className="text-muted-foreground">No presentation selected</p>
          <Button onClick={onBack} variant="outline" className="mt-4">
            <ArrowLeft className="h-4 w-4 mr-2" />
            Back to Upload
          </Button>
        </CardContent>
      </Card>
    );
  }

  const formatFileSize = (bytes: number): string => {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  };

  const isProcessing = currentPresentation.processingStatus !== 'COMPLETED' && 
                      currentPresentation.processingStatus !== 'FAILED';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <Button onClick={onBack} variant="ghost" size="sm">
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back
        </Button>
        <h2 className="text-2xl font-bold">Presentation Details</h2>
        <div className="w-20" /> {/* Spacer for centering */}
      </div>

      {/* Presentation Info */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <div className="flex items-center space-x-3">
              <FileText className="h-6 w-6 text-primary" />
              <span>{currentPresentation.title}</span>
            </div>
            <Badge variant={currentPresentation.processingStatus === 'FAILED' ? 'destructive' : 'default'}>
              {currentPresentation.processingStatus}
            </Badge>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
            <div className="flex items-center space-x-2">
              <Calendar className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">Uploaded:</span>
              <span>{formatDate(currentPresentation.createdAt)}</span>
            </div>
            <div className="flex items-center space-x-2">
              <HardDrive className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">Size:</span>
              <span>{formatFileSize(currentPresentation.fileSize)}</span>
            </div>
            <div className="flex items-center space-x-2">
              <FileText className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">Original:</span>
              <span className="truncate">{currentPresentation.originalFilename}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* AI Analysis Panel - Show when slides are rendered */}
      {['RENDERING_READY', 'ANALYZING', 'INTENT_ANALYSIS_COMPLETE', 
        'NARRATIVE_COMPLETE', 'GENERATING_CONTENT', 'COMPLETED'].includes(currentPresentation.processingStatus) && (
        <AIAnalysisPanel 
          presentationId={currentPresentation.id}
          processingStatus={currentPresentation.processingStatus}
          presentationTitle={currentPresentation.title}
        />
      )}

      {/* Processing Progress */}
      {isProcessing && status && (
        <ProcessingProgress
          presentationId={currentPresentation.id}
          parsingProgress={status.parsingProgress}
          renderingProgress={status.renderingProgress}
        />
      )}

      {/* Error Message */}
      {error && (
        <Card className="border-red-200 bg-red-50">
          <CardContent className="py-4">
            <p className="text-red-600">Error: {error}</p>
          </CardContent>
        </Card>
      )}

      {/* Slides Grid - Show when parsing is complete */}
      {(currentPresentation.processingStatus === 'PARSED' || 
        currentPresentation.processingStatus === 'RENDERING' ||
        currentPresentation.processingStatus === 'COMPLETED') && (
        <SlidesGrid
          key={refreshKey}
          presentationId={currentPresentation.id}
          onRefresh={() => {
            toast({
              title: "Refreshing slides",
              description: "Updating slide information..."
            });
          }}
          onGenerateFullStory={() => {
            toast({
              title: "Generating full story",
              description: "This feature is coming soon!"
            });
          }}
          processingComplete={currentPresentation.processingStatus === 'COMPLETED'}
        />
      )}
    </div>
  );
}