import { useState, useEffect } from 'react';
import { StyleSelector, type NarrativeStyle } from '@/components/StyleSelector';
import { FileUpload } from '@/components/FileUpload';
import { ProcessingStatus } from '@/components/ProcessingStatus';
import { SlidesGrid } from '@/components/SlidesGrid';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';

type AppState = 'style-selection' | 'file-upload' | 'processing' | 'results';

const Index = () => {
  const [appState, setAppState] = useState<AppState>('style-selection');
  const [selectedStyle, setSelectedStyle] = useState<NarrativeStyle | null>(null);
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [processingProgress, setProcessingProgress] = useState(0);
  const [processingComplete, setProcessingComplete] = useState(false);
  const { toast } = useToast();

  // Simulate processing progress
  useEffect(() => {
    if (appState === 'processing') {
      const interval = setInterval(() => {
        setProcessingProgress(prev => {
          const newProgress = prev + Math.random() * 15;
          if (newProgress >= 100) {
            setProcessingComplete(true);
            setAppState('results');
            clearInterval(interval);
            return 100;
          }
          return newProgress;
        });
      }, 1500);

      return () => clearInterval(interval);
    }
  }, [appState]);

  const handleStyleSelect = (style: NarrativeStyle) => {
    setSelectedStyle(style);
    setAppState('file-upload');
  };

  const handleFileUploaded = (file: File) => {
    setUploadedFile(file);
    setAppState('processing');
    setProcessingProgress(0);
    setProcessingComplete(false);
  };

  const handleStopAndRestart = () => {
    setAppState('style-selection');
    setSelectedStyle(null);
    setUploadedFile(null);
    setProcessingProgress(0);
    setProcessingComplete(false);
  };

  const handleRefresh = () => {
    // In real implementation, this would refresh data from backend
    console.log('Refreshing slides data...');
  };

  const handleGenerateFullStory = () => {
    // In real implementation, this would trigger full story generation
    console.log('Generating full story...');
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
            <FileUpload onFileUploaded={handleFileUploaded} />
          )}

          {appState === 'processing' && uploadedFile && (
            <ProcessingStatus
              fileName={uploadedFile.name}
              onStopAndRestart={handleStopAndRestart}
              progress={processingProgress}
            />
          )}

          {appState === 'results' && (
            <SlidesGrid
              onRefresh={handleRefresh}
              onGenerateFullStory={handleGenerateFullStory}
              processingComplete={processingComplete}
            />
          )}
        </div>

        {/* Debug Info */}
        <div className="fixed bottom-4 right-4 text-xs text-muted-foreground bg-card border rounded-lg p-3 shadow-lg">
          <div>State: {appState}</div>
          <div>Style: {selectedStyle || 'none'}</div>
          <div>File: {uploadedFile?.name || 'none'}</div>
          {appState === 'processing' && <div>Progress: {Math.round(processingProgress)}%</div>}
        </div>
      </div>
    </div>
  );
};

export default Index;
