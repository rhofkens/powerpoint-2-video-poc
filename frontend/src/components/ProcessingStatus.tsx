import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Loader2, StopCircle, FileText } from "lucide-react";
import { ProcessingStatus as PresentationProcessingStatus } from "@/types/presentation";

interface ProcessingStatusProps {
  fileName: string;
  onStopAndRestart: () => void;
  progress?: number;
  status?: PresentationProcessingStatus;
}

export function ProcessingStatus({ fileName, onStopAndRestart, progress = 0, status }: ProcessingStatusProps) {
  // Map status to steps with better granularity
  const getStepFromStatus = (status?: PresentationProcessingStatus) => {
    switch (status) {
      case 'UPLOADED': return 0;
      case 'PARSING': return 1;
      case 'RENDERING_READY': return 2;
      case 'ANALYZING': return 3;
      case 'INTENT_ANALYSIS_COMPLETE': return 4;
      case 'NARRATIVE_COMPLETE': return 5;
      case 'GENERATING_CONTENT': return 6;
      case 'COMPLETED': return 7;
      default: return 0;
    }
  };

  const currentStep = getStepFromStatus(status);
  
  const processingSteps = [
    { step: 1, name: "Parsing PowerPoint file", completed: currentStep > 1 },
    { step: 2, name: "Rendering slide images", completed: currentStep > 2 },
    { step: 3, name: "AI deck analysis", completed: currentStep > 3 },
    { step: 4, name: "AI slide analysis", completed: currentStep > 4 },
    { step: 5, name: "Generating narratives", completed: currentStep > 5 },
    { step: 6, name: "Creating audio/video content", completed: currentStep > 6 }
  ];

  // Calculate progress based on current step
  const calculatedProgress = status === 'COMPLETED' ? 100 : (currentStep / 7) * 100;

  return (
    <div className="space-y-6">
      <div className="text-center">
        <h2 className="text-2xl font-bold mb-2">Processing Your Presentation</h2>
        <p className="text-muted-foreground">
          Analyzing and generating content for your slides
        </p>
      </div>

      <Card className="p-8">
        <div className="space-y-6">
          {/* File Info */}
          <div className="flex items-center justify-center space-x-3 text-center">
            <FileText className="h-6 w-6 text-primary" />
            <div>
              <p className="font-medium">{fileName}</p>
              <p className="text-sm text-muted-foreground">Processing in progress...</p>
            </div>
          </div>

          {/* Progress Bar */}
          <div className="space-y-2">
            <div className="flex justify-between text-sm">
              <span>Overall Progress</span>
              <span>{Math.round(calculatedProgress)}%</span>
            </div>
            <Progress value={calculatedProgress} className="h-2" />
          </div>

          {/* Processing Steps */}
          <div className="space-y-3">
            <h4 className="font-medium text-sm text-muted-foreground uppercase tracking-wide">
              Processing Steps
            </h4>
            {processingSteps.map((step) => (
              <div key={step.step} className="flex items-center space-x-3">
                <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                  step.completed 
                    ? 'bg-success text-success-foreground' 
                    : currentStep === step.step
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground'
                }`}>
                  {step.completed ? '✓' : step.step}
                </div>
                <span className={`text-sm ${
                  step.completed ? 'text-success' : 'text-foreground'
                }`}>
                  {step.name}
                </span>
                {!step.completed && currentStep === step.step && (
                  <Loader2 className="h-4 w-4 animate-spin text-primary" />
                )}
              </div>
            ))}
          </div>

          {/* Action Button */}
          <div className="pt-4 border-t">
            <Button 
              variant="destructive" 
              onClick={onStopAndRestart}
              className="w-full"
            >
              <StopCircle className="h-4 w-4 mr-2" />
              Stop Processing and Restart
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}