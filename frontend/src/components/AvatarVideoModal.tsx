import { useState, useEffect, useRef } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { useToast } from '@/hooks/use-toast';
import { apiService } from '@/services/api';
import { 
  AvatarVideo, 
  AvatarVideoRequest, 
  AvatarVideoResponse,
  AvatarVideoStatusDto,
  AvatarGenerationStatus 
} from '@/types/presentation';
import { 
  Video, 
  Loader2, 
  Play, 
  AlertCircle, 
  CheckCircle,
  RefreshCw,
  Clock,
  User
} from 'lucide-react';

interface AvatarVideoModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  slideId: string;
  presentationId: string;
  slideNumber: number;
  hasNarrative: boolean;
  hasSpeech: boolean;
  existingAvatarVideo?: AvatarVideo;
}

export function AvatarVideoModal({
  open,
  onOpenChange,
  slideId,
  presentationId,
  slideNumber,
  hasNarrative,
  hasSpeech,
  existingAvatarVideo
}: AvatarVideoModalProps) {
  const [avatarId, setAvatarId] = useState('Josh_public_3_20240313');
  const [backgroundColor, setBackgroundColor] = useState('#1E3A5F');
  const [isGenerating, setIsGenerating] = useState(false);
  const [currentVideo, setCurrentVideo] = useState<AvatarVideoResponse | null>(existingAvatarVideo || null);
  const [status, setStatus] = useState<AvatarGenerationStatus | null>(existingAvatarVideo?.status || null);
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [videoUrl, setVideoUrl] = useState<string | null>(existingAvatarVideo?.publishedUrl || existingAvatarVideo?.videoUrl || null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const progressIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const { toast } = useToast();

  // Load existing avatar videos when modal opens
  useEffect(() => {
    if (open && slideId) {
      loadExistingAvatarVideos();
    }
    
    // Cleanup on close
    if (!open) {
      stopPolling();
      stopProgressAnimation();
    }
  }, [open, slideId]);

  // Start progress animation when generating
  useEffect(() => {
    if (isGenerating && status === 'PROCESSING') {
      startProgressAnimation();
    } else {
      stopProgressAnimation();
    }
  }, [isGenerating, status]);

  const loadExistingAvatarVideos = async () => {
    try {
      const videos = await apiService.getSlideAvatarVideos(slideId);
      if (videos.length > 0) {
        // Get the most recent video
        const latestVideo = videos[videos.length - 1];
        setCurrentVideo(latestVideo);
        setStatus(latestVideo.status);
        // Prefer published URL from R2, fallback to HeyGen URL
        setVideoUrl(latestVideo.publishedUrl || latestVideo.videoUrl || null);
        
        // If video is still processing, start polling
        if (latestVideo.status === 'PENDING' || latestVideo.status === 'PROCESSING') {
          setIsGenerating(true);
          startPolling(latestVideo.id);
        }
      }
    } catch (error) {
      console.error('Failed to load existing avatar videos:', error);
    }
  };

  const startProgressAnimation = () => {
    stopProgressAnimation();
    let currentProgress = 0;
    
    progressIntervalRef.current = setInterval(() => {
      currentProgress += 0.67; // Slower increment to match 15 second cycle
      if (currentProgress > 100) {
        currentProgress = 0;
      }
      setProgress(currentProgress);
    }, 100); // Update every 100ms for smooth animation (15 seconds total)
  };

  const stopProgressAnimation = () => {
    if (progressIntervalRef.current) {
      clearInterval(progressIntervalRef.current);
      progressIntervalRef.current = null;
    }
  };

  const startPolling = (videoId: string) => {
    stopPolling();
    
    pollingIntervalRef.current = setInterval(async () => {
      try {
        const statusData = await apiService.getAvatarVideoStatus(videoId);
        setStatus(statusData.status);
        
        // Prefer published URL from R2, fallback to HeyGen URL
        if (statusData.publishedUrl) {
          setVideoUrl(statusData.publishedUrl);
        } else if (statusData.videoUrl) {
          setVideoUrl(statusData.videoUrl);
        }
        
        if (statusData.errorMessage) {
          setErrorMessage(statusData.errorMessage);
        }
        
        // Stop polling if reached terminal state
        if (statusData.status === 'COMPLETED' || statusData.status === 'FAILED') {
          stopPolling();
          setIsGenerating(false);
          
          if (statusData.status === 'COMPLETED') {
            toast({
              title: 'Avatar video generated!',
              description: 'Your avatar video has been successfully generated.',
            });
            
            // Auto-play the video
            if (videoRef.current && statusData.videoUrl) {
              videoRef.current.play().catch(console.error);
            }
            
            // R2 publishing is now handled automatically by the backend
          } else if (statusData.status === 'FAILED') {
            toast({
              title: 'Generation failed',
              description: statusData.errorMessage || 'Failed to generate avatar video',
              variant: 'destructive'
            });
          }
        }
      } catch (error) {
        console.error('Failed to get avatar video status:', error);
      }
    }, 15000); // Poll every 15 seconds
  };

  const stopPolling = () => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  };

  const handleGenerate = async () => {
    // Validation
    if (!hasNarrative) {
      toast({
        title: 'Narrative required',
        description: 'Please generate a narrative for this slide first.',
        variant: 'destructive'
      });
      return;
    }
    
    if (!hasSpeech) {
      toast({
        title: 'Speech required',
        description: 'Please generate speech for this slide first.',
        variant: 'destructive'
      });
      return;
    }
    
    setIsGenerating(true);
    setErrorMessage(null);
    setStatus('PENDING');
    setProgress(0);
    
    try {
      const request: AvatarVideoRequest = {
        presentationId,
        slideId,
        avatarId,
        backgroundColor,
        usePublishedAudio: true
      };
      
      const response = await apiService.generateAvatarVideo(request);
      setCurrentVideo(response);
      setStatus(response.status);
      
      toast({
        title: 'Generation started',
        description: 'Avatar video generation has been started. This may take up to 15 minutes.',
      });
      
      // Start polling for status updates
      startPolling(response.id);
      
    } catch (error) {
      setIsGenerating(false);
      setStatus('FAILED');
      setErrorMessage(error instanceof Error ? error.message : 'Failed to generate avatar video');
      
      toast({
        title: 'Generation failed',
        description: error instanceof Error ? error.message : 'Failed to start avatar video generation',
        variant: 'destructive'
      });
    }
  };

  const handleRegenerate = () => {
    setCurrentVideo(null);
    setStatus(null);
    setVideoUrl(null);
    setErrorMessage(null);
    handleGenerate();
  };

  const getStatusBadge = () => {
    if (!status) return null;
    
    switch (status) {
      case 'PENDING':
        return <Badge variant="secondary"><Clock className="h-3 w-3 mr-1" />Pending</Badge>;
      case 'PROCESSING':
        return <Badge variant="default"><Loader2 className="h-3 w-3 mr-1 animate-spin" />Processing</Badge>;
      case 'COMPLETED':
        return <Badge variant="success"><CheckCircle className="h-3 w-3 mr-1" />Completed</Badge>;
      case 'FAILED':
        return <Badge variant="destructive"><AlertCircle className="h-3 w-3 mr-1" />Failed</Badge>;
      default:
        return null;
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Generate Avatar Video - Slide {slideNumber}</DialogTitle>
          <DialogDescription>
            Create an AI avatar video for your slide narrative. Generation may take up to 15 minutes.
          </DialogDescription>
        </DialogHeader>
        
        <div className="space-y-4">
          {/* Configuration */}
          {!currentVideo && (
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="avatarId">Avatar ID</Label>
                <div className="flex items-center space-x-2">
                  <User className="h-4 w-4 text-muted-foreground" />
                  <Input
                    id="avatarId"
                    value={avatarId}
                    onChange={(e) => setAvatarId(e.target.value)}
                    placeholder="Enter avatar ID"
                    disabled={isGenerating}
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  Default: Josh_public_3_20240313
                </p>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="backgroundColor">Background Color</Label>
                <div className="flex items-center space-x-2">
                  <div 
                    className="w-8 h-8 rounded border"
                    style={{ backgroundColor }}
                  />
                  <Input
                    id="backgroundColor"
                    type="color"
                    value={backgroundColor}
                    onChange={(e) => setBackgroundColor(e.target.value)}
                    disabled={isGenerating}
                    className="flex-1"
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  Choose a background color for the video
                </p>
              </div>
            </div>
          )}
          
          {/* Status and Progress */}
          {(isGenerating || status) && (
            <Card>
              <CardContent className="pt-6">
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">Generation Status</span>
                    {getStatusBadge()}
                  </div>
                  
                  {status === 'PROCESSING' && (
                    <>
                      <Progress value={progress} className="h-2" />
                      <p className="text-xs text-muted-foreground text-center">
                        Processing... This may take up to 15 minutes
                      </p>
                    </>
                  )}
                  
                  {errorMessage && (
                    <Alert variant="destructive">
                      <AlertCircle className="h-4 w-4" />
                      <AlertDescription>{errorMessage}</AlertDescription>
                    </Alert>
                  )}
                </div>
              </CardContent>
            </Card>
          )}
          
          {/* Video Player */}
          {videoUrl && status === 'COMPLETED' && (
            <Card>
              <CardContent className="pt-6">
                <div className="space-y-2">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium">Generated Avatar Video</span>
                      {currentVideo?.publishedUrl && (
                        <Badge variant="outline" className="text-xs">R2 Storage</Badge>
                      )}
                    </div>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => videoRef.current?.play()}
                    >
                      <Play className="h-4 w-4 mr-1" />
                      Play
                    </Button>
                  </div>
                  <video
                    ref={videoRef}
                    controls
                    className="w-full rounded-lg"
                    src={videoUrl}
                  >
                    Your browser does not support the video element.
                  </video>
                </div>
              </CardContent>
            </Card>
          )}
          
          {/* Requirements Check */}
          {(!hasNarrative || !hasSpeech) && (
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                <strong>Requirements:</strong>
                <ul className="mt-2 ml-4 list-disc text-sm">
                  {!hasNarrative && <li>Generate a narrative for this slide</li>}
                  {!hasSpeech && <li>Generate speech for this slide</li>}
                </ul>
              </AlertDescription>
            </Alert>
          )}
          
          {/* Action Buttons */}
          <div className="flex justify-end space-x-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isGenerating && status === 'PROCESSING'}
            >
              Close
            </Button>
            
            {currentVideo && status === 'COMPLETED' ? (
              <Button onClick={handleRegenerate} disabled={isGenerating}>
                <RefreshCw className="h-4 w-4 mr-2" />
                Regenerate
              </Button>
            ) : (
              <Button 
                onClick={handleGenerate} 
                disabled={isGenerating || !hasNarrative || !hasSpeech}
              >
                {isGenerating ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Generating...
                  </>
                ) : (
                  <>
                    <Video className="h-4 w-4 mr-2" />
                    Generate Avatar Video
                  </>
                )}
              </Button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}