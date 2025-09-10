import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { DeckAnalysis } from "@/types/presentation";
import { Brain, Target, Lightbulb, Users, FileText, User } from "lucide-react";

interface DeckAnalysisDisplayProps {
  analysis: DeckAnalysis;
}

export function DeckAnalysisDisplay({ analysis }: DeckAnalysisDisplayProps) {
  // Parse key themes from JSON
  const keyThemes = JSON.parse(analysis.keyThemes || '[]') as string[];

  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Brain className="h-5 w-5 text-primary" />
          <CardTitle>Deck Analysis</CardTitle>
        </div>
        <CardDescription>
          AI-generated insights about your presentation
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Extracted Metadata - Title and Author */}
        {(analysis.presentationTitle || analysis.presentationAuthor) && (
          <div className="bg-muted/50 rounded-lg p-4 space-y-3">
            {analysis.presentationTitle && (
              <div className="flex items-start gap-3">
                <FileText className="h-4 w-4 mt-0.5 text-primary" />
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">Presentation Title</p>
                  <p className="text-sm font-semibold">{analysis.presentationTitle}</p>
                </div>
              </div>
            )}
            {analysis.presentationAuthor && (
              <div className="flex items-start gap-3">
                <User className="h-4 w-4 mt-0.5 text-primary" />
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">Author</p>
                  <p className="text-sm">{analysis.presentationAuthor}</p>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Overall Story */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Lightbulb className="h-4 w-4" />
            <span>Overall Story</span>
          </div>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {analysis.overallStory}
          </p>
        </div>

        {/* Communication Intent */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Target className="h-4 w-4" />
            <span>Communication Intent</span>
          </div>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {analysis.communicationIntent}
          </p>
        </div>

        {/* Key Themes */}
        <div className="space-y-2">
          <h4 className="text-sm font-medium">Key Themes</h4>
          <div className="flex flex-wrap gap-2">
            {keyThemes.map((theme, index) => (
              <Badge key={index} variant="secondary">
                {theme}
              </Badge>
            ))}
          </div>
        </div>

        {/* Target Audience & Tone */}
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Users className="h-4 w-4" />
              <span>Target Audience</span>
            </div>
            <p className="text-sm text-muted-foreground">
              {analysis.targetAudience}
            </p>
          </div>
          <div className="space-y-2">
            <h4 className="text-sm font-medium">Tone</h4>
            <Badge variant="outline" className="capitalize">
              {analysis.tone}
            </Badge>
          </div>
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