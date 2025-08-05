import { FC } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Progress } from './ui/progress';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { 
  Loader2, 
  CheckCircle, 
  XCircle, 
  FileText, 
  Image,
  RefreshCw 
} from 'lucide-react';
import { PresentationStatus } from '../types/presentation';
import { cn } from '../lib/utils';

interface ProcessingProgressProps {
  status: PresentationStatus | null;
  onReRender?: (renderer: string) => void;
  isLoading?: boolean;
}

export const ProcessingProgress: FC<ProcessingProgressProps> = ({
  status,
  onReRender,
  isLoading = false
}) => {
  if (!status) {
    return null;
  }

  const { parsingProgress, renderingProgress } = status;
  
  // Calculate overall progress
  const parsingComplete = ['COMPLETED', 'PARSED'].includes(parsingProgress.status);
  const renderingComplete = renderingProgress.status === 'COMPLETED';
  const hasError = parsingProgress.status === 'FAILED' || renderingProgress.status === 'FAILED';
  
  const renderingPercentage = renderingProgress.totalSlides > 0
    ? Math.round((renderingProgress.completedSlides / renderingProgress.totalSlides) * 100)
    : 0;

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'IN_PROGRESS':
      case 'PARSING':
      case 'RENDERING':
        return <Loader2 className="h-4 w-4 animate-spin" />;
      case 'COMPLETED':
      case 'PARSED':
        return <CheckCircle className="h-4 w-4 text-green-600" />;
      case 'FAILED':
        return <XCircle className="h-4 w-4 text-red-600" />;
      default:
        return null;
    }
  };

  const getStatusBadgeVariant = (status: string) => {
    switch (status) {
      case 'IN_PROGRESS':
      case 'PARSING':
      case 'RENDERING':
        return 'default';
      case 'COMPLETED':
      case 'PARSED':
        return 'secondary';
      case 'FAILED':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  return (
    <div className="space-y-4">
      {/* Parsing Progress */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-lg">Parsing Progress</CardTitle>
            </div>
            <Badge variant={getStatusBadgeVariant(parsingProgress.status)}>
              <span className="flex items-center gap-1">
                {getStatusIcon(parsingProgress.status)}
                {parsingProgress.status}
              </span>
            </Badge>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            {parsingComplete ? (
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Slides found:</span>
                <span className="font-medium">{parsingProgress.slideCount}</span>
              </div>
            ) : (
              <div className="space-y-2">
                <Progress value={parsingComplete ? 100 : 50} className="h-2" />
                <p className="text-sm text-muted-foreground">
                  Extracting slide content and metadata...
                </p>
              </div>
            )}
            
            {parsingProgress.startedAt && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Started:</span>
                <span>{new Date(parsingProgress.startedAt).toLocaleTimeString()}</span>
              </div>
            )}
            
            {parsingProgress.completedAt && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Completed:</span>
                <span>{new Date(parsingProgress.completedAt).toLocaleTimeString()}</span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Rendering Progress */}
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Image className="h-5 w-5 text-muted-foreground" />
              <CardTitle className="text-lg">Rendering Progress</CardTitle>
            </div>
            <div className="flex items-center gap-2">
              <Badge variant={getStatusBadgeVariant(renderingProgress.status)}>
                <span className="flex items-center gap-1">
                  {getStatusIcon(renderingProgress.status)}
                  {renderingProgress.status}
                </span>
              </Badge>
              {renderingProgress.renderer && (
                <Badge variant="outline">
                  {renderingProgress.renderer}
                </Badge>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {renderingProgress.totalSlides > 0 && (
              <>
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">Progress:</span>
                    <span className="font-medium">
                      {renderingProgress.completedSlides} / {renderingProgress.totalSlides} slides
                    </span>
                  </div>
                  <Progress value={renderingPercentage} className="h-2" />
                </div>
              </>
            )}
            
            {renderingProgress.status === 'PENDING' && (
              <p className="text-sm text-muted-foreground">
                Waiting for parsing to complete...
              </p>
            )}
            
            {renderingProgress.startedAt && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Started:</span>
                <span>{new Date(renderingProgress.startedAt).toLocaleTimeString()}</span>
              </div>
            )}
            
            {renderingProgress.completedAt && (
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">Completed:</span>
                <span>{new Date(renderingProgress.completedAt).toLocaleTimeString()}</span>
              </div>
            )}
            
            {/* Re-render button */}
            {renderingComplete && onReRender && (
              <div className="pt-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => onReRender('MSGRAPH')}
                  disabled={isLoading}
                  className="w-full"
                >
                  <RefreshCw className={cn(
                    "h-4 w-4 mr-2",
                    isLoading && "animate-spin"
                  )} />
                  Re-render with Different Renderer
                </Button>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Error Message */}
      {hasError && (
        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-6">
            <p className="text-sm text-red-800">
              An error occurred during processing. Please try again or contact support.
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
};