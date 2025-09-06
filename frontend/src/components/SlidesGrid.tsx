import { useState, useEffect } from 'react';
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Play, Volume2, Video, RefreshCw, FileVideo, ImageIcon, Brain, MessageCircle, Loader2, User, ChevronDown, Scissors, Sparkles } from "lucide-react";
import { apiService } from '@/services/api';
import { Slide, SlideAnalysis, SlideNarrative, AvatarVideo } from '@/types/presentation';
import { useToast } from "@/hooks/use-toast";
import { usePresentationStore } from '@/store/presentationStore';
import { useAnalysisStore } from '@/store/analysisStore';
import { SlideNarrativeWithTTS } from './SlideNarrativeWithTTS';
import { SlideAnalysisCard } from './SlideAnalysisCard';
import { AvatarVideoModal } from './AvatarVideoModal';

interface SlidesGridProps {
  presentationId: string;
  onRefresh: () => void;
  onGenerateFullStory: () => void;
  processingComplete: boolean;
}

export function SlidesGrid({ presentationId, onRefresh, onGenerateFullStory, processingComplete }: SlidesGridProps) {
  const [slides, setSlides] = useState<Slide[]>([]);
  const [videoPlayerOpen, setVideoPlayerOpen] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [analyzingSlides, setAnalyzingSlides] = useState<Set<string>>(new Set());
  const [generatingNarratives, setGeneratingNarratives] = useState<Set<string>>(new Set());
  const [enhancingNarratives, setEnhancingNarratives] = useState<Set<string>>(new Set());
  const [slideAvatarVideos, setSlideAvatarVideos] = useState<Map<string, AvatarVideo[]>>(new Map());
  const [avatarModalOpen, setAvatarModalOpen] = useState<string | null>(null);
  const [slidesWithSpeech, setSlidesWithSpeech] = useState<Set<string>>(new Set());
  
  // Modal states for detailed content view
  const [modalOpen, setModalOpen] = useState<{
    slideId: string;
    type: 'content' | 'notes' | 'analysis' | 'title' | 'narrative';
    autoGenerateSpeech?: boolean;
    shortenMode?: boolean;
  } | null>(null);
  const { toast } = useToast();
  const { currentSlides, setSlides: setStoreSlides } = usePresentationStore();
  const { narrativeStyle } = useAnalysisStore();

  useEffect(() => {
    if (presentationId) {
      fetchSlides();
    }
  }, [presentationId, processingComplete]);

  // Sync local state with store state when store updates
  useEffect(() => {
    if (currentSlides.length > 0) {
      setSlides(currentSlides);
    }
  }, [currentSlides]);

  const fetchSlides = async () => {
    try {
      setLoading(true);
      setError(null);
      const slidesData = await apiService.getSlides(presentationId);
      setSlides(slidesData);
      setStoreSlides(slidesData);
      
      // Check for speech on each slide
      const speechSet = new Set<string>();
      for (const slide of slidesData) {
        if (slide.audioPath || (await checkSlideHasSpeech(slide.id))) {
          speechSet.add(slide.id);
        }
      }
      setSlidesWithSpeech(speechSet);
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
  
  const checkSlideHasSpeech = async (slideId: string): Promise<boolean> => {
    try {
      const speechData = await apiService.getSlideSpeech(slideId);
      return speechData !== null && speechData !== undefined;
    } catch (error) {
      return false;
    }
  };

  const analyzeSlide = async (slideId: string) => {
    const slide = slides.find(s => s.id === slideId);
    const hasExistingAnalysis = !!slide?.slideAnalysis;
    
    setAnalyzingSlides(prev => new Set(prev).add(slideId));
    try {
      const analysis = await apiService.analyzeSlide(slideId, hasExistingAnalysis);
      toast({
        title: hasExistingAnalysis ? "Slide re-analyzed" : "Slide analyzed",
        description: hasExistingAnalysis 
          ? "AI has successfully re-analyzed the slide content" 
          : "AI has successfully analyzed the slide content"
      });
      // Refresh slides to show analysis results
      await fetchSlides();
    } catch (err) {
      console.error('Error analyzing slide:', err);
      toast({
        title: "Analysis failed",
        description: "Failed to analyze slide. Please try again.",
        variant: "destructive"
      });
    } finally {
      setAnalyzingSlides(prev => {
        const newSet = new Set(prev);
        newSet.delete(slideId);
        return newSet;
      });
    }
  };

  const generateNarrative = async (slideId: string) => {
    setGeneratingNarratives(prev => new Set(prev).add(slideId));
    try {
      const narrative = await apiService.generateNarrative(slideId, narrativeStyle);
      toast({
        title: "Narrative generated",
        description: "AI has successfully generated the slide narrative"
      });
      // Refresh slides to show narrative
      await fetchSlides();
    } catch (err) {
      console.error('Error generating narrative:', err);
      toast({
        title: "Generation failed",
        description: "Failed to generate narrative. Please try again.",
        variant: "destructive"
      });
    } finally {
      setGeneratingNarratives(prev => {
        const newSet = new Set(prev);
        newSet.delete(slideId);
        return newSet;
      });
    }
  };

  const enhanceNarrative = async (slideId: string) => {
    const slide = slides.find(s => s.id === slideId);
    if (!slide?.slideNarrative) {
      toast({
        title: "No Narrative Available",
        description: "Please generate a narrative for this slide first",
        variant: "destructive"
      });
      return;
    }

    setEnhancingNarratives(prev => new Set(prev).add(slideId));
    try {
      const enhancedNarrative = await apiService.enhanceSlideNarrative(slideId);
      toast({
        title: "Narrative enhanced",
        description: "Emotional markers have been added to the narrative"
      });
      // Refresh slides to show enhanced narrative
      await fetchSlides();
    } catch (err) {
      console.error('Error enhancing narrative:', err);
      toast({
        title: "Enhancement failed",
        description: "Failed to enhance narrative. Please try again.",
        variant: "destructive"
      });
    } finally {
      setEnhancingNarratives(prev => {
        const newSet = new Set(prev);
        newSet.delete(slideId);
        return newSet;
      });
    }
  };

  const generateAudio = (slideId: string) => {
    const slide = slides.find(s => s.id === slideId);
    if (!slide?.slideNarrative) {
      toast({
        title: "No Narrative Available",
        description: "Please generate a narrative for this slide first",
        variant: "destructive"
      });
      return;
    }
    // Open the narrative modal and trigger speech generation
    setModalOpen({ slideId, type: 'narrative', autoGenerateSpeech: true });
    // Mark slide as having speech after generation starts
    setTimeout(() => {
      setSlidesWithSpeech(prev => new Set(prev).add(slideId));
    }, 2000);
  };

  const generateVideo = (slideId: string) => {
    // TODO: Implement video generation
    console.log(`Generating video for slide ${slideId}`);
  };

  const loadSlideAvatarVideos = async (slideId: string) => {
    try {
      const videos = await apiService.getSlideAvatarVideos(slideId);
      setSlideAvatarVideos(prev => new Map(prev).set(slideId, videos));
      return videos;
    } catch (error) {
      console.error('Failed to load avatar videos:', error);
      return [];
    }
  };

  const openAvatarModal = async (slideId: string) => {
    // Load avatar videos for this slide
    await loadSlideAvatarVideos(slideId);
    setAvatarModalOpen(slideId);
  };

  const checkHasSpeech = (slide: Slide) => {
    // Check if slide has audio path or is tracked as having speech
    return !!slide.audioPath || slidesWithSpeech.has(slide.id);
  };

  const checkHasNarrative = (slide: Slide) => {
    // Check if slide has narrative
    return !!slide.slideNarrative;
  };

  const playAudio = (slideId: string) => {
    // Open the narrative modal to show speech playback and regeneration options
    setModalOpen({ slideId, type: 'narrative', autoGenerateSpeech: false });
  };

  const playVideo = (slideId: string) => {
    setVideoPlayerOpen(slideId);
  };

  const openModal = (slideId: string, type: 'content' | 'notes' | 'analysis' | 'title' | 'narrative', shortenMode: boolean = false) => {
    setModalOpen({ slideId, type, shortenMode });
  };

  const getModalContent = () => {
    if (!modalOpen) return null;
    
    const slide = slides.find(s => s.id === modalOpen.slideId);
    if (!slide) return null;

    switch (modalOpen.type) {
      case 'content':
        return {
          title: `Extracted Text - Slide ${slide.slideNumber}`,
          content: slide.content || 'No text content'
        };
      case 'notes':
        return {
          title: `Extracted Notes - Slide ${slide.slideNumber}`,
          content: slide.speakerNotes || 'No speaker notes available'
        };
      case 'analysis':
        if (!slide.slideAnalysis) {
          return {
            title: `AI Analysis - Slide ${slide.slideNumber}`,
            content: <p className="text-muted-foreground">No analysis available</p>
          };
        }
        return {
          title: `AI Analysis - Slide ${slide.slideNumber}`,
          content: <SlideAnalysisCard analysis={slide.slideAnalysis} slideNumber={slide.slideNumber} />
        };
      case 'title':
        return {
          title: `Slide Title - Slide ${slide.slideNumber}`,
          content: slide.title || 'No title available'
        };
      case 'narrative':
        if (slide.slideNarrative) {
          return {
            title: `Slide Narrative - Slide ${slide.slideNumber}`,
            content: <SlideNarrativeWithTTS 
              narrative={slide.slideNarrative} 
              slideNumber={slide.slideNumber}
              slideId={slide.id}
              presentationId={presentationId}
              narrativeStyle={narrativeStyle}
              autoGenerateSpeech={modalOpen?.autoGenerateSpeech}
              shortenMode={modalOpen?.shortenMode}
              onNarrativeShortened={() => {
                fetchSlides();
                setModalOpen(null);
              }}
            />
          };
        } else {
          return {
            title: `Slide Narrative - Slide ${slide.slideNumber}`,
            content: <p className="text-muted-foreground">{slide.generatedNarrative || 'Narrative not generated yet'}</p>
          };
        }
      default:
        return null;
    }
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
                  <div className="absolute top-2 left-2 flex gap-2 flex-wrap">
                    <Badge variant="secondary">Slide {slide.slideNumber}</Badge>
                    {slide.slideAnalysis?.slideType && (
                      <Badge 
                        variant={
                          slide.slideAnalysis.slideType === 'INTRO' ? 'default' :
                          slide.slideAnalysis.slideType === 'SEPARATOR' ? 'outline' :
                          slide.slideAnalysis.slideType === 'THANK_YOU' ? 'default' :
                          'secondary'
                        }
                      >
                        {slide.slideAnalysis.slideType}
                      </Badge>
                    )}
                    {slide.slideNarrative?.targetDurationSeconds && (
                      <Badge variant="outline">
                        {slide.slideNarrative.targetDurationSeconds}s
                      </Badge>
                    )}
                  </div>
                </div>
              </div>

              {/* Content Columns */}
              <div className="lg:col-span-4 grid grid-cols-1 md:grid-cols-5 gap-4 h-full items-end">
                {/* Extracted Text */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Extracted Text</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div 
                        className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start"
                        onDoubleClick={() => openModal(slide.id, 'content')}
                        title="Double-click to open in modal"
                      >
                        <p className="text-xs line-clamp-4">{slide.content || 'No text content'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Text - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.content || 'No text content'}</pre>
                        </ScrollArea>
                        <p className="text-xs text-muted-foreground mt-2">💡 Double-click to open in larger modal</p>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Extracted Notes */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Extracted Notes</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div 
                        className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start"
                        onDoubleClick={() => openModal(slide.id, 'notes')}
                        title="Double-click to open in modal"
                      >
                        <p className="text-xs line-clamp-4">{slide.speakerNotes || 'No speaker notes available'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Extracted Notes - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.speakerNotes || 'No speaker notes available'}</pre>
                        </ScrollArea>
                        <p className="text-xs text-muted-foreground mt-2">💡 Double-click to open in larger modal</p>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* AI Analysis */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">AI Analysis</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div 
                        className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start"
                        onDoubleClick={() => openModal(slide.id, 'analysis')}
                        title="Double-click to open in modal"
                      >
                        <p className="text-xs line-clamp-4">
                          {slide.slideAnalysis?.generalMessage || 'No analysis available'}
                        </p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">AI Analysis - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          {slide.slideAnalysis ? (
                            <div className="space-y-3">
                              <div>
                                <p className="font-medium text-sm mb-1">General Message:</p>
                                <p className="text-sm">{slide.slideAnalysis.generalMessage}</p>
                              </div>
                              {slide.slideAnalysis.keyPoints && (
                                <div>
                                  <p className="font-medium text-sm mb-1">Key Points:</p>
                                  <ul className="text-sm space-y-1">
                                    {JSON.parse(slide.slideAnalysis.keyPoints).map((point: string, i: number) => (
                                      <li key={i}>• {point}</li>
                                    ))}
                                  </ul>
                                </div>
                              )}
                              {slide.slideAnalysis.emphasisLevel && (
                                <div>
                                  <p className="font-medium text-sm mb-1">Emphasis Level:</p>
                                  <p className="text-sm">{slide.slideAnalysis.emphasisLevel}</p>
                                </div>
                              )}
                            </div>
                          ) : (
                            <p className="text-sm">No analysis available</p>
                          )}
                        </ScrollArea>
                        <p className="text-xs text-muted-foreground mt-2">💡 Double-click to open in larger modal</p>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Slide Title */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Slide Title</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div 
                        className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start"
                        onDoubleClick={() => openModal(slide.id, 'title')}
                        title="Double-click to open in modal"
                      >
                        <p className="text-xs line-clamp-4">{slide.title || 'No title available'}</p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Slide Title - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          <pre className="whitespace-pre-wrap text-sm">{slide.title || 'No title available'}</pre>
                        </ScrollArea>
                        <p className="text-xs text-muted-foreground mt-2">💡 Double-click to open in larger modal</p>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>

                {/* Slide Narrative */}
                <div className="flex flex-col h-full">
                  <h4 className="font-medium text-sm mb-2">Slide Narrative</h4>
                  <HoverCard>
                    <HoverCardTrigger asChild>
                      <div 
                        className="flex-1 p-3 bg-muted/50 rounded-md cursor-pointer hover:bg-muted transition-colors flex items-start"
                        onDoubleClick={() => openModal(slide.id, 'narrative')}
                        title="Double-click to open in modal"
                      >
                        <p className="text-xs line-clamp-4">
                          {slide.slideNarrative?.narrativeText || slide.generatedNarrative || 'Narrative not generated yet'}
                        </p>
                      </div>
                    </HoverCardTrigger>
                    <HoverCardContent className="w-80">
                      <div className="space-y-2">
                        <h4 className="text-sm font-semibold">Slide Narrative - Slide {slide.slideNumber}</h4>
                        <ScrollArea className="max-h-96">
                          {slide.slideNarrative ? (
                            <div className="space-y-3">
                              <div>
                                <p className="font-medium text-sm mb-1">Narrative:</p>
                                <p className="text-sm whitespace-pre-wrap">{slide.slideNarrative.narrativeText}</p>
                              </div>
                              {slide.slideNarrative.transitionPhrase && (
                                <div>
                                  <p className="font-medium text-sm mb-1">Transition:</p>
                                  <p className="text-sm italic">{slide.slideNarrative.transitionPhrase}</p>
                                </div>
                              )}
                              {slide.slideNarrative.durationSeconds && (
                                <div>
                                  <p className="font-medium text-sm mb-1">Duration:</p>
                                  <p className="text-sm">{slide.slideNarrative.durationSeconds} seconds</p>
                                </div>
                              )}
                              {slide.slideNarrative.targetDurationSeconds && (
                                <div>
                                  <p className="font-medium text-sm mb-1">Target Duration:</p>
                                  <p className="text-sm">{slide.slideNarrative.targetDurationSeconds} seconds</p>
                                </div>
                              )}
                            </div>
                          ) : (
                            <p className="text-sm whitespace-pre-wrap">
                              {slide.generatedNarrative || 'Narrative not generated yet'}
                            </p>
                          )}
                        </ScrollArea>
                        <p className="text-xs text-muted-foreground mt-2">💡 Double-click to open in larger modal</p>
                      </div>
                    </HoverCardContent>
                  </HoverCard>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="lg:col-span-6 flex flex-col sm:flex-row justify-center items-center space-y-2 sm:space-y-0 sm:space-x-3 pt-4 border-t">
                {/* AI Analysis */}
                <div className="flex space-x-2">
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => analyzeSlide(slide.id)}
                    disabled={analyzingSlides.has(slide.id)}
                  >
                    {analyzingSlides.has(slide.id) ? (
                      <>
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        Analyzing...
                      </>
                    ) : (
                      <>
                        <Brain className="h-4 w-4 mr-2" />
                        {slide.slideAnalysis ? 'Re-analyze' : 'Analyze Slide'}
                      </>
                    )}
                  </Button>
                  
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button 
                        variant="outline" 
                        size="sm"
                        disabled={generatingNarratives.has(slide.id) || enhancingNarratives.has(slide.id)}
                      >
                        {generatingNarratives.has(slide.id) ? (
                          <>
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                            Generating...
                          </>
                        ) : enhancingNarratives.has(slide.id) ? (
                          <>
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                            Enhancing...
                          </>
                        ) : (
                          <>
                            <MessageCircle className="h-4 w-4 mr-2" />
                            Narrative
                            <ChevronDown className="h-3 w-3 ml-1" />
                          </>
                        )}
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => generateNarrative(slide.id)}>
                        <MessageCircle className="h-4 w-4 mr-2" />
                        {slide.slideNarrative ? 'Regenerate Narrative' : 'Generate Narrative'}
                      </DropdownMenuItem>
                      {slide.slideNarrative && (
                        <>
                          <DropdownMenuItem onClick={() => enhanceNarrative(slide.id)}>
                            <Sparkles className="h-4 w-4 mr-2" />
                            Enhance with Emotions
                          </DropdownMenuItem>
                          <DropdownMenuItem onClick={() => openModal(slide.id, 'narrative', true)}>
                            <Scissors className="h-4 w-4 mr-2" />
                            Shorten Narrative
                          </DropdownMenuItem>
                        </>
                      )}
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>

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
                      title="Play speech and manage regeneration"
                    >
                      <Play className="h-4 w-4 mr-2" />
                      Play Audio
                    </Button>
                  )}
                </div>

                {/* Avatar Video Generation */}
                <div className="flex space-x-2">
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => openAvatarModal(slide.id)}
                    disabled={!checkHasNarrative(slide) || !checkHasSpeech(slide)}
                    title={
                      !checkHasNarrative(slide) 
                        ? "Generate narrative first" 
                        : !checkHasSpeech(slide)
                        ? "Generate speech first"
                        : "Generate avatar video"
                    }
                  >
                    <User className="h-4 w-4 mr-2" />
                    {slideAvatarVideos.get(slide.id)?.some(v => v.status === 'COMPLETED') 
                      ? 'Regenerate Avatar' 
                      : 'Generate Avatar'}
                  </Button>
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
                      Generate Video
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

      {/* Content Detail Modal */}
      <Dialog open={modalOpen !== null} onOpenChange={() => setModalOpen(null)}>
        <DialogContent className="max-w-4xl max-h-[80vh]">
          <DialogHeader>
            <DialogTitle>{getModalContent()?.title}</DialogTitle>
          </DialogHeader>
          <ScrollArea className="max-h-[60vh] pr-4">
            <div className="space-y-4">
              {typeof getModalContent()?.content === 'string' ? (
                <pre className="whitespace-pre-wrap text-sm leading-relaxed">{getModalContent()?.content}</pre>
              ) : (
                getModalContent()?.content
              )}
            </div>
          </ScrollArea>
        </DialogContent>
      </Dialog>

      {/* Avatar Video Modal */}
      {avatarModalOpen && (
        <AvatarVideoModal
          open={avatarModalOpen !== null}
          onOpenChange={(open) => {
            if (!open) {
              setAvatarModalOpen(null);
              // Reload avatar videos to update button state
              if (avatarModalOpen) {
                loadSlideAvatarVideos(avatarModalOpen);
              }
            }
          }}
          slideId={avatarModalOpen}
          presentationId={presentationId}
          slideNumber={slides.find(s => s.id === avatarModalOpen)?.slideNumber || 1}
          hasNarrative={checkHasNarrative(slides.find(s => s.id === avatarModalOpen) || {} as Slide)}
          hasSpeech={checkHasSpeech(slides.find(s => s.id === avatarModalOpen) || {} as Slide)}
          existingAvatarVideo={slideAvatarVideos.get(avatarModalOpen)?.[slideAvatarVideos.get(avatarModalOpen)!.length - 1]}
        />
      )}

    </div>
  );
}