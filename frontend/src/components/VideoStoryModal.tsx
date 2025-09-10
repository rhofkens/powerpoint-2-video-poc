import { useState, useEffect, useCallback } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { useToast } from "@/hooks/use-toast";
import { apiService } from '@/services/api';
import { VideoStoryResponse, VideoStoryStatus, RenderJobStatus } from '@/types/video-story';
import { IntroVideo } from '@/types/intro-video';
import { 
  Video, 
  Loader2, 
  Play, 
  Download, 
  Copy, 
  CheckCircle, 
  XCircle,
  AlertCircle,
  Code,
  Eye,
  RefreshCw,
  Clipboard,
  Film
} from "lucide-react";

interface VideoStoryModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  presentationTitle: string;
  introVideo: IntroVideo | null;
}

export function VideoStoryModal({ 
  open, 
  onOpenChange, 
  presentationId, 
  presentationTitle,
  introVideo 
}: VideoStoryModalProps) {
  const [videoStory, setVideoStory] = useState<VideoStoryResponse | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [isRendering, setIsRendering] = useState(false);
  const [renderProgress, setRenderProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("preview");
  const [jsonCopied, setJsonCopied] = useState(false);
  const [videoUrl, setVideoUrl] = useState<string | null>(null);
  const [isLoadingVideo, setIsLoadingVideo] = useState(false);
  const { toast } = useToast();

  // Reset state when modal opens
  useEffect(() => {
    if (open) {
      setVideoStory(null);
      setIsCreating(false);
      setIsRendering(false);
      setRenderProgress(0);
      setError(null);
      setActiveTab("preview");
      setVideoUrl(null);
      setIsLoadingVideo(false);
      
      // Check for existing video story or create new one
      if (introVideo) {
        checkOrCreateVideoStory();
      }
    }
  }, [open, introVideo]);

  // Check for existing video URL when video story is loaded or updated
  useEffect(() => {
    if (videoStory?.videoStored && videoStory?.id && !videoUrl) {
      loadVideoUrl(videoStory.id);
    } else if (videoStory?.videoUrl && !videoUrl) {
      // If videoUrl is directly available in the response, use it
      setVideoUrl(videoStory.videoUrl);
      if (videoStory.videoStored) {
        setActiveTab("player");
      }
    }
  }, [videoStory]);

  const checkOrCreateVideoStory = async () => {
    if (!introVideo) return;
    
    setIsCreating(true);
    setError(null);
    
    try {
      // First, try to get existing video stories for this presentation
      const response = await apiService.axiosInstance.get<VideoStoryResponse[]>(
        `/video-stories/presentations/${presentationId}`
      ).catch(() => null);
      
      if (response?.data && response.data.length > 0) {
        // First try to find a completed story with video
        let existingStory = response.data.find(s => s.status === 'COMPLETED' && s.videoStored);
        
        // If no completed story with video, find any completed story
        if (!existingStory) {
          existingStory = response.data.find(s => s.status === 'COMPLETED');
        }
        
        // Otherwise use the most recent story
        if (!existingStory) {
          existingStory = response.data[0]; // Already sorted by createdAt desc
        }
        
        setVideoStory(existingStory);
        
        // If it's completed and has video URL, load it and switch to player tab
        if (existingStory.status === 'COMPLETED' && existingStory.videoUrl) {
          setVideoUrl(existingStory.videoUrl);
          setActiveTab('player'); // Auto-switch to player tab for completed videos
        } else if (existingStory.status === 'COMPLETED' && existingStory.videoStored) {
          // Try loading video URL from endpoint and switch to player tab
          await loadVideoUrl(existingStory.id, true); // Pass true to auto-switch tabs
        }
        
        toast({
          title: "Video Story Loaded",
          description: existingStory.status === 'COMPLETED' ? 
            "Found completed video story for this presentation." :
            "Found existing video story for this presentation."
        });
      } else {
        // No existing story, create a new one
        await handleCreateVideoStory();
      }
    } catch (err) {
      // If getting existing fails, create new one
      await handleCreateVideoStory();
    } finally {
      setIsCreating(false);
    }
  };

  const handleCreateVideoStory = async (force: boolean = false) => {
    if (!introVideo) {
      setError("No intro video available. Please generate an intro video first.");
      return;
    }

    setIsCreating(true);
    setError(null);

    try {
      const url = force ? `/video-stories?force=true` : '/video-stories';
      const response = await apiService.axiosInstance.post<VideoStoryResponse>(url, {
        presentationId,
        introVideoId: introVideo.id,
        title: `${presentationTitle} - Video Story`,
        description: `Automated video story for ${presentationTitle}`,
        providerType: 'shotstack'
      });

      setVideoStory(response.data);
      toast({
        title: force ? "Video Story Regenerated" : "Video Story Created",
        description: "Video composition has been generated successfully."
      });
    } catch (err) {
      const errorMessage = (err as Error).message || "Failed to create video story";
      setError(errorMessage);
      toast({
        title: "Creation Failed",
        description: errorMessage,
        variant: "destructive"
      });
    } finally {
      setIsCreating(false);
    }
  };

  const handleRenderVideo = async () => {
    if (!videoStory) return;

    setIsRendering(true);
    setError(null);
    setRenderProgress(0);

    try {
      // Start rendering
      const renderResponse = await apiService.renderVideoStory(videoStory.id);
      setVideoStory(renderResponse);

      toast({
        title: "Rendering Started",
        description: "Your video is being rendered. This may take a few minutes."
      });

      // Poll for status
      await apiService.pollVideoStoryRenderStatus(
        videoStory.id,
        (response) => {
          setVideoStory(response);
          if (response.progress) {
            setRenderProgress(response.progress);
          }
        },
        5000 // Poll every 5 seconds
      );

      // Rendering complete
      const finalStatus = await apiService.getVideoStory(videoStory.id);
      setVideoStory(finalStatus);
      
      // Load video URL and switch to player tab
      await loadVideoUrl(videoStory.id);
      
      toast({
        title: "Rendering Complete",
        description: "Your video has been rendered successfully!"
      });
    } catch (err) {
      const errorMessage = (err as Error).message || "Failed to render video";
      setError(errorMessage);
      toast({
        title: "Rendering Failed",
        description: errorMessage,
        variant: "destructive"
      });
    } finally {
      setIsRendering(false);
    }
  };

  const copyVideoUrl = () => {
    if (videoStory?.videoUrl) {
      navigator.clipboard.writeText(videoStory.videoUrl);
      toast({
        title: "URL Copied",
        description: "Video URL has been copied to clipboard."
      });
    }
  };

  const copyCompositionJson = () => {
    if (videoStory?.compositionData) {
      const formattedJson = formatComposition(videoStory.compositionData);
      navigator.clipboard.writeText(formattedJson);
      setJsonCopied(true);
      setTimeout(() => setJsonCopied(false), 2000);
      toast({
        title: "JSON Copied",
        description: "Composition JSON has been copied to clipboard."
      });
    }
  };

  const loadVideoUrl = async (storyId: string, autoSwitch: boolean = false) => {
    setIsLoadingVideo(true);
    try {
      const response = await apiService.axiosInstance.get(`/video-stories/${storyId}/video-url`);
      if (response.data?.videoUrl) {
        setVideoUrl(response.data.videoUrl);
        // Switch to video player tab if requested
        if (autoSwitch) {
          setActiveTab("player");
        }
      }
    } catch (err) {
      console.error('Failed to load video URL:', err);
    } finally {
      setIsLoadingVideo(false);
    }
  };

  const handleVideoDownload = () => {
    if (videoUrl) {
      const link = document.createElement('a');
      link.href = videoUrl;
      link.download = `${presentationTitle}-video-story.mp4`;
      link.click();
    }
  };

  const getStatusBadge = () => {
    if (!videoStory) return null;
    
    const statusColors: Record<string, string> = {
      'DRAFT': 'bg-gray-500',
      'READY': 'bg-blue-500',
      'RENDERING': 'bg-yellow-500',
      'COMPLETED': 'bg-green-500',
      'FAILED': 'bg-red-500'
    };

    return (
      <Badge className={`${statusColors[videoStory.status] || 'bg-gray-500'} text-white`}>
        {videoStory.status}
      </Badge>
    );
  };

  const getRenderStatusIcon = () => {
    if (!videoStory?.renderStatus) return null;

    switch (videoStory.renderStatus) {
      case RenderJobStatus.COMPLETED:
        return <CheckCircle className="h-5 w-5 text-green-500" />;
      case RenderJobStatus.FAILED:
      case RenderJobStatus.CANCELLED:
        return <XCircle className="h-5 w-5 text-red-500" />;
      case RenderJobStatus.PROCESSING:
      case RenderJobStatus.QUEUED:
        return <Loader2 className="h-5 w-5 animate-spin" />;
      default:
        return null;
    }
  };

  const formatComposition = (compositionData: string) => {
    try {
      return JSON.stringify(JSON.parse(compositionData), null, 2);
    } catch {
      return compositionData;
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl h-[90vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Video className="h-5 w-5" />
            Video Story Generator
          </DialogTitle>
          <DialogDescription>
            Create and render a video story from your presentation
          </DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-hidden">
          {!introVideo ? (
            <Alert>
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>
                Please generate an intro video first before creating a video story.
              </AlertDescription>
            </Alert>
          ) : isCreating ? (
            <div className="flex flex-col items-center justify-center h-full gap-4">
              <Loader2 className="h-8 w-8 animate-spin" />
              <p className="text-muted-foreground">Creating video composition...</p>
            </div>
          ) : videoStory ? (
            <div className="flex flex-col h-full gap-4">
              {/* Status Bar */}
              <div className="flex items-center justify-between p-3 bg-muted rounded-lg">
                <div className="flex items-center gap-3">
                  {getStatusBadge()}
                  {getRenderStatusIcon()}
                  {videoStory.renderStatus && (
                    <span className="text-sm text-muted-foreground">
                      Render: {videoStory.renderStatus}
                    </span>
                  )}
                </div>
                {isRendering && (
                  <div className="flex items-center gap-2">
                    <Progress value={renderProgress} className="w-32 h-2" />
                    <span className="text-sm text-muted-foreground">{renderProgress}%</span>
                  </div>
                )}
              </div>

              {/* Error Alert */}
              {error && (
                <Alert variant="destructive">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              {/* Content Tabs */}
              <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 flex flex-col">
                <TabsList className="grid w-full grid-cols-3">
                  <TabsTrigger value="preview" className="flex items-center gap-2">
                    <Eye className="h-4 w-4" />
                    Preview
                  </TabsTrigger>
                  <TabsTrigger value="composition" className="flex items-center gap-2">
                    <Code className="h-4 w-4" />
                    Composition JSON
                  </TabsTrigger>
                  <TabsTrigger 
                    value="player" 
                    className="flex items-center gap-2"
                    disabled={!videoUrl && !videoStory?.videoStored}
                  >
                    <Film className="h-4 w-4" />
                    Video Player
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="preview" className="flex-1 mt-4">
                  <div className="space-y-4">
                    <div className="p-4 border rounded-lg">
                      <h3 className="font-semibold mb-2">Video Details</h3>
                      <dl className="space-y-2 text-sm">
                        <div className="flex justify-between">
                          <dt className="text-muted-foreground">Title:</dt>
                          <dd>{videoStory.title}</dd>
                        </div>
                        <div className="flex justify-between">
                          <dt className="text-muted-foreground">Duration:</dt>
                          <dd>8 seconds (intro only)</dd>
                        </div>
                        <div className="flex justify-between">
                          <dt className="text-muted-foreground">Format:</dt>
                          <dd>MP4, 1920x1080, 25fps</dd>
                        </div>
                      </dl>
                    </div>

                    {videoStory.videoUrl && (
                      <div className="p-4 border rounded-lg bg-green-50 dark:bg-green-900/20">
                        <h3 className="font-semibold mb-2 text-green-800 dark:text-green-200">
                          Video Ready!
                        </h3>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => window.open(videoStory.videoUrl, '_blank')}
                          >
                            <Play className="h-4 w-4 mr-2" />
                            Play Video
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => window.open(videoStory.videoUrl, '_blank')}
                          >
                            <Download className="h-4 w-4 mr-2" />
                            Download
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={copyVideoUrl}
                          >
                            <Copy className="h-4 w-4 mr-2" />
                            Copy URL
                          </Button>
                        </div>
                      </div>
                    )}

                    <div className="p-4 border rounded-lg">
                      <h3 className="font-semibold mb-2">Timeline Components</h3>
                      <ul className="space-y-1 text-sm text-muted-foreground">
                        <li>• Intro video (8 seconds)</li>
                        <li>• Title overlay with fade transitions</li>
                        <li>• Subtitle/audience text</li>
                        <li>• Lower thirds animations</li>
                        <li>• Luma matte transition at end</li>
                      </ul>
                    </div>
                  </div>
                </TabsContent>

                <TabsContent value="composition" className="flex-1 mt-4 overflow-hidden">
                  <div className="h-full flex flex-col gap-2">
                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={copyCompositionJson}
                        disabled={!videoStory?.compositionData}
                      >
                        {jsonCopied ? (
                          <>
                            <CheckCircle className="h-4 w-4 mr-2" />
                            Copied!
                          </>
                        ) : (
                          <>
                            <Clipboard className="h-4 w-4 mr-2" />
                            Copy JSON
                          </>
                        )}
                      </Button>
                    </div>
                    <div className="flex-1 overflow-auto rounded-lg border p-4 bg-muted/30">
                      <pre className="text-xs font-mono">
                        {videoStory.compositionData ? 
                          formatComposition(videoStory.compositionData) : 
                          'No composition data available'}
                      </pre>
                    </div>
                  </div>
                </TabsContent>

                <TabsContent value="player" className="flex-1 mt-4 overflow-hidden">
                  <div className="h-full flex items-center justify-center">
                    {isLoadingVideo ? (
                      <div className="flex flex-col items-center gap-4">
                        <Loader2 className="h-8 w-8 animate-spin" />
                        <p className="text-muted-foreground">Loading video...</p>
                      </div>
                    ) : videoUrl ? (
                      <div className="w-full max-w-3xl space-y-4">
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium">Rendered Video</span>
                            {videoStory?.videoStored && (
                              <Badge variant="outline" className="text-xs">R2 Storage</Badge>
                            )}
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={handleVideoDownload}
                          >
                            <Download className="h-4 w-4 mr-2" />
                            Download
                          </Button>
                        </div>
                        <video
                          controls
                          className="w-full rounded-lg bg-black"
                          src={videoUrl}
                        >
                          Your browser does not support the video element.
                        </video>
                      </div>
                    ) : (
                      <p className="text-muted-foreground">No video available</p>
                    )}
                  </div>
                </TabsContent>
              </Tabs>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <p className="text-muted-foreground">Click create to generate video composition</p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          {videoStory && (
            <>
              <Button 
                variant="outline" 
                onClick={() => handleCreateVideoStory(true)} 
                disabled={isCreating || isRendering}
              >
                {isCreating ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Regenerating...
                  </>
                ) : (
                  <>
                    <RefreshCw className="h-4 w-4 mr-2" />
                    Regenerate
                  </>
                )}
              </Button>
              {videoStory.status === VideoStoryStatus.READY && !isRendering && (
                <Button onClick={handleRenderVideo} disabled={isRendering}>
                  {isRendering ? (
                    <>
                      <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      Rendering...
                    </>
                  ) : (
                    <>
                      <Play className="h-4 w-4 mr-2" />
                      Render Video
                    </>
                  )}
                </Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}