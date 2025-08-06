import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import { SlideAnalysis, VisualConcept } from "@/types/presentation";
import { Brain, Target, Lightbulb, BarChart3, AlertCircle } from "lucide-react";

interface SlideAnalysisCardProps {
  analysis: SlideAnalysis;
  slideNumber: number;
}

export function SlideAnalysisCard({ analysis, slideNumber }: SlideAnalysisCardProps) {
  // Parse JSON fields
  const visualConcepts = JSON.parse(analysis.visualConcepts || '[]') as VisualConcept[];
  const keyPoints = JSON.parse(analysis.keyPoints || '[]') as string[];

  const getEmphasisIcon = (level: string) => {
    switch (level) {
      case 'HIGH':
        return <AlertCircle className="h-4 w-4 text-red-500" />;
      case 'MEDIUM':
        return <AlertCircle className="h-4 w-4 text-yellow-500" />;
      case 'LOW':
        return <AlertCircle className="h-4 w-4 text-green-500" />;
      default:
        return null;
    }
  };

  const getConceptIcon = (type: string) => {
    if (type.includes('CHART') || type.includes('GRAPH')) return <BarChart3 className="h-4 w-4" />;
    if (type.includes('PROCESS') || type.includes('FLOW')) return <Target className="h-4 w-4" />;
    return <Lightbulb className="h-4 w-4" />;
  };

  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Brain className="h-5 w-5 text-primary" />
            <CardTitle>Slide {slideNumber} Analysis</CardTitle>
          </div>
          <div className="flex items-center gap-2">
            {getEmphasisIcon(analysis.emphasisLevel)}
            <Badge variant="outline">{analysis.emphasisLevel} Emphasis</Badge>
          </div>
        </div>
        <CardDescription>
          AI-extracted insights and visual concepts
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* General Message */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">General Message</h4>
          <p className="text-sm text-muted-foreground">
            {analysis.generalMessage}
          </p>
        </div>

        {/* Key Points */}
        {keyPoints.length > 0 && (
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Key Points</h4>
            <ul className="list-disc list-inside space-y-1">
              {keyPoints.map((point, index) => (
                <li key={index} className="text-sm text-muted-foreground">
                  {point}
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Visual Concepts */}
        {visualConcepts.length > 0 && (
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Visual Concepts</h4>
            <div className="grid grid-cols-2 gap-2">
              {visualConcepts.map((concept, index) => (
                <div 
                  key={index}
                  className="flex items-center gap-2 p-2 bg-muted/50 rounded-md"
                >
                  {getConceptIcon(concept.type)}
                  <div className="flex-1">
                    <p className="text-xs font-medium">
                      {concept.type.replace(/_/g, ' ')}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {concept.confidence} confidence
                    </p>
                  </div>
                  <Badge variant="secondary" className="text-xs">
                    {concept.prominence}
                  </Badge>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Data Insights */}
        {analysis.dataInsights && (
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Data Insights</h4>
            <p className="text-sm text-muted-foreground">
              {analysis.dataInsights}
            </p>
          </div>
        )}

        {/* Transition Context */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Transition Context</h4>
          <p className="text-sm text-muted-foreground">
            {analysis.transitionContext}
          </p>
        </div>

        {/* Metadata */}
        <div className="pt-4 border-t">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>Model: {analysis.modelUsed}</span>
            <span>Version: {analysis.promptVersion}</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}