import { useState, useEffect, useRef } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import { apiService } from '@/services/api';
import { IntroVideo, IntroVideoRequest, VideoGenerationStatus, ColorPalette } from '@/types/intro-video';
import { DeckAnalysis } from '@/types/presentation';
import { Video, Loader2, Sparkles, AlertCircle, CheckCircle, Palette } from 'lucide-react';

interface IntroVideoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  deckAnalysis: DeckAnalysis | null;
  onVideoGenerated?: (video: IntroVideo) => void;
}

export function IntroVideoModal({
  open,
  onOpenChange,
  presentationId,
  deckAnalysis,
  onVideoGenerated
}: IntroVideoModalProps) {
  const [isGenerating, setIsGenerating] = useState(false);
  const [currentVideo, setCurrentVideo] = useState<IntroVideo | null>(null);
  const [status, setStatus] = useState<VideoGenerationStatus | null>(null);
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [extractedColors, setExtractedColors] = useState<ColorPalette | null>(null);
  const { toast } = useToast();
  
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    if (open) {
      loadExistingIntroVideo();
      extractColors();
    }
    
    return () => {
      if (pollingIntervalRef.current) {
        clearInterval(pollingIntervalRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, presentationId]);

  const loadExistingIntroVideo = async () => {
    try {
      const video = await apiService.getIntroVideo(presentationId);
      if (video) {
        setCurrentVideo(video);
        setStatus(video.status);
        
        if (video.status === 'PROCESSING' || video.status === 'PENDING') {
          setIsGenerating(true);
          startPolling(video.id);
        }
      }
    } catch (error) {
      console.log('No existing intro video found');
    }
  };

  const extractColors = async () => {
    try {
      const colors = await apiService.extractSlideColors(presentationId);
      setExtractedColors(colors);
    } catch (error) {
      console.error('Failed to extract colors:', error);
    }
  };

  const handleGenerate = async () => {
    setIsGenerating(true);
    setErrorMessage(null);
    setProgress(0);
    
    try {
      const request: IntroVideoRequest = {
        presentationId,
        regenerate: currentVideo !== null
      };
      
      const response = await apiService.generateIntroVideo(request);
      setCurrentVideo(response);
      setStatus(response.status);
      
      // Start polling for status updates
      startPolling(response.id);
      
      // Animate progress
      animateProgress();
      
      toast({
        title: "Video Generation Started",
        description: "Your intro video is being generated. This may take a few minutes."
      });
      
    } catch (error) {
      setIsGenerating(false);
      setErrorMessage((error as Error).message || 'Failed to generate intro video');
      toast({
        title: "Generation Failed",
        description: (error as Error).message || 'Failed to start video generation',
        variant: "destructive"
      });
    }
  };

  const startPolling = (videoId: string) => {
    pollingIntervalRef.current = setInterval(async () => {
      try {
        const status = await apiService.getIntroVideoStatus(videoId);
        setStatus(status.status);
        setCurrentVideo(status);
        
        if (status.status === 'COMPLETED') {
          setIsGenerating(false);
          setProgress(100);
          stopPolling();
          
          toast({
            title: "Video Ready!",
            description: "Your intro video has been generated successfully."
          });
          
          if (onVideoGenerated) {
            onVideoGenerated(status);
          }
          
        } else if (status.status === 'FAILED') {
          setIsGenerating(false);
          setErrorMessage(status.errorMessage || 'Video generation failed');
          stopPolling();
          
          toast({
            title: "Generation Failed",
            description: status.errorMessage || 'Video generation failed',
            variant: "destructive"
          });
        }
      } catch (error) {
        console.error('Polling error:', error);
      }
    }, 20000);
  };

  const stopPolling = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  };

  const animateProgress = () => {
    let currentProgress = 0;
    const interval = setInterval(() => {
      currentProgress += Math.random() * 2;
      if (currentProgress >= 90) {
        clearInterval(interval);
        setProgress(90);
      } else {
        setProgress(currentProgress);
      }
    }, 1000);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="w-5 h-5" />
            Generate Intro Video
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Deck Analysis Summary - With Better Spacing */}
          {deckAnalysis && (
            <Card>
              <CardHeader className="pb-2 pt-3">
                <CardTitle className="text-sm font-medium">Presentation Analysis</CardTitle>
              </CardHeader>
              <CardContent className="p-4 pt-2 max-h-40 overflow-y-auto">
                <div className="space-y-4">
                  {/* Intent - First and prominent */}
                  {deckAnalysis.communicationIntent && (
                    <div>
                      <span className="text-muted-foreground text-xs font-medium uppercase tracking-wider">Intent</span>
                      <p className="text-sm mt-1 leading-normal">{deckAnalysis.communicationIntent}</p>
                    </div>
                  )}
                  
                  {/* Tone and Audience - Full width, stacked with clear separation */}
                  <div className="space-y-3 pt-2 border-t">
                    <div className="flex items-center justify-between">
                      <span className="text-muted-foreground text-sm">Tone:</span>
                      <Badge variant="secondary" className="text-xs">{deckAnalysis.tone || 'Professional'}</Badge>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-muted-foreground text-sm">Target Audience:</span>
                      <Badge variant="secondary" className="text-xs">{deckAnalysis.targetAudience || 'General'}</Badge>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Color Palette */}
          {extractedColors && extractedColors.dominantColors.length > 0 && (
            <div className="space-y-2">
              <Label className="flex items-center gap-2">
                <Palette className="w-4 h-4" />
                Extracted Color Palette
              </Label>
              <div className="flex gap-2">
                {extractedColors.dominantColors.slice(0, 5).map((color, index) => (
                  <div
                    key={index}
                    className="w-12 h-12 rounded border-2 border-gray-200"
                    style={{ backgroundColor: color }}
                    title={color}
                  />
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                These colors will be used to maintain visual consistency with your presentation
              </p>
            </div>
          )}

          {/* Generation Status */}
          {isGenerating && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Generating Video...</span>
                <Badge variant="secondary">
                  {status === 'PROCESSING' ? 'Processing' : 'Pending'}
                </Badge>
              </div>
              <Progress value={progress} className="h-2" />
              <p className="text-xs text-muted-foreground">
                This may take 1-6 minutes. You can close this dialog and check back later.
              </p>
            </div>
          )}

          {/* Error Message */}
          {errorMessage && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          )}

          {/* Video Preview */}
          {currentVideo?.publishedUrl && !isGenerating && (
            <div className="space-y-2">
              <Label>Generated Intro Video</Label>
              <video
                src={currentVideo.publishedUrl}
                controls
                className="w-full rounded-lg border"
                style={{ maxHeight: '300px' }}
              />
              <div className="flex items-center justify-between text-sm text-muted-foreground">
                <span>Duration: 8 seconds</span>
                <span>Resolution: {currentVideo.resolution || '1080p'}</span>
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isGenerating}
            >
              Close
            </Button>
            <Button
              onClick={handleGenerate}
              disabled={isGenerating || !deckAnalysis}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Generating...
                </>
              ) : currentVideo ? (
                'Regenerate Video'
              ) : (
                'Generate Intro Video'
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}