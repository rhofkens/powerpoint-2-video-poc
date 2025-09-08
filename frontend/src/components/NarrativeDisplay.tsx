import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { SlideNarrative, EmotionIndicator, AvatarInstructions, SpeechMarkers } from "@/types/presentation";
import { MessageCircle, Smile, Clock, RefreshCw, Volume2, Sparkles, FileText, AlertCircle } from "lucide-react";
import { useState } from "react";

interface NarrativeDisplayProps {
  narrative: SlideNarrative;
  slideNumber: number;
  onRegenerate?: () => void;
}

export function NarrativeDisplay({ narrative, slideNumber, onRegenerate }: NarrativeDisplayProps) {
  const [showDetails, setShowDetails] = useState(false);
  
  // Parse JSON data
  const emotionIndicators = JSON.parse(narrative.emotionIndicators || '[]') as EmotionIndicator[];
  const avatarInstructions = JSON.parse(narrative.avatarInstructions || '{}') as AvatarInstructions;
  const speechMarkers = JSON.parse(narrative.speechMarkers || '{}') as SpeechMarkers;
  const emphasisWords = JSON.parse(narrative.emphasisWords || '[]') as string[];

  // Function to render narrative with emotion highlighting
  const renderNarrativeWithEmotions = (text: string) => {
    const words = text.split(' ');
    let currentEmotionIndex = 0;
    
    return words.map((word, index) => {
      // Check if we need to update the current emotion
      while (currentEmotionIndex < emotionIndicators.length &&
             index >= emotionIndicators[currentEmotionIndex].endWord) {
        currentEmotionIndex++;
      }
      
      const currentEmotion = currentEmotionIndex < emotionIndicators.length &&
                           index >= emotionIndicators[currentEmotionIndex].startWord
                           ? emotionIndicators[currentEmotionIndex].emotion
                           : null;
      
      const isEmphasized = emphasisWords.includes(word.replace(/[.,!?;:]/, ''));
      
      return (
        <span
          key={index}
          className={`
            ${currentEmotion ? `emotion-${currentEmotion}` : ''}
            ${isEmphasized ? 'font-semibold' : ''}
          `}
          title={currentEmotion || undefined}
        >
          {word}{' '}
        </span>
      );
    });
  };

  const getEmotionColor = (emotion: string) => {
    const emotionColors: Record<string, string> = {
      'enthusiastic': 'text-orange-600',
      'serious': 'text-gray-700',
      'curious': 'text-blue-600',
      'confident': 'text-purple-600',
      'friendly': 'text-green-600',
      'neutral': 'text-gray-600',
    };
    return emotionColors[emotion] || 'text-gray-600';
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
            {narrative.durationSeconds && (
              <Badge variant="outline" className="gap-1">
                <Clock className="h-3 w-3" />
                {narrative.durationSeconds}s
              </Badge>
            )}
            <Badge variant="secondary">
              v{narrative.version}
            </Badge>
            {narrative.isActive && (
              <Badge variant="default">Active</Badge>
            )}
          </div>
        </div>
        <CardDescription>
          AI-generated narrative with emotional delivery instructions
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Avatar Instructions Summary */}
        <div className="flex items-center gap-4 p-3 bg-muted rounded-lg">
          <Smile className="h-5 w-5 text-primary" />
          <div className="flex-1 text-sm">
            <span className="font-medium">Primary Emotion:</span>{' '}
            <span className={getEmotionColor(avatarInstructions.primaryEmotion)}>
              {avatarInstructions.primaryEmotion}
            </span>
            <span className="mx-2">•</span>
            <span className="font-medium">Gesture Intensity:</span>{' '}
            {avatarInstructions.gestureIntensity}
          </div>
        </div>

        {/* Narrative Text - Show tabs if enhanced version exists */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium">Narrative Text</h4>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setShowDetails(!showDetails)}
            >
              {showDetails ? 'Hide' : 'Show'} Details
            </Button>
          </div>
          
          {narrative.enhancedNarrativeText ? (
            <Tabs defaultValue="enhanced" className="w-full">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="original" className="gap-2">
                  <FileText className="h-4 w-4" />
                  Original
                </TabsTrigger>
                <TabsTrigger value="enhanced" className="gap-2">
                  <Sparkles className="h-4 w-4" />
                  Enhanced (ElevenLabs)
                </TabsTrigger>
              </TabsList>
              <TabsContent value="original" className="mt-4">
                <div className="p-4 bg-muted/50 rounded-lg text-sm leading-relaxed">
                  {showDetails ? renderNarrativeWithEmotions(narrative.narrativeText) : narrative.narrativeText}
                </div>
                <div className="mt-2 text-xs text-muted-foreground">
                  Generated with: {narrative.modelUsed}
                </div>
              </TabsContent>
              <TabsContent value="enhanced" className="mt-4">
                <div className="p-4 bg-muted/50 rounded-lg text-sm leading-relaxed">
                  {showDetails ? renderNarrativeWithEmotions(narrative.enhancedNarrativeText) : narrative.enhancedNarrativeText}
                </div>
                <div className="mt-2 flex items-center justify-between text-xs text-muted-foreground">
                  <span>Enhanced with: {narrative.enhancementModelUsed}</span>
                  {narrative.enhancementTimestamp && (
                    <span>Enhanced: {new Date(narrative.enhancementTimestamp).toLocaleString()}</span>
                  )}
                </div>
              </TabsContent>
            </Tabs>
          ) : (
            <>
              <div className="p-4 bg-muted/50 rounded-lg text-sm leading-relaxed">
                {showDetails ? renderNarrativeWithEmotions(narrative.narrativeText) : narrative.narrativeText}
              </div>
              <Alert className="mt-3">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription className="text-sm">
                  No emotional enhancement available. Run the ElevenLabs optimizer to add emotional markers for better TTS quality.
                </AlertDescription>
              </Alert>
            </>
          )}
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

        {/* Speech Markers Details */}
        {showDetails && speechMarkers && (
          <div className="space-y-2 pt-2 border-t">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <Volume2 className="h-4 w-4" />
              Speech Delivery Details
            </h4>
            <div className="grid grid-cols-2 gap-4 text-sm">
              {speechMarkers.pauses && speechMarkers.pauses.length > 0 && (
                <div>
                  <span className="font-medium">Pauses:</span>
                  <ul className="mt-1 space-y-1">
                    {speechMarkers.pauses.map((pause, index) => (
                      <li key={index} className="text-muted-foreground">
                        After word {pause.afterWord}: {pause.duration}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {emphasisWords.length > 0 && (
                <div>
                  <span className="font-medium">Emphasized Words:</span>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {emphasisWords.map((word, index) => (
                      <Badge key={index} variant="outline" className="text-xs">
                        {word}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex justify-end pt-2 border-t">
          {onRegenerate && (
            <Button
              size="sm"
              variant="outline"
              onClick={onRegenerate}
              className="gap-2"
            >
              <RefreshCw className="h-4 w-4" />
              Regenerate Narrative
            </Button>
          )}
        </div>
      </CardContent>

      <style jsx>{`
        .emotion-enthusiastic { color: rgb(234 88 12); }
        .emotion-serious { color: rgb(55 65 81); }
        .emotion-curious { color: rgb(37 99 235); }
        .emotion-confident { color: rgb(147 51 234); }
        .emotion-friendly { color: rgb(34 197 94); }
        .emotion-neutral { color: rgb(75 85 99); }
      `}</style>
    </Card>
  );
}