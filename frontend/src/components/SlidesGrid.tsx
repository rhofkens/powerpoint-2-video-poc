import { useState } from 'react';
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Play, Volume2, Video, RefreshCw, FileVideo } from "lucide-react";
import slide1 from "@/assets/slide-1.png";
import slide2 from "@/assets/slide-2.png";
import slide3 from "@/assets/slide-3.png";

interface Slide {
  id: number;
  thumbnail: string;
  extractedText: string;
  extractedNotes: string;
  conceptualAnalysis: string;
  narrative: string;
  hasAudio: boolean;
  hasVideo: boolean;
}

interface SlidesGridProps {
  onRefresh: () => void;
  onGenerateFullStory: () => void;
  processingComplete: boolean;
}

const mockSlides: Slide[] = [
  {
    id: 1,
    thumbnail: slide1,
    extractedText: "Q3 Financial Performance\n• Revenue: $2.4M (+15% YoY)\n• Profit Margin: 23.5%\n• Customer Growth: 12%\n• Market Share: 8.2%",
    extractedNotes: "Key talking points:\n- Emphasize the strong revenue growth\n- Highlight improved profit margins\n- Mention customer acquisition success\n- Compare favorably to competitors",
    conceptualAnalysis: "Objects detected: Bar charts, line graphs, financial tables, growth arrows\n\nKey concepts:\n- Financial performance visualization\n- Year-over-year comparison emphasis\n- Positive trend indicators\n- Market position context\n- Performance metrics dashboard",
    narrative: "Welcome to our Q3 financial review. I'm excited to share some outstanding results that demonstrate our company's continued growth trajectory. This quarter, we've achieved significant milestones across all key performance indicators, setting a strong foundation for the remainder of the year.",
    hasAudio: false,
    hasVideo: false
  },
  {
    id: 2,
    thumbnail: slide2,
    extractedText: "Marketing Campaign Results\n• Social Media Reach: 2.8M\n• Email Open Rate: 34%\n• Conversion Rate: 8.5%\n• ROI: 320%",
    extractedNotes: "Campaign highlights:\n- Record-breaking social media engagement\n- Email performance above industry average\n- Strong conversion metrics\n- Exceptional return on investment",
    conceptualAnalysis: "Objects detected: Infographics, timeline elements, social media icons, conversion funnels\n\nKey concepts:\n- Multi-channel marketing approach\n- Performance metrics visualization\n- Timeline progression\n- ROI success story\n- Digital marketing effectiveness",
    narrative: "Building on our financial success, let's examine how our strategic marketing initiatives have contributed to these remarkable results. Our integrated campaign approach has delivered exceptional performance across all channels, demonstrating the power of data-driven marketing decisions.",
    hasAudio: true,
    hasVideo: false
  },
  {
    id: 3,
    thumbnail: slide3,
    extractedText: "Future Outlook & Strategy\n• Q4 Revenue Target: $3.2M\n• New Product Launch: Q1 2024\n• Team Expansion: 25 new hires\n• Market Expansion: 3 new regions",
    extractedNotes: "Strategic priorities:\n- Aggressive Q4 revenue targets\n- Innovation pipeline ready\n- Scaling team capabilities\n- Geographic expansion plan",
    conceptualAnalysis: "Objects detected: Roadmap visualization, target indicators, team growth charts, geographic maps\n\nKey concepts:\n- Strategic planning framework\n- Growth trajectory projection\n- Resource allocation planning\n- Market expansion strategy\n- Future vision articulation",
    narrative: "As we look ahead, our strategic roadmap positions us for accelerated growth and market leadership. With ambitious yet achievable targets, innovative product development, and strategic team expansion, we're well-positioned to capitalize on emerging opportunities and deliver sustained value to our stakeholders.",
    hasAudio: true,
    hasVideo: true
  }
];

export function SlidesGrid({ onRefresh, onGenerateFullStory, processingComplete }: SlidesGridProps) {
  const [slides] = useState<Slide[]>(mockSlides);
  const [audioPlayerOpen, setAudioPlayerOpen] = useState<number | null>(null);
  const [videoPlayerOpen, setVideoPlayerOpen] = useState<number | null>(null);

  const generateAudio = (slideId: number) => {
    // Mock audio generation
    console.log(`Generating audio for slide ${slideId}`);
  };

  const generateVideo = (slideId: number) => {
    // Mock video generation
    console.log(`Generating video for slide ${slideId}`);
  };

  const playAudio = (slideId: number) => {
    setAudioPlayerOpen(slideId);
  };

  const playVideo = (slideId: number) => {
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
          <Button variant="outline" onClick={onRefresh}>
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

      <div className="space-y-4">
        {slides.map((slide) => (
          <Card key={slide.id} className="p-6">
            <div className="grid grid-cols-1 lg:grid-cols-6 gap-6 items-start">
              {/* Slide Thumbnail */}
              <div className="lg:col-span-2">
                <div className="relative group">
                  <img 
                    src={slide.thumbnail} 
                    alt={`Slide ${slide.id}`}
                    className="w-full h-auto rounded-lg shadow-md group-hover:shadow-lg transition-shadow"
                  />
                  <div className="absolute top-2 left-2">
                    <Badge variant="secondary">Slide {slide.id}</Badge>
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
                        <p className="text-xs line-clamp-4">{slide.extractedText}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Text - Slide {slide.id}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.extractedText}</pre>
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
                        <p className="text-xs line-clamp-4">{slide.extractedNotes}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Notes - Slide {slide.id}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.extractedNotes}</pre>
                        </ScrollArea>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Conceptual Analysis */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Conceptual Analysis</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start">
                        <p className="text-xs line-clamp-4">{slide.conceptualAnalysis}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Conceptual Analysis - Slide {slide.id}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.conceptualAnalysis}</pre>
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
                        <p className="text-xs line-clamp-4">{slide.narrative}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Slide Narrative - Slide {slide.id}</h4>
                        <ScrollArea className="max-h-96">
                          <p className="text-sm whitespace-pre-wrap">{slide.narrative}</p>
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
                  {!slide.hasAudio ? (
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
                  {!slide.hasVideo ? (
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

      {/* Audio Player Dialog */}
      <Dialog open={audioPlayerOpen !== null} onOpenChange={() => setAudioPlayerOpen(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Audio Player - Slide {audioPlayerOpen}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-muted/50 p-4 rounded-lg">
              <p className="text-sm mb-3">Playing narrative for slide {audioPlayerOpen}</p>
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
            <DialogTitle>Video Player - Slide {videoPlayerOpen}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <div className="bg-muted/50 p-4 rounded-lg">
              <p className="text-sm mb-3">Playing video with speech for slide {videoPlayerOpen}</p>
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