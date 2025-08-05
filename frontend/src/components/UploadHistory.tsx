import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { 
  FileText, 
  Calendar, 
  MoreVertical, 
  Eye, 
  Trash2, 
  RefreshCw,
  AlertCircle,
  CheckCircle,
  Clock,
  Loader2
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useToast } from "@/hooks/use-toast";
import { apiService, ApiError } from '@/services/api';
import { usePresentationStore } from '@/store/presentationStore';
import { Presentation, ProcessingStatus } from '@/types/presentation';

interface UploadHistoryProps {
  className?: string;
}

export function UploadHistory({ className }: UploadHistoryProps) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>('');
  const { toast } = useToast();
  const { presentations, setCurrentPresentation, removePresentation, setPresentations } = usePresentationStore();

  const loadPresentations = useCallback(async () => {
    setLoading(true);
    setError('');
    
    try {
      const data = await apiService.getPresentations();
      // Update the store with fetched presentations
      setPresentations(data);
      console.log('Fetched presentations:', data);
    } catch (err) {
      const errorMsg = err instanceof ApiError ? err.message : 'Failed to load presentations';
      setError(errorMsg);
      toast({
        title: "Error loading presentations",
        description: errorMsg,
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, [toast, setPresentations]);

  useEffect(() => {
    loadPresentations();
  }, [loadPresentations]);

  const handleDelete = async (presentation: Presentation) => {
    try {
      await apiService.deletePresentation(presentation.id);
      removePresentation(presentation.id);
      toast({
        title: "Presentation deleted",
        description: `${presentation.originalFilename} has been deleted`,
      });
    } catch (err) {
      const errorMsg = err instanceof ApiError ? err.message : 'Failed to delete presentation';
      toast({
        title: "Delete failed",
        description: errorMsg,
        variant: "destructive"
      });
    }
  };

  const handleView = (presentation: Presentation) => {
    setCurrentPresentation(presentation);
    toast({
      title: "Presentation selected",
      description: `Viewing ${presentation.originalFilename}`,
    });
  };

  const formatFileSize = (bytes: number): string => {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { 
      hour: '2-digit', 
      minute: '2-digit' 
    });
  };

  const getStatusBadge = (status: ProcessingStatus) => {
    const statusConfig = {
      UPLOADED: { 
        variant: 'secondary' as const, 
        icon: Clock, 
        label: 'Uploaded' 
      },
      PARSING: { 
        variant: 'default' as const, 
        icon: Loader2, 
        label: 'Parsing' 
      },
      PARSED: { 
        variant: 'outline' as const, 
        icon: CheckCircle, 
        label: 'Parsed' 
      },
      RENDERING: { 
        variant: 'default' as const, 
        icon: Loader2, 
        label: 'Rendering' 
      },
      COMPLETED: { 
        variant: 'default' as const, 
        icon: CheckCircle, 
        label: 'Completed' 
      },
      FAILED: { 
        variant: 'destructive' as const, 
        icon: AlertCircle, 
        label: 'Failed' 
      }
    };

    const config = statusConfig[status];
    const Icon = config.icon;

    return (
      <Badge variant={config.variant} className="flex items-center space-x-1">
        <Icon className={`h-3 w-3 ${
          status === 'PARSING' || status === 'RENDERING' 
            ? 'animate-spin' 
            : ''
        }`} />
        <span>{config.label}</span>
      </Badge>
    );
  };

  if (loading) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <FileText className="h-5 w-5" />
            <span>Upload History</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-muted-foreground">Loading presentations...</span>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <FileText className="h-5 w-5" />
            <span>Upload History</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-8 space-y-4">
            <AlertCircle className="h-8 w-8 text-red-500" />
            <div className="text-center">
              <p className="text-sm text-red-600 mb-2">{error}</p>
              <Button 
                onClick={loadPresentations} 
                variant="outline" 
                size="sm"
              >
                <RefreshCw className="h-4 w-4 mr-1" />
                Retry
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="flex items-center space-x-2">
          <FileText className="h-5 w-5" />
          <span>Upload History</span>
          <Badge variant="secondary">{presentations.length}</Badge>
        </CardTitle>
        <Button 
          onClick={loadPresentations} 
          variant="ghost" 
          size="sm"
          className="text-muted-foreground hover:text-foreground"
        >
          <RefreshCw className="h-4 w-4" />
        </Button>
      </CardHeader>
      <CardContent>
        {presentations.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 space-y-4">
            <FileText className="h-12 w-12 text-muted-foreground" />
            <div className="text-center">
              <p className="text-lg font-medium text-muted-foreground">No presentations yet</p>
              <p className="text-sm text-muted-foreground">
                Upload your first PowerPoint file to get started
              </p>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {presentations
              .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
              .map((presentation) => (
              <div 
                key={presentation.id}
                className="flex items-center justify-between p-4 border rounded-lg hover:bg-muted/30 transition-colors"
              >
                <div className="flex items-center space-x-4 flex-1">
                  <div className="relative">
                    <FileText className="h-8 w-8 text-primary" />
                    {presentation.processingStatus === 'COMPLETED' && (
                      <CheckCircle className="h-4 w-4 text-green-600 absolute -top-1 -right-1 bg-white rounded-full" />
                    )}
                    {presentation.processingStatus === 'FAILED' && (
                      <AlertCircle className="h-4 w-4 text-red-600 absolute -top-1 -right-1 bg-white rounded-full" />
                    )}
                  </div>
                  
                  <div className="flex-1 min-w-0">
                    <p className="font-medium truncate">{presentation.title}</p>
                    <div className="flex items-center space-x-4 text-sm text-muted-foreground">
                      <span className="flex items-center space-x-1">
                        <Calendar className="h-3 w-3" />
                        <span>{formatDate(presentation.createdAt)}</span>
                      </span>
                      <span>{formatFileSize(presentation.fileSize)}</span>
                    </div>
                  </div>
                  
                  <div className="flex items-center space-x-3">
                    {getStatusBadge(presentation.processingStatus)}
                    
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="sm">
                          <MoreVertical className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleView(presentation)}>
                          <Eye className="h-4 w-4 mr-2" />
                          View Details
                        </DropdownMenuItem>
                        <DropdownMenuItem 
                          onClick={() => handleDelete(presentation)}
                          className="text-red-600 hover:text-red-700"
                        >
                          <Trash2 className="h-4 w-4 mr-2" />
                          Delete
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}