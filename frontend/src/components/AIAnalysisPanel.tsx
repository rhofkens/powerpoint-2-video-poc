import { useState, useEffect, useRef } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Progress } from "@/components/ui/progress";
import { DeckAnalysisDisplay } from './DeckAnalysisDisplay';
import { NarrativeStyleSelector, type NarrativeStyle } from './NarrativeStyleSelector';
import { CompleteNarrativeTab } from './CompleteNarrativeTab';
import { PreflightCheckModal } from './PreflightCheckModal';
import { IntroVideoModal } from './IntroVideoModal';
import { VideoStoryModal } from './VideoStoryModal';
import { apiService } from '@/services/api';
import { useAnalysisStore } from '@/store/analysisStore';
import { useToast } from "@/hooks/use-toast";
import { Brain, Loader2, RefreshCw, Sparkles, MessageCircle, AlertCircle, CheckCircle, Volume2, Video, Film } from "lucide-react";
import { PreflightCheckResponse } from '@/types/preflight';
import { IntroVideo } from '@/types/intro-video';

interface AIAnalysisPanelProps {
  presentationId: string;
  processingStatus: string;
  presentationTitle?: string;
}

export function AIAnalysisPanel({ presentationId, processingStatus, presentationTitle }: AIAnalysisPanelProps) {
  const [isAnalyzingDeck, setIsAnalyzingDeck] = useState(false);
  const [preflightModalOpen, setPreflightModalOpen] = useState(false);
  const [introVideoModalOpen, setIntroVideoModalOpen] = useState(false);
  const [videoStoryModalOpen, setVideoStoryModalOpen] = useState(false);
  const [introVideo, setIntroVideo] = useState<IntroVideo | null>(null);
  const [isRunningPreflightCheck, setIsRunningPreflightCheck] = useState(false);
  const [isGeneratingAllAudio, setIsGeneratingAllAudio] = useState(false);
  const [audioGenerationProgress, setAudioGenerationProgress] = useState({ current: 0, total: 0 });
  const { toast } = useToast();
  
  // Get state and actions from Zustand store
  const {
    analysisStatuses,
    deckAnalyses,
    loading,
    narrativeStyle,
    fetchDeckAnalysis,
    fetchAnalysisStatus,
    startPolling,
    stopPolling,
    clearAnalysisData,
    setNarrativeStyle,
  } = useAnalysisStore();
  
  // Get current presentation's data
  const deckAnalysis = deckAnalyses[presentationId] || null;
  const isLoadingDeckAnalysis = loading.deckAnalysis[presentationId] || false;
  const currentAnalysisStatuses = analysisStatuses.filter(s => s.presentationId === presentationId);
  

  // Check if AI analysis features should be enabled
  const canRunAnalysis = ['RENDERING_READY', 'ANALYZING', 'INTENT_ANALYSIS_COMPLETE', 
                         'NARRATIVE_COMPLETE', 'GENERATING_CONTENT', 'COMPLETED'].includes(processingStatus);

  // Track previous analysis statuses for notifications
  const previousStatusesRef = useRef<typeof analysisStatuses>([]);
  
  // Handle notifications when statuses change
  useEffect(() => {
    const previousStatuses = previousStatusesRef.current;
    const currentStatuses = analysisStatuses.filter(s => s.presentationId === presentationId);
    
    currentStatuses.forEach(newStatus => {
      const oldStatus = previousStatuses.find(s => 
        s.analysisType === newStatus.analysisType && 
        s.presentationId === presentationId
      );
      
      if (oldStatus?.state === 'IN_PROGRESS') {
        if (newStatus.state === 'COMPLETED') {
          const typeLabel = newStatus.analysisType === 'ALL_SLIDES_ANALYSIS' 
            ? 'Slide Analysis' 
            : newStatus.analysisType === 'ALL_NARRATIVES_GENERATION'
            ? 'Narrative Generation'
            : 'Analysis';
            
          toast({
            title: `${typeLabel} Complete`,
            description: newStatus.message || `${typeLabel} completed successfully.`
          });
        } else if (newStatus.state === 'FAILED') {
          const typeLabel = newStatus.analysisType === 'ALL_SLIDES_ANALYSIS' 
            ? 'Slide Analysis' 
            : newStatus.analysisType === 'ALL_NARRATIVES_GENERATION'
            ? 'Narrative Generation'
            : 'Analysis';
            
          toast({
            title: `${typeLabel} Failed`,
            description: newStatus.errors.length > 0 
              ? newStatus.errors[0] 
              : `${typeLabel} encountered an error.`,
            variant: "destructive"
          });
        }
      }
    });
    
    previousStatusesRef.current = analysisStatuses;
  }, [analysisStatuses, presentationId, toast]);
  
  // Load initial data and check for running analyses
  useEffect(() => {
    if (!canRunAnalysis || !presentationId) return;
    
    // Fetch deck analysis (don't let this block other operations)
    fetchDeckAnalysis(presentationId).catch(() => {
      // Ignore errors - deck analysis might not exist yet
    });
    
    // Fetch intro video if exists
    apiService.getIntroVideo(presentationId).then(video => {
      if (video) {
        setIntroVideo(video);
      }
    }).catch(() => {
      // Ignore errors - intro video might not exist yet
    });
    
    // Check for running analyses  
    fetchAnalysisStatus(presentationId).then(() => {
      const store = useAnalysisStore.getState();
      const hasRunning = store.analysisStatuses.some(s => 
        s.presentationId === presentationId &&
        (s.state === 'IN_PROGRESS' || s.state === 'PENDING')
      );
      
      if (hasRunning) {
        startPolling(presentationId);
      }
    }).catch(error => {
      console.error('Failed to fetch initial analysis status:', error);
    });
    
    // Cleanup
    return () => {
      stopPolling(presentationId);
    };
    // Only run when presentationId or canRunAnalysis changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [presentationId, canRunAnalysis]);

  const handleAnalyzeDeck = async () => {
    setIsAnalyzingDeck(true);
    try {
      // Force regeneration if deck analysis already exists
      const forceRegenerate = deckAnalysis !== null;
      const analysis = await apiService.analyzeDeck(presentationId, forceRegenerate);
      useAnalysisStore.getState().setDeckAnalysis(presentationId, analysis);
      toast({
        title: "Deck Analysis Complete",
        description: forceRegenerate 
          ? "AI has successfully regenerated the presentation analysis."
          : "AI has successfully analyzed your presentation deck."
      });
    } catch (error) {
      console.error('Failed to analyze deck:', error);
      toast({
        title: "Analysis Failed",
        description: "Failed to analyze the presentation deck. Please try again.",
        variant: "destructive"
      });
    } finally {
      setIsAnalyzingDeck(false);
    }
  };

  const handleAnalyzeAllSlides = async () => {
    try {
      await apiService.analyzeAllSlides(presentationId);
      toast({
        title: "Slide Analysis Started",
        description: "AI is analyzing all slides. This may take a few minutes."
      });
      
      // Immediately fetch status to get the initial state
      await fetchAnalysisStatus(presentationId);
      
      // Start polling for progress updates
      startPolling(presentationId);
      
    } catch (error) {
      console.error('Failed to analyze slides:', error);
      toast({
        title: "Analysis Failed",
        description: "Failed to start slide analysis. Please try again.",
        variant: "destructive"
      });
    }
  };

  const handleGenerateAllNarratives = async () => {
    try {
      console.log('[AIAnalysisPanel] Starting narrative generation for presentation:', presentationId);
      console.log('[AIAnalysisPanel] Narrative style:', narrativeStyle);
      
      await apiService.generateAllNarratives(presentationId, narrativeStyle);
      toast({
        title: "Narrative Generation Started",
        description: `AI is generating ${narrativeStyle} style narratives for all slides. This may take a few minutes.`
      });
      
      // Small delay to ensure backend has started processing
      await new Promise(resolve => setTimeout(resolve, 500));
      
      // Fetch initial status and start polling
      console.log('[AIAnalysisPanel] Fetching initial status...');
      await fetchAnalysisStatus(presentationId);
      
      // Check what statuses we got
      const store = useAnalysisStore.getState();
      const narrativeStatuses = store.analysisStatuses.filter(s => 
        s.presentationId === presentationId && 
        s.analysisType === 'ALL_NARRATIVES_GENERATION'
      );
      console.log('[AIAnalysisPanel] Narrative generation statuses after fetch:', narrativeStatuses);
      
      console.log('[AIAnalysisPanel] Starting polling...');
      startPolling(presentationId);
      
    } catch (error) {
      console.error('Failed to generate narratives:', error);
      toast({
        title: "Generation Failed",
        description: "Failed to start narrative generation. Please try again.",
        variant: "destructive"
      });
    }
  };

  const handleGenerateAllAudio = async () => {
    setIsGeneratingAllAudio(true);
    setAudioGenerationProgress({ current: 0, total: 0 });
    
    try {
      // Fetch all slides
      const slides = await apiService.getSlides(presentationId);
      const sortedSlides = slides.sort((a, b) => a.slideNumber - b.slideNumber);
      
      setAudioGenerationProgress({ current: 0, total: sortedSlides.length });
      
      let successCount = 0;
      const failedSlides: number[] = [];
      
      // Process each slide sequentially
      for (let i = 0; i < sortedSlides.length; i++) {
        const slide = sortedSlides[i];
        
        // Update progress
        setAudioGenerationProgress({ current: i + 1, total: sortedSlides.length });
        
        try {
          // Generate speech for this slide - backend will check for narrative
          await apiService.generateSpeech(slide.id, {
            presentationId,
            slideId: slide.id,
            narrativeStyle: narrativeStyle || 'business',
            forceRegenerate: false
          });
          
          successCount++;
          
          // Small delay between slides to avoid overwhelming the API
          await new Promise(resolve => setTimeout(resolve, 500));
          
        } catch (error) {
          console.error(`Failed to generate audio for slide ${slide.slideNumber}:`, error);
          
          // Check if error is due to missing narrative
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const errorMessage = (error as any)?.response?.data?.message;
          if (errorMessage?.includes('No active narrative')) {
            console.log(`Slide ${slide.slideNumber}: No active narrative found`);
          }
          
          failedSlides.push(slide.slideNumber);
          // Continue with next slide even if one fails
        }
      }
      
      // Show completion toast
      if (successCount === sortedSlides.length) {
        toast({
          title: "Audio Generation Complete",
          description: `Successfully generated audio for all ${sortedSlides.length} slides`
        });
      } else if (successCount > 0) {
        toast({
          title: "Audio Generation Partially Complete",
          description: `Generated audio for ${successCount} of ${sortedSlides.length} slides. Failed slides: ${failedSlides.join(', ')}`,
          variant: "default"
        });
      } else {
        toast({
          title: "Audio Generation Failed",
          description: "Failed to generate audio for all slides. Ensure all slides have narratives.",
          variant: "destructive"
        });
      }
      
    } catch (error) {
      console.error('Failed to generate audio for all slides:', error);
      toast({
        title: "Audio Generation Failed",
        description: "Failed to start audio generation. Please try again.",
        variant: "destructive"
      });
    } finally {
      setIsGeneratingAllAudio(false);
      setAudioGenerationProgress({ current: 0, total: 0 });
    }
  };

  const handlePreflightCheck = () => {
    setPreflightModalOpen(true);
  };

  const handlePreflightComplete = (result: PreflightCheckResponse) => {
    console.log('[AIAnalysisPanel] Preflight check completed:', result);
    // You can add additional logic here if needed when the check completes
  };

  if (!canRunAnalysis) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Brain className="h-5 w-5" />
            AI Analysis
          </CardTitle>
          <CardDescription>
            AI analysis will be available once slide rendering is complete
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Brain className="h-5 w-5" />
                AI Analysis & Content Generation
              </CardTitle>
              <CardDescription>
                Use AI to analyze your presentation and generate engaging narratives
              </CardDescription>
            </div>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                onClick={handleAnalyzeDeck}
                disabled={isAnalyzingDeck || !canRunAnalysis}
              >
                {isAnalyzingDeck ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Analyzing...
                  </>
                ) : (
                  <>
                    <RefreshCw className="h-4 w-4 mr-2" />
                    {deckAnalysis ? 'Regenerate Analysis' : 'Analyze Deck'}
                  </>
                )}
              </Button>
            </div>
          </div>
        </CardHeader>
      </Card>

      <Tabs defaultValue="deck" className="w-full">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="deck">Deck Analysis</TabsTrigger>
          <TabsTrigger value="actions">AI Actions</TabsTrigger>
          <TabsTrigger value="status">Status</TabsTrigger>
          <TabsTrigger value="narrative">Complete Narrative</TabsTrigger>
        </TabsList>

        <TabsContent value="deck" className="space-y-4">
          {isLoadingDeckAnalysis ? (
            <Card>
              <CardContent className="flex items-center justify-center py-8">
                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
              </CardContent>
            </Card>
          ) : deckAnalysis ? (
            <DeckAnalysisDisplay analysis={deckAnalysis} />
          ) : (
            <Card>
              <CardContent className="text-center py-8">
                <p className="text-muted-foreground mb-4">
                  No deck analysis available yet.
                </p>
                <Button onClick={handleAnalyzeDeck} disabled={isAnalyzingDeck}>
                  <Sparkles className="h-4 w-4 mr-2" />
                  Run Deck Analysis
                </Button>
              </CardContent>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="actions" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>AI Processing Actions</CardTitle>
              <CardDescription>
                Run AI analysis and content generation for your presentation
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-4">
                <NarrativeStyleSelector
                  value={narrativeStyle}
                  onChange={setNarrativeStyle}
                  disabled={false}
                />
                
                <div className="space-y-2">
                  <h4 className="font-medium">Batch Operations</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <Button
                      variant="outline"
                      onClick={handleAnalyzeAllSlides}
                      className="justify-start"
                      disabled={currentAnalysisStatuses.some(s => s.analysisType === 'ALL_SLIDES_ANALYSIS' && s.state === 'IN_PROGRESS')}
                    >
                      {currentAnalysisStatuses.some(s => s.analysisType === 'ALL_SLIDES_ANALYSIS' && s.state === 'IN_PROGRESS') ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      ) : (
                        <Brain className="h-4 w-4 mr-2" />
                      )}
                      Analyze All Slides
                    </Button>
                    <Button
                      variant="outline"
                      onClick={handleGenerateAllNarratives}
                      className="justify-start"
                      disabled={currentAnalysisStatuses.some(s => s.analysisType === 'ALL_NARRATIVES_GENERATION' && s.state === 'IN_PROGRESS')}
                    >
                      {currentAnalysisStatuses.some(s => s.analysisType === 'ALL_NARRATIVES_GENERATION' && s.state === 'IN_PROGRESS') ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      ) : (
                        <MessageCircle className="h-4 w-4 mr-2" />
                      )}
                      Generate All Narratives
                    </Button>
                    <Button
                      variant="outline"
                      onClick={handleGenerateAllAudio}
                      className="justify-start"
                      disabled={isGeneratingAllAudio}
                    >
                      {isGeneratingAllAudio ? (
                        <>
                          <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                          Generating Audio...
                        </>
                      ) : (
                        <>
                          <Volume2 className="h-4 w-4 mr-2" />
                          Generate All Audio
                        </>
                      )}
                    </Button>
                    <Button
                      variant="outline"
                      onClick={handlePreflightCheck}
                      className="justify-start"
                      disabled={isRunningPreflightCheck}
                    >
                      {isRunningPreflightCheck ? (
                        <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                      ) : (
                        <CheckCircle className="h-4 w-4 mr-2" />
                      )}
                      Pre-flight Check
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => setIntroVideoModalOpen(true)}
                      className="justify-start"
                      disabled={!deckAnalysis}
                    >
                      <Video className="h-4 w-4 mr-2" />
                      Generate Intro Video
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => setVideoStoryModalOpen(true)}
                      className="justify-start"
                      disabled={!introVideo}
                    >
                      <Film className="h-4 w-4 mr-2" />
                      Generate Video Story
                    </Button>
                  </div>
                </div>
                
                {/* Show progress for audio generation */}
                {isGeneratingAllAudio && audioGenerationProgress.total > 0 && (
                  <div className="space-y-2 p-3 bg-muted/50 rounded-lg mt-3">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">
                        Audio Generation Progress
                      </span>
                      <span className="text-sm text-muted-foreground">
                        {Math.round((audioGenerationProgress.current / audioGenerationProgress.total) * 100)}%
                      </span>
                    </div>
                    <Progress value={(audioGenerationProgress.current / audioGenerationProgress.total) * 100} className="h-2" />
                    <div className="flex justify-between text-xs text-muted-foreground">
                      <span>{audioGenerationProgress.current} of {audioGenerationProgress.total} slides completed</span>
                      <span>Generating audio sequentially for better quality</span>
                    </div>
                  </div>
                )}
                
                {/* Show progress for active operations */}
                {(() => {
                  const inProgressStatuses = currentAnalysisStatuses.filter(s => s.state === 'IN_PROGRESS');
                  console.log('[AIAnalysisPanel] In-progress statuses:', inProgressStatuses);
                  console.log('[AIAnalysisPanel] All current statuses:', currentAnalysisStatuses);
                  
                  return inProgressStatuses.map((status, index) => {
                    const progress = status.totalItems > 0 ? 
                      ((status.completedItems + status.failedItems) / status.totalItems) * 100 : 0;
                    
                    const getAnalysisTypeLabel = (type: string) => {
                      switch (type) {
                        case 'ALL_SLIDES_ANALYSIS': return 'Slide Analysis';
                        case 'ALL_NARRATIVES_GENERATION': return 'Narrative Generation';
                        default: return type;
                      }
                    };
                    
                    console.log(`[AIAnalysisPanel] Rendering progress bar for ${status.analysisType}: ${progress}%`);
                    
                    return (
                      <div key={index} className="space-y-2 p-3 bg-muted/50 rounded-lg mt-3">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">
                            {getAnalysisTypeLabel(status.analysisType)} Progress
                          </span>
                          <span className="text-sm text-muted-foreground">
                            {Math.round(progress)}%
                          </span>
                        </div>
                        <Progress value={progress} className="h-2" />
                        <div className="flex justify-between text-xs text-muted-foreground">
                          <span>{status.completedItems} of {status.totalItems} completed</span>
                          {status.message && <span>{status.message}</span>}
                        </div>
                      </div>
                    );
                  });
                })()}
              </div>
              <div className="text-sm text-muted-foreground">
                <p>Note: These operations may take several minutes depending on the number of slides.</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="status" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>AI Processing Status</CardTitle>
              <CardDescription>
                Current status of AI analysis and content generation
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">Presentation Status:</span>
                  <Badge variant={processingStatus === 'COMPLETED' ? 'default' : 'secondary'}>
                    {processingStatus === 'COMPLETED' ? 'ANALYZED' : processingStatus}
                  </Badge>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium">Deck Analysis:</span>
                  <Badge variant={deckAnalysis ? 'default' : 'outline'}>
                    {deckAnalysis ? 'Complete' : 'Not Started'}
                  </Badge>
                </div>
                
                {/* Active Analysis Operations */}
                {currentAnalysisStatuses.length > 0 && (
                  <div className="space-y-3 pt-2 border-t">
                    <h4 className="text-sm font-medium">Active Operations:</h4>
                    {currentAnalysisStatuses.map((status, index) => {
                      const progress = status.totalItems > 0 ? 
                        ((status.completedItems + status.failedItems) / status.totalItems) * 100 : 0;
                      
                      const getStateVariant = (state: string) => {
                        switch (state) {
                          case 'COMPLETED': return 'default';
                          case 'FAILED': return 'destructive';
                          case 'IN_PROGRESS': return 'secondary';
                          default: return 'outline';
                        }
                      };
                      
                      const getAnalysisTypeLabel = (type: string) => {
                        switch (type) {
                          case 'ALL_SLIDES_ANALYSIS': return 'Slide Analysis';
                          case 'ALL_NARRATIVES_GENERATION': return 'Narrative Generation';
                          case 'DECK_ANALYSIS': return 'Deck Analysis';
                          default: return type;
                        }
                      };

                      return (
                        <div key={index} className="space-y-2 p-3 bg-muted/50 rounded-lg">
                          <div className="flex items-center justify-between">
                            <span className="text-sm font-medium">
                              {getAnalysisTypeLabel(status.analysisType)}
                            </span>
                            <Badge variant={getStateVariant(status.state)}>
                              {status.state}
                            </Badge>
                          </div>
                          
                          {status.state === 'IN_PROGRESS' && (
                            <div className="space-y-1">
                              <Progress value={progress} className="h-2" />
                              <div className="flex justify-between text-xs text-muted-foreground">
                                <span>{status.completedItems} of {status.totalItems} completed</span>
                                <span>{Math.round(progress)}%</span>
                              </div>
                              {status.message && (
                                <p className="text-xs text-muted-foreground">{status.message}</p>
                              )}
                            </div>
                          )}
                          
                          {status.state === 'FAILED' && status.errors.length > 0 && (
                            <div className="text-xs text-destructive">
                              <div className="flex items-center gap-1">
                                <AlertCircle className="h-3 w-3" />
                                <span>Errors occurred:</span>
                              </div>
                              <ul className="mt-1 space-y-1 ml-4">
                                {status.errors.slice(0, 3).map((error, i) => (
                                  <li key={i}>• {error}</li>
                                ))}
                                {status.errors.length > 3 && (
                                  <li>• ...and {status.errors.length - 3} more</li>
                                )}
                              </ul>
                            </div>
                          )}
                          
                          {status.state === 'COMPLETED' && (
                            <p className="text-xs text-muted-foreground">
                              {status.message || 'Operation completed successfully'}
                            </p>
                          )}
                        </div>
                      );
                    })}
                    
                    {currentAnalysisStatuses.some(s => s.state === 'IN_PROGRESS' || s.state === 'PENDING') && (
                      <div className="flex items-center gap-2 text-xs text-muted-foreground">
                        <Loader2 className="h-3 w-3 animate-spin" />
                        <span>Monitoring progress...</span>
                      </div>
                    )}
                  </div>
                )}

                <div className="text-sm text-muted-foreground mt-4">
                  <p>AI Features Status:</p>
                  <ul className="mt-2 space-y-1">
                    <li>• Deck Analysis: {deckAnalysis ? '✓' : '○'} {deckAnalysis ? 'Available' : 'Pending'}</li>
                    <li>• Slide Analysis: {processingStatus === 'INTENT_ANALYSIS_COMPLETE' || processingStatus === 'NARRATIVE_COMPLETE' || processingStatus === 'COMPLETED' ? '✓ Available' : '○ Pending'}</li>
                    <li>• Narrative Generation: {processingStatus === 'NARRATIVE_COMPLETE' || processingStatus === 'COMPLETED' ? '✓ Available' : '○ Pending'}</li>
                  </ul>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="narrative" className="space-y-4">
          <CompleteNarrativeTab 
            presentationId={presentationId}
            presentationTitle={presentationTitle || 'Presentation'}
          />
        </TabsContent>
      </Tabs>
      
      {/* Preflight Check Modal */}
      <PreflightCheckModal
        open={preflightModalOpen}
        onOpenChange={setPreflightModalOpen}
        presentationId={presentationId}
        onComplete={handlePreflightComplete}
      />
      
      {/* Intro Video Modal */}
      <IntroVideoModal
        open={introVideoModalOpen}
        onOpenChange={setIntroVideoModalOpen}
        presentationId={presentationId}
        deckAnalysis={deckAnalysis}
        onIntroVideoCreated={(video) => setIntroVideo(video)}
      />
      
      {/* Video Story Modal */}
      <VideoStoryModal
        open={videoStoryModalOpen}
        onOpenChange={setVideoStoryModalOpen}
        presentationId={presentationId}
        presentationTitle={presentationTitle || 'Presentation'}
        introVideo={introVideo}
      />
    </div>
  );
}