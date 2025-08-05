import { useState, useEffect } from 'react';
import { StyleSelector, type NarrativeStyle } from '@/components/StyleSelector';
import { FileUpload } from '@/components/FileUpload';
import { UploadHistory } from '@/components/UploadHistory';
import { PresentationDetail } from '@/components/PresentationDetail';
import { RendererStatus } from '@/components/RendererStatus';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';
import { usePresentationStore } from '@/store/presentationStore';
import { PresentationUploadResponse } from '@/types/presentation';

type AppState = 'style-selection' | 'file-upload' | 'presentation-detail';

const Index = () => {
  const [appState, setAppState] = useState<AppState>('file-upload'); // Start with upload instead of style selection
  const [selectedStyle, setSelectedStyle] = useState<NarrativeStyle | null>(null);
  const { toast } = useToast();
  const { currentPresentation, addPresentation, setCurrentPresentation } = usePresentationStore();

  // Watch for current presentation changes to switch to detail view
  useEffect(() => {
    if (currentPresentation && appState === 'file-upload') {
      setAppState('presentation-detail');
    }
  }, [currentPresentation, appState]);

  const handleStyleSelect = (style: NarrativeStyle) => {
    setSelectedStyle(style);
    setAppState('file-upload');
  };

  const handleFileUploaded = (response: PresentationUploadResponse) => {
    // Convert upload response to presentation object
    const presentation = {
      id: response.id,
      title: response.originalFilename.replace(/\.(pptx?|ppt)$/i, ''),
      originalFilename: response.originalFilename,
      fileSize: response.fileSize,
      processingStatus: response.status,
      createdAt: response.uploadTimestamp,
      updatedAt: response.uploadTimestamp
    };
    addPresentation(presentation);
    setCurrentPresentation(presentation);
    setAppState('presentation-detail');
  };

  const handleBackToUpload = () => {
    setAppState('file-upload');
    setCurrentPresentation(null);
  };

  const handleHealthCheck = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/health');
      if (response.ok) {
        const data = await response.json();
        toast({
          title: "Health Check Success",
          description: `Backend status: ${data.status}`,
        });
      } else {
        toast({
          title: "Health Check Failed",
          description: `Server responded with status: ${response.status}`,
          variant: "destructive",
        });
      }
    } catch (error) {
      toast({
        title: "Health Check Error",
        description: `Failed to connect to backend: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: "destructive",
      });
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-background via-background to-secondary/20">
      <div className="container mx-auto px-4 py-8 max-w-7xl">
        {/* Header */}
        <div className="text-center mb-12">
          <h1 className="text-4xl font-bold bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent mb-4">
            PowerPoint Analysis POC
          </h1>
          <p className="text-lg text-muted-foreground max-w-2xl mx-auto">
            Experimental platform for AI-powered presentation analysis, narrative generation, 
            and multimedia content creation
          </p>
          {selectedStyle && (
            <div className="mt-4 inline-flex items-center space-x-2 bg-primary/10 px-4 py-2 rounded-full">
              <span className="text-sm font-medium">Selected Style:</span>
              <span className="text-sm font-bold text-primary capitalize">{selectedStyle}</span>
            </div>
          )}
          {/* Temporary Health Check Button */}
          <div className="mt-4">
            <Button 
              onClick={handleHealthCheck}
              variant="outline"
              size="sm"
              className="bg-yellow-100 hover:bg-yellow-200 text-yellow-900 border-yellow-300"
            >
              Test Backend Health Check
            </Button>
          </div>
        </div>

        {/* Main Content */}
        <div className="space-y-8">
          {appState === 'style-selection' && (
            <StyleSelector
              selectedStyle={selectedStyle}
              onStyleSelect={handleStyleSelect}
            />
          )}

          {appState === 'file-upload' && (
            <div className="space-y-8">
              <div className="grid gap-8 lg:grid-cols-2">
                <div>
                  <FileUpload onFileUploaded={handleFileUploaded} />
                </div>
                <div>
                  <UploadHistory />
                </div>
              </div>
              <div className="max-w-2xl mx-auto">
                <RendererStatus />
              </div>
            </div>
          )}

          {appState === 'presentation-detail' && (
            <PresentationDetail onBack={handleBackToUpload} />
          )}
        </div>

        {/* Debug Info */}
        <div className="fixed bottom-4 right-4 text-xs text-muted-foreground bg-card border rounded-lg p-3 shadow-lg">
          <div>State: {appState}</div>
          <div>Style: {selectedStyle || 'none'}</div>
          <div>Current: {currentPresentation?.originalFilename || 'none'}</div>
        </div>
      </div>
    </div>
  );
};

export default Index;
