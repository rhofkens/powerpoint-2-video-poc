import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { SlideAnalysis, VisualConcept } from "@/types/presentation";
import { Eye, MessageSquare, ChartBar, Layers, Zap } from "lucide-react";

interface SlideAnalysisDisplayProps {
  analysis: SlideAnalysis;
  slideNumber: number;
}

export function SlideAnalysisDisplay({ analysis, slideNumber }: SlideAnalysisDisplayProps) {
  // Parse data from JSON strings
  const visualConcepts = JSON.parse(analysis.visualConcepts || '[]') as VisualConcept[];
  const keyPoints = JSON.parse(analysis.keyPoints || '[]') as string[];

  const getConceptIcon = (type: string) => {
    const conceptIcons: Record<string, string> = {
      // Structural Concepts
      'TIMELINE': '📅',
      'PROCESS_FLOW': '🔄',
      'HIERARCHY': '🏛️',
      'MATRIX': '⚡',
      'CYCLE': '♻️',
      // Comparison Concepts
      'COMPARISON_TABLE': '⚖️',
      'PROS_CONS': '👍',
      'BEFORE_AFTER': '🔀',
      'VENN_DIAGRAM': '⭕',
      // Data Visualization
      'BAR_CHART': '📊',
      'LINE_CHART': '📈',
      'PIE_CHART': '🥧',
      'SCATTER_PLOT': '🔵',
      'GAUGE_CHART': '🎯',
      'HEATMAP': '🔥',
      // Business Concepts
      'SWOT_ANALYSIS': '💼',
      'BUSINESS_MODEL': '🏢',
      'ROADMAP': '🗺️',
      'KPI_DASHBOARD': '📊',
      'FUNNEL': '🔻',
      'CUSTOMER_JOURNEY': '🚶',
      // Informational Concepts
      'BULLET_LIST': '📝',
      'DEFINITION': '📖',
      'QUOTE': '💬',
      'STATISTICS': '📊',
      'ICON_GRID': '🔢',
      'INFOGRAPHIC': '🎨',
      // Relational Concepts
      'MIND_MAP': '🧠',
      'NETWORK_DIAGRAM': '🔗',
      'FLOWCHART': '🔀',
      'DEPENDENCY_MAP': '🕸️',
      // Specialized Concepts
      'GANTT_CHART': '📊',
      'RISK_MATRIX': '⚠️',
      'PRICING_TABLE': '💰',
      'TEAM_STRUCTURE': '👥',
      'CALL_TO_ACTION': '🎯',
      'AGENDA': '📋',
      'SUMMARY': '📌',
      'QUESTION_SLIDE': '❓'
    };
    return conceptIcons[type] || '📋';
  };

  const getEmphasisColor = (level: string) => {
    switch (level) {
      case 'HIGH': return 'destructive';
      case 'MEDIUM': return 'default';
      case 'LOW': return 'secondary';
      default: return 'outline';
    }
  };

  return (
    <Card className="w-full">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Eye className="h-5 w-5 text-primary" />
            <CardTitle>Slide {slideNumber} Analysis</CardTitle>
          </div>
          <Badge variant={getEmphasisColor(analysis.emphasisLevel)}>
            {analysis.emphasisLevel} Emphasis
          </Badge>
        </div>
        <CardDescription>
          AI-powered analysis of slide content and visuals
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* General Message */}
        <div className="space-y-2">
          <div className="flex items-center gap-2 text-sm font-medium">
            <MessageSquare className="h-4 w-4" />
            <span>Main Message</span>
          </div>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {analysis.generalMessage}
          </p>
        </div>

        {/* Visual Concepts */}
        {visualConcepts.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Layers className="h-4 w-4" />
              <span>Visual Concepts</span>
            </div>
            <div className="space-y-3">
              {visualConcepts
                .sort((a, b) => {
                  // Sort by prominence: PRIMARY > SECONDARY > SUPPORTING
                  const prominenceOrder = { PRIMARY: 0, SECONDARY: 1, SUPPORTING: 2 };
                  return (prominenceOrder[a.prominence] || 2) - (prominenceOrder[b.prominence] || 2);
                })
                .map((concept, index) => (
                <div key={index} className={`
                  rounded-lg p-3 border
                  ${concept.prominence === 'PRIMARY' ? 'border-primary bg-primary/5' : 
                    concept.prominence === 'SECONDARY' ? 'border-secondary bg-secondary/5' : 
                    'border-muted bg-muted/5'}
                `}>
                  <div className="flex items-start gap-2">
                    <span className="text-lg">{getConceptIcon(concept.type)}</span>
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-medium text-sm">
                          {concept.type.replace(/_/g, ' ')}
                        </span>
                        <Badge 
                          variant={concept.confidence === 'HIGH' ? 'default' : 
                                  concept.confidence === 'MEDIUM' ? 'secondary' : 'outline'}
                          className="text-xs"
                        >
                          {concept.confidence}
                        </Badge>
                        {concept.prominence === 'PRIMARY' && (
                          <Badge variant="default" className="text-xs">
                            Primary
                          </Badge>
                        )}
                      </div>
                      
                      {/* Position indicator */}
                      {concept.position && (
                        <div className="text-xs text-muted-foreground mb-1">
                          Position: {concept.position.vertical} {concept.position.horizontal}
                        </div>
                      )}
                      
                      {/* Concept details */}
                      {concept.details && Object.keys(concept.details).length > 0 && (
                        <div className="text-xs space-y-1 mt-2">
                          {Object.entries(concept.details).map(([key, value]) => (
                            <div key={key} className="flex flex-wrap gap-1">
                              <span className="font-medium text-muted-foreground">
                                {key.replace(/([A-Z])/g, ' $1').trim()}:
                              </span>
                              <span className="text-foreground">
                                {Array.isArray(value) ? (
                                  <span className="inline-flex flex-wrap gap-1">
                                    {value.map((item, i) => (
                                      <Badge key={i} variant="outline" className="text-xs">
                                        {String(item)}
                                      </Badge>
                                    ))}
                                  </span>
                                ) : typeof value === 'object' ? (
                                  JSON.stringify(value, null, 2)
                                ) : (
                                  String(value)
                                )}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Key Points */}
        {keyPoints.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Zap className="h-4 w-4" />
              <span>Key Points</span>
            </div>
            <ul className="space-y-1">
              {keyPoints.map((point, index) => (
                <li key={index} className="text-sm text-muted-foreground flex items-start gap-2">
                  <span className="text-primary mt-0.5">•</span>
                  <span>{point}</span>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* Data Insights */}
        {analysis.dataInsights && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium">
              <ChartBar className="h-4 w-4" />
              <span>Data Insights</span>
            </div>
            <p className="text-sm text-muted-foreground">
              {analysis.dataInsights}
            </p>
          </div>
        )}

        {/* Transition Context */}
        {analysis.transitionContext && (
          <div className="space-y-2 pt-2 border-t">
            <h4 className="text-sm font-medium">Transition Context</h4>
            <p className="text-sm text-muted-foreground">
              {analysis.transitionContext}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}