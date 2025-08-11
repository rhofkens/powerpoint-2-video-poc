import { useState, useEffect, useRef } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Progress } from "@/components/ui/progress";
import { SlideNarrative, EmotionIndicator, AvatarInstructions, SpeechMarkers } from "@/types/presentation";
import { SlideSpeech } from "@/types/tts";
import { apiService } from "@/services/api";
import { usePresentationStore } from "@/store/presentationStore";
import { 
  MessageCircle, 
  Timer, 
  Smile, 
  Volume2, 
  PauseCircle, 
  Mic, 
  Loader2, 
  Play,
  AlertCircle,
  CheckCircle,
  Pause
} from "lucide-react";
import { useToast } from "@/hooks/use-toast";

interface SlideNarrativeWithTTSProps {
  narrative: SlideNarrative;
  slideNumber: number;
  slideId: string;
  presentationId: string;
  narrativeStyle?: string;
  autoGenerateSpeech?: boolean;
}

export function SlideNarrativeWithTTS({ 
  narrative, 
  slideNumber, 
  slideId,
  presentationId,
  narrativeStyle = "business",
  autoGenerateSpeech = false
}: SlideNarrativeWithTTSProps) {
  const [isGeneratingSpeech, setIsGeneratingSpeech] = useState(false);
  const [speech, setSpeech] = useState<SlideSpeech | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const { toast } = useToast();
  const { updateSlide } = usePresentationStore();

  // Parse JSON fields
  const emotionIndicators = JSON.parse(narrative.emotionIndicators || '[]') as EmotionIndicator[];
  const avatarInstructions = JSON.parse(narrative.avatarInstructions || '{}') as AvatarInstructions;
  const speechMarkers = JSON.parse(narrative.speechMarkers || '{}') as SpeechMarkers;
  const emphasisWords = JSON.parse(narrative.emphasisWords || '[]') as string[];

  // Load existing speech data on mount
  useEffect(() => {
    loadSpeechData();
  }, [slideId]);

  // Auto-generate speech if requested and not already generated
  useEffect(() => {
    if (autoGenerateSpeech && !speech && !isGeneratingSpeech) {
      handleGenerateSpeech();
    }
  }, [autoGenerateSpeech, speech]);

  // Clean up audio element on unmount
  useEffect(() => {
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.src = '';
      }
    };
  }, []);

  const loadSpeechData = async () => {
    try {
      const speechData = await apiService.getSlideSpeech(slideId);
      if (speechData) {
        setSpeech(speechData);
      }
    } catch (error) {
      console.error("Failed to load speech data:", error);
    }
  };

  const handleGenerateSpeech = async (forceRegenerate: boolean = false) => {
    setIsGeneratingSpeech(true);
    try {
      const speechData = await apiService.generateSpeech(slideId, {
        presentationId,
        slideId,
        narrativeStyle,
        forceRegenerate
      });
      
      setSpeech(speechData);
      toast({
        title: "Speech Generated",
        description: `Speech has been generated successfully (${formatDuration(speechData.durationSeconds)})`,
      });
      
      // Update the slide in the store with the new narrative duration
      // This will trigger a re-fetch of the slide data to get the updated narrative
      try {
        const updatedSlide = await apiService.getSlide(slideId);
        updateSlide(slideId, updatedSlide);
      } catch (error) {
        console.error("Failed to fetch updated slide data:", error);
      }
      
      // Auto-play the generated speech
      setTimeout(() => {
        initializeAndPlayAudio();
      }, 500); // Small delay to ensure audio URL is ready
    } catch (error) {
      console.error("Failed to generate speech:", error);
      toast({
        title: "Generation Failed",
        description: error instanceof Error ? error.message : "Failed to generate speech",
        variant: "destructive",
      });
    } finally {
      setIsGeneratingSpeech(false);
    }
  };

  const initializeAndPlayAudio = () => {
    if (!speech) return;
    
    const audio = new Audio(apiService.getSpeechAudioUrl(speech.id));
    
    audio.addEventListener('timeupdate', () => {
      setCurrentTime(audio.currentTime);
    });
    
    audio.addEventListener('loadedmetadata', () => {
      setDuration(audio.duration);
      // Start playing once metadata is loaded
      audio.play().then(() => {
        setIsPlaying(true);
      }).catch((error) => {
        console.error("Auto-play failed:", error);
        // Silent fail for auto-play, user can manually click play
      });
    });
    
    audio.addEventListener('ended', () => {
      setIsPlaying(false);
      setCurrentTime(0);
    });
    
    audio.addEventListener('error', (e) => {
      console.error("Audio playback error:", e);
      toast({
        title: "Playback Error",
        description: "Failed to play audio",
        variant: "destructive",
      });
      setIsPlaying(false);
    });
    
    audioRef.current = audio;
  };

  const handlePlayPause = () => {
    if (!speech) return;
    
    if (!audioRef.current) {
      const audio = new Audio(apiService.getSpeechAudioUrl(speech.id));
      
      audio.addEventListener('timeupdate', () => {
        setCurrentTime(audio.currentTime);
      });
      
      audio.addEventListener('loadedmetadata', () => {
        setDuration(audio.duration);
      });
      
      audio.addEventListener('ended', () => {
        setIsPlaying(false);
        setCurrentTime(0);
      });
      
      audio.addEventListener('error', (e) => {
        console.error("Audio playback error:", e);
        toast({
          title: "Playback Error",
          description: "Failed to play audio",
          variant: "destructive",
        });
        setIsPlaying(false);
      });
      
      audioRef.current = audio;
    }
    
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play();
      setIsPlaying(true);
    }
  };

  const getEmotionIcon = (emotion: string) => {
    return <Smile className="h-4 w-4" />;
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds) return 'N/A';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <MessageCircle className="h-5 w-5 text-primary" />
            <CardTitle>Slide {slideNumber} Narrative</CardTitle>
          </div>
          <div className="flex items-center gap-2">
            <Timer className="h-4 w-4" />
            <span className="text-sm text-muted-foreground">
              {formatDuration(speech?.durationSeconds || narrative.durationSeconds)}
            </span>
            {narrative.isActive && (
              <Badge variant="default">Active</Badge>
            )}
          </div>
        </div>
        <CardDescription>
          AI-generated narrative for video presentation
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* TTS Generation Section */}
        <div className="space-y-3 p-4 bg-muted/30 rounded-lg">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <Mic className="h-4 w-4" />
              Text-to-Speech
            </h4>
            {speech && (
              <Badge variant="outline" className="text-xs">
                <CheckCircle className="h-3 w-3 mr-1" />
                Generated
              </Badge>
            )}
          </div>
          
          {speech ? (
            <div className="space-y-3">
              <div className="flex items-center justify-between text-sm">
                <div className="space-y-1">
                  <p className="text-xs text-muted-foreground">Voice Style: {speech.voiceStyle}</p>
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => handleGenerateSpeech(true)}
                  disabled={isGeneratingSpeech}
                >
                  {isGeneratingSpeech ? (
                    <>
                      <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      Regenerating...
                    </>
                  ) : (
                    <>
                      <Mic className="h-4 w-4 mr-2" />
                      Regenerate
                    </>
                  )}
                </Button>
              </div>
              
              {/* Audio Player */}
              <div className="space-y-2 p-3 bg-background border rounded-lg">
                <div className="flex items-center gap-3">
                  <Button
                    size="sm"
                    variant="outline"
                    className="h-10 w-10 p-0"
                    onClick={handlePlayPause}
                  >
                    {isPlaying ? (
                      <Pause className="h-4 w-4" />
                    ) : (
                      <Play className="h-4 w-4" />
                    )}
                  </Button>
                  
                  <div className="flex-1 space-y-1">
                    <Progress 
                      value={duration > 0 ? (currentTime / duration) * 100 : 0} 
                      className="h-2"
                    />
                    <div className="flex justify-between text-xs text-muted-foreground">
                      <span>{formatTime(currentTime)}</span>
                      <span>{formatTime(duration || speech.durationSeconds || 0)}</span>
                    </div>
                  </div>
                </div>
              </div>
              
              {speech.timestamps && speech.timestamps.length > 0 && (
                <Alert className="py-2">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription className="text-xs">
                    Speech includes {speech.timestamps.length} character-level timestamps for precise synchronization
                  </AlertDescription>
                </Alert>
              )}
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">
                No speech generated yet. Click below to convert the narrative to speech.
              </p>
              <Button
                onClick={() => handleGenerateSpeech()}
                disabled={isGeneratingSpeech}
                className="w-full"
              >
                {isGeneratingSpeech ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Generating Speech...
                  </>
                ) : (
                  <>
                    <Mic className="h-4 w-4 mr-2" />
                    Generate Speech
                  </>
                )}
              </Button>
            </div>
          )}
        </div>

        {/* Narrative Text */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Narrative Text</h4>
          <ScrollArea className="h-48 w-full rounded-md border p-4">
            <p className="text-sm whitespace-pre-wrap">
              {narrative.narrativeText}
            </p>
          </ScrollArea>
        </div>

        {/* Avatar Instructions */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium flex items-center gap-2">
            <Smile className="h-4 w-4" />
            Avatar Instructions
          </h4>
          <div className="grid grid-cols-3 gap-2 text-sm">
            <div className="p-2 bg-muted/50 rounded-md">
              <p className="text-xs font-medium">Primary Emotion</p>
              <p className="text-xs text-muted-foreground capitalize">
                {avatarInstructions.primaryEmotion || 'Neutral'}
              </p>
            </div>
            <div className="p-2 bg-muted/50 rounded-md">
              <p className="text-xs font-medium">Gesture Intensity</p>
              <p className="text-xs text-muted-foreground">
                {avatarInstructions.gestureIntensity || 'Medium'}
              </p>
            </div>
            <div className="p-2 bg-muted/50 rounded-md">
              <p className="text-xs font-medium">Facial Expression</p>
              <p className="text-xs text-muted-foreground">
                {avatarInstructions.facialExpression || 'Professional'}
              </p>
            </div>
          </div>
        </div>

        {/* Speech Markers */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium flex items-center gap-2">
            <Volume2 className="h-4 w-4" />
            Speech Delivery
          </h4>
          <div className="space-y-2">
            {speechMarkers.pauses && speechMarkers.pauses.length > 0 && (
              <div className="flex items-center gap-2 text-sm">
                <PauseCircle className="h-4 w-4" />
                <span className="text-muted-foreground">
                  {speechMarkers.pauses.length} pauses planned
                </span>
              </div>
            )}
            {emphasisWords.length > 0 && (
              <div className="space-y-1">
                <p className="text-xs font-medium">Emphasis Words:</p>
                <div className="flex flex-wrap gap-1">
                  {emphasisWords.map((word, index) => (
                    <Badge key={index} variant="secondary" className="text-xs">
                      {word}
                    </Badge>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Transition Phrase */}
        {narrative.transitionPhrase && (
          <div className="space-y-2 p-3 bg-primary/5 border-l-2 border-primary/50 rounded-md">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
              Transition to Next Slide
              {speech && speech.transitionSkippedReason && (
                <Badge variant="secondary" className="text-xs ml-2">
                  Skipped: {speech.transitionSkippedReason}
                </Badge>
              )}
              {speech && speech.transitionIncluded && (
                <Badge variant="default" className="text-xs ml-2">
                  Included in Speech
                </Badge>
              )}
            </h4>
            <p className="text-sm italic">
              "{narrative.transitionPhrase}"
            </p>
          </div>
        )}

        {/* Emotion Indicators */}
        {emotionIndicators.length > 0 && (
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Emotion Timeline</h4>
            <div className="space-y-1">
              {emotionIndicators.map((indicator, index) => (
                <div key={index} className="flex items-center gap-2 text-xs">
                  {getEmotionIcon(indicator.emotion)}
                  <span className="text-muted-foreground">
                    Words {indicator.startWord}-{indicator.endWord}: {indicator.emotion}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Metadata */}
        <div className="pt-4 border-t">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>Version: {narrative.version}</span>
            <span>Model: {narrative.modelUsed}</span>
            {speech && (
              <span>TTS Model: {speech.modelUsed}</span>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}