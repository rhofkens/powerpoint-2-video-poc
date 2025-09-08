import { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/accordion';
import { useToast } from '@/hooks/use-toast';
import { apiService } from '@/services/api';
import { 
  PreflightCheckResponse,
  PreflightStatus,
  CheckStatus,
  SlideCheckResult,
  getStatusColor,
  PreflightCheckOptions
} from '@/types/preflight';
import { 
  CheckCircle,
  XCircle,
  AlertTriangle,
  Minus,
  Loader2,
  FileCheck,
  AlertCircle,
  Download,
  RefreshCw,
  ChevronRight
} from 'lucide-react';

interface PreflightCheckModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  presentationId: string;
  onComplete?: (result: PreflightCheckResponse) => void;
}

export function PreflightCheckModal({
  open,
  onOpenChange,
  presentationId,
  onComplete
}: PreflightCheckModalProps) {
  const [checkStatus, setCheckStatus] = useState<PreflightStatus>('CHECKING');
  const [checkResponse, setCheckResponse] = useState<PreflightCheckResponse | null>(null);
  const [currentSlide, setCurrentSlide] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [checkOptions, setCheckOptions] = useState<PreflightCheckOptions>({
    checkEnhancedNarrative: true,
    forceRefresh: false
  });
  const { toast } = useToast();

  useEffect(() => {
    if (open && !isRunning) {
      runPreflightCheck();
    }
  }, [open]);

  const runPreflightCheck = async () => {
    setIsRunning(true);
    setError(null);
    setCheckStatus('CHECKING');
    setCurrentSlide(0);

    try {
      // First check if there's a cached result
      if (!checkOptions.forceRefresh) {
        const cachedStatus = await apiService.getPreflightStatus(presentationId);
        if (cachedStatus) {
          setCheckResponse(cachedStatus);
          setCheckStatus(cachedStatus.overallStatus);
          setIsRunning(false);
          return;
        }
      }

      // Run the preflight check
      const response = await apiService.runPreflightCheck(presentationId, checkOptions);
      
      // Simulate progress for each slide
      const totalSlides = response.summary.totalSlides;
      for (let i = 0; i < totalSlides; i++) {
        setCurrentSlide(i + 1);
        await new Promise(resolve => setTimeout(resolve, 100)); // Brief delay for UI feedback
      }

      setCheckResponse(response);
      setCheckStatus(response.overallStatus);
      
      if (onComplete) {
        onComplete(response);
      }

      // Show appropriate toast based on status
      if (response.overallStatus === 'READY') {
        toast({
          title: 'Preflight Check Passed',
          description: 'All slides are ready for video generation',
        });
      } else if (response.overallStatus === 'HAS_WARNINGS') {
        toast({
          title: 'Preflight Check Complete',
          description: 'Some assets need to be published to R2',
          variant: 'default',
        });
      } else if (response.overallStatus === 'INCOMPLETE') {
        toast({
          title: 'Preflight Check Failed',
          description: `${response.summary.totalSlides - response.summary.slidesReady} slides need attention`,
          variant: 'destructive',
        });
      }
    } catch (err) {
      console.error('Preflight check error:', err);
      setError(err instanceof Error ? err.message : 'An unexpected error occurred');
      setCheckStatus('ERROR');
      toast({
        title: 'Preflight Check Failed',
        description: 'Failed to complete the preflight check',
        variant: 'destructive',
      });
    } finally {
      setIsRunning(false);
    }
  };

  const getStatusIcon = (status: CheckStatus | PreflightStatus) => {
    switch (status) {
      case 'PASSED':
      case 'READY':
        return <CheckCircle className="h-4 w-4 text-green-600" />;
      case 'FAILED':
      case 'INCOMPLETE':
      case 'ERROR':
        return <XCircle className="h-4 w-4 text-red-600" />;
      case 'WARNING':
      case 'HAS_WARNINGS':
        return <AlertTriangle className="h-4 w-4 text-yellow-600" />;
      case 'NOT_APPLICABLE':
        return <Minus className="h-4 w-4 text-gray-400" />;
      case 'CHECKING':
        return <Loader2 className="h-4 w-4 animate-spin text-blue-600" />;
      default:
        return null;
    }
  };

  const getOverallStatusMessage = () => {
    if (!checkResponse) return '';
    
    const { summary } = checkResponse;
    if (checkStatus === 'READY') {
      return `All ${summary.totalSlides} slides are ready for video generation`;
    } else if (checkStatus === 'HAS_WARNINGS') {
      const warnings = [];
      if (summary.slidesWithUnpublishedAssets > 0) {
        warnings.push(`${summary.slidesWithUnpublishedAssets} have unpublished assets`);
      }
      if (summary.slidesMissingEnhancedNarrative > 0) {
        warnings.push(`${summary.slidesMissingEnhancedNarrative} missing enhanced narratives (optional)`);
      }
      return `${summary.slidesReady} of ${summary.totalSlides} slides ready. Warnings: ${warnings.join(', ')}`;
    } else if (checkStatus === 'INCOMPLETE') {
      const issues = [];
      if (summary.slidesMissingNarrative > 0) issues.push(`${summary.slidesMissingNarrative} missing narratives`);
      if (summary.slidesMissingAudio > 0) issues.push(`${summary.slidesMissingAudio} missing audio`);
      if (summary.slidesMissingVideo > 0) issues.push(`${summary.slidesMissingVideo} missing videos`);
      if (summary.slidesMissingImages > 0) issues.push(`${summary.slidesMissingImages} missing images`);
      return `Issues found: ${issues.join(', ')}`;
    }
    return '';
  };

  const exportResults = () => {
    if (!checkResponse) return;
    
    const exportData = {
      ...checkResponse,
      exportedAt: new Date().toISOString()
    };
    
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `preflight-check-${presentationId}-${Date.now()}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    
    toast({
      title: 'Results Exported',
      description: 'Preflight check results have been downloaded',
    });
  };

  const renderSlideResult = (slide: SlideCheckResult) => {
    const hasIssues = slide.issues.length > 0;
    
    return (
      <AccordionItem key={slide.slideId} value={slide.slideId}>
        <AccordionTrigger className="hover:no-underline text-left">
          <div className="flex items-center justify-between w-full pr-4 gap-4">
            <div className="flex items-center gap-3 min-w-0">
              <span className="font-medium shrink-0">Slide {slide.slideNumber}</span>
              {slide.slideTitle && (
                <span className="text-sm text-muted-foreground line-clamp-2">{slide.slideTitle}</span>
              )}
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {getStatusIcon(slide.narrativeStatus)}
              {getStatusIcon(slide.audioStatus)}
              {getStatusIcon(slide.avatarVideoStatus)}
              {getStatusIcon(slide.imageStatus)}
              {hasIssues && (
                <Badge variant="destructive" className="ml-2">
                  {slide.issues.length} issue{slide.issues.length !== 1 ? 's' : ''}
                </Badge>
              )}
            </div>
          </div>
        </AccordionTrigger>
        <AccordionContent>
          <div className="space-y-3 pt-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="flex items-center gap-2">
                {getStatusIcon(slide.narrativeStatus)}
                <span className="text-sm">Narrative: {slide.narrativeStatus}</span>
              </div>
              <div className="flex items-center gap-2">
                {getStatusIcon(slide.audioStatus)}
                <span className="text-sm">Audio: {slide.audioStatus}</span>
              </div>
              <div className="flex items-center gap-2">
                {getStatusIcon(slide.enhancedNarrativeStatus)}
                <span className="text-sm">Enhanced Narrative: {slide.enhancedNarrativeStatus}</span>
              </div>
              <div className="flex items-center gap-2">
                {getStatusIcon(slide.avatarVideoStatus)}
                <span className="text-sm">Avatar Video: {slide.avatarVideoStatus}</span>
              </div>
              <div className="flex items-center gap-2">
                {getStatusIcon(slide.imageStatus)}
                <span className="text-sm">Slide Image: {slide.imageStatus}</span>
              </div>
            </div>
            
            {hasIssues && (
              <Alert variant="destructive">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  <ul className="list-disc list-inside space-y-1">
                    {slide.issues.map((issue, idx) => (
                      <li key={idx} className="text-sm">{issue}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            )}
            
            {slide.metadata && Object.keys(slide.metadata).length > 0 && (
              <details className="text-xs text-muted-foreground mt-2">
                <summary className="cursor-pointer hover:text-foreground font-medium">View Metadata</summary>
                <div className="mt-2 p-3 bg-muted rounded-md overflow-hidden">
                  <div className="overflow-x-auto max-h-64">
                    <pre className="text-xs" style={{ wordBreak: 'break-all', whiteSpace: 'pre-wrap' }}>
                      {JSON.stringify(slide.metadata, null, 2)}
                    </pre>
                  </div>
                </div>
              </details>
            )}
          </div>
        </AccordionContent>
      </AccordionItem>
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[80vh] overflow-hidden">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileCheck className="h-5 w-5" />
            Pre-flight Check
          </DialogTitle>
          <DialogDescription>
            Validating presentation readiness for video generation
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 overflow-hidden">
          {/* Progress Section */}
          {isRunning && !checkResponse && (
            <Card>
              <CardContent className="pt-6">
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">Checking slides...</span>
                    <span className="text-sm text-muted-foreground">
                      {currentSlide > 0 ? `Slide ${currentSlide}` : 'Initializing...'}
                    </span>
                  </div>
                  <Progress value={checkResponse ? 100 : 50} className="h-2" />
                </div>
              </CardContent>
            </Card>
          )}

          {/* Error State */}
          {error && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {/* Results Section */}
          {checkResponse && (
            <>
              {/* Overall Status */}
              <Card>
                <CardContent className="pt-6">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {getStatusIcon(checkStatus)}
                      <div>
                        <p className="font-medium">
                          {checkStatus === 'READY' ? 'All Checks Passed' :
                           checkStatus === 'HAS_WARNINGS' ? 'Checks Passed with Warnings' :
                           checkStatus === 'INCOMPLETE' ? 'Checks Failed' :
                           'Check Error'}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {getOverallStatusMessage()}
                        </p>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setCheckOptions({ ...checkOptions, forceRefresh: true });
                          runPreflightCheck();
                        }}
                        disabled={isRunning}
                      >
                        <RefreshCw className="h-4 w-4 mr-1" />
                        Refresh
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={exportResults}
                      >
                        <Download className="h-4 w-4 mr-1" />
                        Export
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>

              {/* Summary Stats */}
              <div className="grid grid-cols-4 gap-3">
                <Card>
                  <CardContent className="pt-4 pb-3">
                    <div className="text-2xl font-bold">{checkResponse.summary.totalSlides}</div>
                    <p className="text-xs text-muted-foreground">Total Slides</p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4 pb-3">
                    <div className="text-2xl font-bold text-green-600">{checkResponse.summary.slidesReady}</div>
                    <p className="text-xs text-muted-foreground">Ready</p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4 pb-3">
                    <div className="text-2xl font-bold text-yellow-600">{checkResponse.summary.slidesWithUnpublishedAssets}</div>
                    <p className="text-xs text-muted-foreground">Unpublished</p>
                  </CardContent>
                </Card>
                <Card>
                  <CardContent className="pt-4 pb-3">
                    <div className="text-2xl font-bold text-red-600">
                      {checkResponse.summary.totalSlides - checkResponse.summary.slidesReady}
                    </div>
                    <p className="text-xs text-muted-foreground">Need Work</p>
                  </CardContent>
                </Card>
              </div>

              {/* Detailed Results */}
              <Card className="overflow-hidden">
                <CardContent className="pt-4">
                  <h3 className="font-medium mb-3">Slide Details</h3>
                  <ScrollArea className="h-[300px] pr-4">
                    <Accordion type="single" collapsible className="w-full">
                      {checkResponse.slideResults.map(renderSlideResult)}
                    </Accordion>
                  </ScrollArea>
                </CardContent>
              </Card>
            </>
          )}
        </div>

        <div className="flex justify-end gap-2 mt-4">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          {checkResponse && checkStatus === 'READY' && (
            <Button onClick={() => onOpenChange(false)}>
              Proceed to Generation
              <ChevronRight className="h-4 w-4 ml-1" />
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}