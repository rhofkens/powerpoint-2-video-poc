import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { SlideNarrative, EmotionIndicator, AvatarInstructions, SpeechMarkers } from "@/types/presentation";
import { MessageCircle, Timer, Smile, Volume2, PauseCircle } from "lucide-react";

interface SlideNarrativeCardProps {
  narrative: SlideNarrative;
  slideNumber: number;
}

export function SlideNarrativeCard({ narrative, slideNumber }: SlideNarrativeCardProps) {
  // Parse JSON fields
  const emotionIndicators = JSON.parse(narrative.emotionIndicators || '[]') as EmotionIndicator[];
  const avatarInstructions = JSON.parse(narrative.avatarInstructions || '{}') as AvatarInstructions;
  const speechMarkers = JSON.parse(narrative.speechMarkers || '{}') as SpeechMarkers;
  const emphasisWords = JSON.parse(narrative.emphasisWords || '[]') as string[];

  const getEmotionIcon = (emotion: string) => {
    // You can expand this with more emotion-specific icons
    return <Smile className="h-4 w-4" />;
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds) return 'N/A';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
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
              {formatDuration(narrative.durationSeconds)}
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
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Transition to Next Slide</h4>
            <p className="text-sm text-muted-foreground italic">
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
          </div>
        </div>
      </CardContent>
    </Card>
  );
}