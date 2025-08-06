import { useState, useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { MessageCircle, Loader2 } from "lucide-react";
import { apiService } from '@/services/api';
import { SlideNarrative } from '@/types/presentation';
import { useAnalysisStore } from '@/store/analysisStore';
import { useToast } from "@/hooks/use-toast";

interface CompleteNarrativeTabProps {
  presentationId: string;
  presentationTitle: string;
}

export function CompleteNarrativeTab({ presentationId, presentationTitle }: CompleteNarrativeTabProps) {
  const [narratives, setNarratives] = useState<SlideNarrative[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();
  
  // Get narrative generation status from store
  const analysisStatuses = useAnalysisStore(state => state.analysisStatuses);
  const narrativeStatus = analysisStatuses.find(
    s => s.presentationId === presentationId && s.analysisType === 'ALL_NARRATIVES_GENERATION'
  );
  
  const isGenerating = narrativeStatus?.state === 'IN_PROGRESS';
  const progress = narrativeStatus && narrativeStatus.totalItems > 0
    ? ((narrativeStatus.completedItems + narrativeStatus.failedItems) / narrativeStatus.totalItems) * 100
    : 0;

  // Fetch complete narrative
  const fetchNarratives = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await apiService.getCompleteNarrative(presentationId);
      setNarratives(data);
    } catch (err) {
      console.error('Failed to fetch complete narrative:', err);
      setError('Failed to load narratives');
      toast({
        title: "Error",
        description: "Failed to load complete narrative",
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNarratives();
  }, [presentationId]);

  // Refresh when generation completes
  useEffect(() => {
    if (narrativeStatus?.state === 'COMPLETED' && !isGenerating) {
      fetchNarratives();
    }
  }, [narrativeStatus?.state]);

  // Parse emotional cues from narrative text
  const parseNarrativeWithCues = (text: string) => {
    // Split by emotional cues pattern [emotion]
    const parts = text.split(/\[([^\]]+)\]/);
    return parts.map((part, index) => {
      if (index % 2 === 1) {
        // This is an emotional cue
        return (
          <Badge key={index} variant="outline" className="mx-1 text-xs">
            {part}
          </Badge>
        );
      }
      return part;
    });
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  if (error) {
    return (
      <Card>
        <CardContent className="pt-6">
          <div className="text-center text-muted-foreground">
            <MessageCircle className="h-12 w-12 mx-auto mb-4 opacity-50" />
            <p>{error}</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header with status */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Complete Narrative</CardTitle>
              <p className="text-sm text-muted-foreground mt-1">
                {presentationTitle}
              </p>
            </div>
            <div className="flex items-center gap-4">
              {narratives.length > 0 && narratives[0].style && (
                <Badge variant="secondary" className="capitalize">
                  {narratives[0].style} Style
                </Badge>
              )}
              <Badge variant={isGenerating ? "default" : "outline"}>
                {isGenerating ? 'Generating...' : `${narratives.length} Slides`}
              </Badge>
            </div>
          </div>
        </CardHeader>
        
        {/* Progress bar if generating */}
        {isGenerating && (
          <CardContent className="pt-0">
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Generation Progress</span>
                <span className="font-medium">{Math.round(progress)}%</span>
              </div>
              <Progress value={progress} className="h-2" />
              {narrativeStatus?.message && (
                <p className="text-xs text-muted-foreground">{narrativeStatus.message}</p>
              )}
            </div>
          </CardContent>
        )}
      </Card>

      {/* Narratives list */}
      <Card>
        <ScrollArea className="h-[600px]">
          <CardContent className="pt-6">
            {narratives.length === 0 ? (
              <div className="text-center py-12">
                <MessageCircle className="h-12 w-12 mx-auto mb-4 text-muted-foreground" />
                <p className="text-muted-foreground">
                  {isGenerating ? 'Narratives are being generated...' : 'No narratives generated yet'}
                </p>
              </div>
            ) : (
              <div className="space-y-6">
                {narratives.map((narrative, index) => (
                  <div key={narrative.id}>
                    {/* Slide header */}
                    <div className="flex items-center gap-3 mb-3">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 bg-primary/10 rounded-full flex items-center justify-center">
                          <span className="text-sm font-semibold">{narrative.slide?.slideNumber || index + 1}</span>
                        </div>
                        <div>
                          <h3 className="font-semibold">
                            Slide {narrative.slide?.slideNumber || index + 1}: {narrative.slide?.title || 'Untitled'}
                          </h3>
                        </div>
                      </div>
                      {narrative.duration && (
                        <Badge variant="outline" className="text-xs">
                          {narrative.duration}s
                        </Badge>
                      )}
                    </div>

                    {/* Narrative content */}
                    <div className="pl-10">
                      <div className="prose prose-sm max-w-none text-foreground">
                        <p className="leading-relaxed">
                          {parseNarrativeWithCues(narrative.narrativeText)}
                        </p>
                      </div>
                      
                      {/* Additional metadata */}
                      {narrative.avatarInstructions && (
                        <div className="mt-3 p-3 bg-muted/50 rounded-md">
                          <p className="text-xs text-muted-foreground">
                            <span className="font-medium">Avatar Instructions:</span> {narrative.avatarInstructions}
                          </p>
                        </div>
                      )}
                    </div>

                    {/* Separator between slides */}
                    {index < narratives.length - 1 && (
                      <Separator className="my-6" />
                    )}
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </ScrollArea>
      </Card>

      {/* Auto-refresh indicator */}
      {isGenerating && (
        <div className="flex items-center justify-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>Auto-refreshing as narratives are generated...</span>
        </div>
      )}
    </div>
  );
}