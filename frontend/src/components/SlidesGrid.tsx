import { useState, useEffect } from 'react';
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Skeleton } from "@/components/ui/skeleton";
import { Play, Volume2, Video, RefreshCw, FileVideo, ImageIcon } from "lucide-react";
import { apiService } from '@/services/api';
import { Slide } from '@/types/presentation';
import { useToast } from "@/hooks/use-toast";
import { usePresentationStore } from '@/store/presentationStore';

interface SlidesGridProps {
  presentationId: string;
  onRefresh: () => void;
  onGenerateFullStory: () => void;
  processingComplete: boolean;
}

export function SlidesGrid({ presentationId, onRefresh, onGenerateFullStory, processingComplete }: SlidesGridProps) {
  const [slides, setSlides] = useState<Slide[]>([]);
  const [audioPlayerOpen, setAudioPlayerOpen] = useState<string | null>(null);
  const [videoPlayerOpen, setVideoPlayerOpen] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();
  const { currentSlides, setSlides: setStoreSlides } = usePresentationStore();

  useEffect(() => {
    if (presentationId) {
      fetchSlides();
    }
  }, [presentationId, processingComplete]);

  const fetchSlides = async () => {
    try {
      setLoading(true);
      setError(null);
      const slidesData = await apiService.getSlides(presentationId);
      setSlides(slidesData);
      setStoreSlides(slidesData);
    } catch (err) {
      console.error('Error fetching slides:', err);
      setError('Failed to load slides');
      toast({
        title: "Error loading slides",
        description: "Failed to fetch slides from the server",
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  const generateAudio = (slideId: string) => {
    // TODO: Implement audio generation
    console.log(`Generating audio for slide ${slideId}`);
  };

  const generateVideo = (slideId: string) => {
    // TODO: Implement video generation
    console.log(`Generating video for slide ${slideId}`);
  };

  const playAudio = (slideId: string) => {
    setAudioPlayerOpen(slideId);
  };

  const playVideo = (slideId: string) => {
    setVideoPlayerOpen(slideId);
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">Presentation Analysis Results</h2>
          <p className="text-muted-foreground">
            Review extracted content and generate audio/video narratives
          </p>
        </div>
        <div className="flex space-x-2">
          <Button variant="outline" onClick={() => {
            onRefresh();
            fetchSlides();
          }}>
            <RefreshCw className="h-4 w-4 mr-2" />
            Refresh
          </Button>
          {processingComplete && (
            <Button variant="gradient" onClick={onGenerateFullStory}>
              <FileVideo className="h-4 w-4 mr-2" />
              Generate Full Story
            </Button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="space-y-4">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="p-6">
              <div className="grid grid-cols-1 lg:grid-cols-6 gap-6">
                <div className="lg:col-span-2">
                  <Skeleton className="w-full h-48 rounded-lg" />
                </div>
                <div className="lg:col-span-4 grid grid-cols-4 gap-4">
                  {[1, 2, 3, 4].map((j) => (
                    <Skeleton key={j} className="h-24" />
                  ))}
                </div>
              </div>
            </Card>
          ))}
        </div>
      ) : slides.length === 0 ? (
        <Card className="p-12">
          <div className="text-center space-y-4">
            <ImageIcon className="h-12 w-12 mx-auto text-muted-foreground" />
            <p className="text-muted-foreground">No slides found in this presentation</p>
            <Button onClick={() => {
              onRefresh();
              fetchSlides();
            }} variant="outline">
              <RefreshCw className="h-4 w-4 mr-2" />
              Refresh
            </Button>
          </div>
        </Card>
      ) : (
        <div className="space-y-4">
          {slides.map((slide) => (
          <Card key={slide.id} className="p-6">
            <div className="grid grid-cols-1 lg:grid-cols-6 gap-6 items-start">
              {/* Slide Thumbnail */}
              <div className="lg:col-span-2">
                <div className="relative group">
                  {slide.imagePath ? (
                    <img 
                      src={`http://localhost:8080/api/slides/${slide.id}/image`} 
                      alt={`Slide ${slide.slideNumber}`}
                      className="w-full h-auto rounded-lg shadow-md group-hover:shadow-lg transition-shadow"
                    />
                  ) : (
                    <div className="w-full h-48 bg-muted rounded-lg shadow-md flex items-center justify-center">
                      <div className="text-center space-y-2">
                        <ImageIcon className="h-8 w-8 mx-auto text-muted-foreground" />
                        <p className="text-sm text-muted-foreground">
                          {slide.imagePath?.includes('placeholder') ? 'Rendering in progress...' : 'No image available'}
                        </p>
                      </div>
                    </div>
                  )}
                  <div className="absolute top-2 left-2">
                    <Badge variant="secondary">Slide {slide.slideNumber}</Badge>
                  </div>
                </div>
              </div>

              {/* Content Columns */}
              <div className="lg:col-span-4 grid grid-cols-1 md:grid-cols-4 gap-4 h-full items-end">
                {/* Extracted Text */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Extracted Text</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start">
                        <p className="text-xs line-clamp-4">{slide.content || 'No text content'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Text - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.content || 'No text content'}</pre>
                        </ScrollArea>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Extracted Notes */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Extracted Notes</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start">
                        <p className="text-xs line-clamp-4">{slide.speakerNotes || 'No speaker notes available'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Notes - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.speakerNotes || 'No speaker notes available'}</pre>
                        </ScrollArea>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Slide Title */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Slide Title</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start">
                        <p className="text-xs line-clamp-4">{slide.title || 'Analysis pending...'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Slide Title - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.title || 'No title available'}</pre>
                        </ScrollArea>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Slide Narrative */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Slide Narrative</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start">
                        <p className="text-xs line-clamp-4">{slide.generatedNarrative || 'Narrative not generated yet'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Slide Narrative - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <p className="text-sm whitespace-pre-wrap">{slide.generatedNarrative || 'Narrative not generated yet'}</p>
                        </ScrollArea>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="lg:col-span-6 flex flex-col sm:flex-row justify-center items-center space-y-2 sm:space-y-0 sm:space-x-3 pt-4 border-t">
                {/* Audio Generation */}
                <div className="flex space-x-2">
                  {!slide.audioPath ? (
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => generateAudio(slide.id)}
                    >
                      <Volume2 className="h-4 w-4 mr-2" />
                      Generate Speech
                    </Button>
                  ) : (
                    <Button 
                      variant="success" 
                      size="sm"
                      onClick={() => playAudio(slide.id)}
                    >
                      <Play className="h-4 w-4 mr-2" />
                      Play Audio
                    </Button>
                  )}
                </div>

                {/* Video Generation */}
                <div className="flex space-x-2">
                  {!slide.videoPath ? (
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => generateVideo(slide.id)}
                    >
                      <Video className="h-4 w-4 mr-2" />
                      Generate Video & Speech
                    </Button>
                  ) : (
                    <Button 
                      variant="success" 
                      size="sm"
                      onClick={() => playVideo(slide.id)}
                    >
                      <Play className="h-4 w-4 mr-2" />
                      Play Video
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </Card>
          ))}
        </div>
      )}

      {/* Audio Player Dialog */}
      <Dialog open={audioPlayerOpen !== null} onOpenChange={() => setAudioPlayerOpen(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Audio Player - Slide {slides.find(s => s.id === audioPlayerOpen)?.slideNumber}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-muted/50 p-4 rounded-lg">
              <p className="text-sm mb-3">Playing narrative for slide {slides.find(s => s.id === audioPlayerOpen)?.slideNumber}</p>
              <audio controls className="w-full">
                <source src="/path/to/audio.mp3" type="audio/mpeg" />
                Your browser does not support the audio element.
              </audio>
            </div>
            <p className="text-xs text-muted-foreground">
              Mock audio player - In production, this would play the generated speech for the slide narrative.
            </p>
          </div>
        </DialogContent>
      </Dialog>

      {/* Video Player Dialog */}
      <Dialog open={videoPlayerOpen !== null} onOpenChange={() => setVideoPlayerOpen(null)}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>Video Player - Slide {slides.find(s => s.id === videoPlayerOpen)?.slideNumber}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-muted/50 p-4 rounded-lg">
              <p className="text-sm mb-3">Playing video with speech for slide {slides.find(s => s.id === videoPlayerOpen)?.slideNumber}</p>
              <video controls className="w-full rounded-md">
                <source src="/path/to/video.mp4" type="video/mp4" />
                Your browser does not support the video element.
              </video>
            </div>
            <p className="text-xs text-muted-foreground">
              Mock video player - In production, this would play the generated video with avatar speech for the slide narrative.
            </p>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}