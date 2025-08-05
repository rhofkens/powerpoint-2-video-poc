import { useState, useCallback, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Upload, FileText, X, AlertCircle, CheckCircle } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { apiService, ApiError } from '@/services/api';
import { PresentationUploadResponse, UploadProgress } from '@/types/presentation';

interface FileUploadProps {
  onFileUploaded?: (response: PresentationUploadResponse) => void;
}

type UploadState = 'idle' | 'uploading' | 'success' | 'error';

export function FileUpload({ onFileUploaded }: FileUploadProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadState, setUploadState] = useState<UploadState>('idle');
  const [uploadProgress, setUploadProgress] = useState<UploadProgress | null>(null);
  const [errorMessage, setErrorMessage] = useState<string>('');
  const [maxFileSize, setMaxFileSize] = useState(250 * 1024 * 1024); // Default 250MB
  const [maxFileSizeMB, setMaxFileSizeMB] = useState(250);
  const { toast } = useToast();
  
  // Fetch configuration on component mount
  useEffect(() => {
    fetch('http://localhost:8080/api/config')
      .then(res => res.json())
      .then(config => {
        setMaxFileSize(config.maxFileSizeBytes);
        setMaxFileSizeMB(config.maxFileSizeMB);
      })
      .catch(err => {
        console.error('Failed to fetch config:', err);
      });
  }, []);

  const validateFile = useCallback((file: File): string | null => {
    // Check file type
    if (!file.name.toLowerCase().endsWith('.pptx')) {
      return 'Please upload a PowerPoint (.pptx) file';
    }

    // Check file size
    if (file.size > maxFileSize) {
      const sizeMB = Math.round(file.size / (1024 * 1024));
      return `File size (${sizeMB}MB) exceeds the ${maxFileSizeMB}MB limit`;
    }

    return null;
  }, [maxFileSize, maxFileSizeMB]);

  const handleFileSelect = useCallback((files: File[]) => {
    const file = files[0];
    if (!file) return;

    const validationError = validateFile(file);
    if (validationError) {
      toast({
        title: "Invalid file",
        description: validationError,
        variant: "destructive"
      });
      return;
    }

    setSelectedFile(file);
    setUploadState('idle');
    setErrorMessage('');
    setUploadProgress(null);
  }, [validateFile, toast]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: handleFileSelect,
    accept: {
      'application/vnd.openxmlformats-officedocument.presentationml.presentation': ['.pptx']
    },
    multiple: false,
    disabled: uploadState === 'uploading'
  });

  const handleUpload = async () => {
    if (!selectedFile || uploadState === 'uploading') return;

    setUploadState('uploading');
    setErrorMessage('');
    setUploadProgress(null);

    try {
      const response = await apiService.uploadPresentation(
        selectedFile,
        (progress: UploadProgress) => {
          setUploadProgress(progress);
        }
      );

      setUploadState('success');

      toast({
        title: "Upload successful",
        description: `${selectedFile.name} has been uploaded and is being processed`,
      });

      // Call callback if provided - let the parent handle adding to store
      onFileUploaded?.(response);

    } catch (error) {
      setUploadState('error');
      
      let errorMsg = 'Upload failed. Please try again.';
      if (error instanceof ApiError) {
        errorMsg = error.message;
      } else if (error instanceof Error) {
        errorMsg = error.message;
      }
      
      setErrorMessage(errorMsg);
      
      toast({
        title: "Upload failed",
        description: errorMsg,
        variant: "destructive"
      });
    }
  };

  const handleRetry = () => {
    setUploadState('idle');
    setErrorMessage('');
    setUploadProgress(null);
  };

  const clearFile = () => {
    setSelectedFile(null);
    setUploadState('idle');
    setErrorMessage('');
    setUploadProgress(null);
  };

  const formatFileSize = (bytes: number): string => {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  };

  const formatSpeed = (speed: number): string => {
    if (speed < 1024) return `${speed.toFixed(0)} B/s`;
    if (speed < 1024 * 1024) return `${(speed / 1024).toFixed(1)} KB/s`;
    return `${(speed / (1024 * 1024)).toFixed(1)} MB/s`;
  };

  const formatTime = (seconds: number): string => {
    if (seconds < 60) return `${Math.round(seconds)}s`;
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = Math.round(seconds % 60);
    return `${minutes}m ${remainingSeconds}s`;
  };

  return (
    <div className="space-y-6">
      <div className="text-center">
        <h2 className="text-2xl font-bold mb-2">Upload Your Presentation</h2>
        <p className="text-muted-foreground">
          Upload a PowerPoint (.pptx) file to begin analysis (max {maxFileSizeMB}MB)
        </p>
      </div>

      <Card 
        {...getRootProps()}
        className={`p-8 border-2 border-dashed transition-all duration-300 cursor-pointer ${
          isDragActive 
            ? 'border-primary bg-primary/5 scale-105' 
            : uploadState === 'uploading'
            ? 'border-muted bg-muted/20'
            : 'border-border hover:border-primary/50'
        } ${uploadState === 'uploading' ? 'cursor-not-allowed' : 'cursor-pointer'}`}
      >
        <input {...getInputProps()} type="file" hidden />
        
        {selectedFile ? (
          <div className="space-y-4">
            {/* File Info */}
            <div className="flex items-center justify-center space-x-3">
              <div className="relative">
                <FileText className="h-8 w-8 text-primary" />
                {uploadState === 'success' && (
                  <CheckCircle className="h-4 w-4 text-green-600 absolute -top-1 -right-1 bg-white rounded-full" />
                )}
                {uploadState === 'error' && (
                  <AlertCircle className="h-4 w-4 text-red-600 absolute -top-1 -right-1 bg-white rounded-full" />
                )}
              </div>
              <div className="text-left flex-1">
                <p className="font-medium">{selectedFile.name}</p>
                <p className="text-sm text-muted-foreground">
                  {formatFileSize(selectedFile.size)}
                </p>
              </div>
              {uploadState !== 'uploading' && (
                <Button 
                  variant="ghost" 
                  size="sm" 
                  onClick={(e) => {
                    e.stopPropagation();
                    clearFile();
                  }}
                  className="text-destructive hover:text-destructive"
                >
                  <X className="h-4 w-4" />
                </Button>
              )}
            </div>

            {/* Upload Progress */}
            {uploadState === 'uploading' && uploadProgress && (
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span>Uploading...</span>
                  <span>{uploadProgress.percentage}%</span>
                </div>
                <Progress value={uploadProgress.percentage} className="w-full" />
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>{formatSpeed(uploadProgress.speed)}</span>
                  {uploadProgress.estimatedTime && (
                    <span>{formatTime(uploadProgress.estimatedTime)} remaining</span>
                  )}
                </div>
              </div>
            )}

            {/* Error Message */}
            {uploadState === 'error' && errorMessage && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-md">
                <div className="flex items-center space-x-2">
                  <AlertCircle className="h-4 w-4 text-red-600" />
                  <p className="text-sm text-red-800">{errorMessage}</p>
                </div>
              </div>
            )}

            {/* Success Message */}
            {uploadState === 'success' && (
              <div className="p-3 bg-green-50 border border-green-200 rounded-md">
                <div className="flex items-center space-x-2">
                  <CheckCircle className="h-4 w-4 text-green-600" />
                  <p className="text-sm text-green-800">
                    Upload successful! Your presentation is being processed.
                  </p>
                </div>
              </div>
            )}

            {/* Action Buttons */}
            <div className="flex space-x-2">
              {uploadState === 'idle' && (
                <Button 
                  onClick={(e) => {
                    e.stopPropagation();
                    handleUpload();
                  }}
                  className="flex-1"
                >
                  Start Upload
                </Button>
              )}
              
              {uploadState === 'error' && (
                <Button 
                  onClick={(e) => {
                    e.stopPropagation();
                    handleRetry();
                  }}
                  className="flex-1"
                  variant="outline"
                >
                  Try Again
                </Button>
              )}
              
              {uploadState === 'success' && (
                <Button 
                  onClick={(e) => {
                    e.stopPropagation();
                    clearFile();
                  }}
                  className="flex-1"
                  variant="outline"
                >
                  Upload Another
                </Button>
              )}
            </div>
          </div>
        ) : (
          <div className="text-center space-y-4">
            <Upload className={`h-12 w-12 mx-auto transition-colors ${
              isDragActive ? 'text-primary' : 'text-muted-foreground'
            }`} />
            <div>
              <p className="text-lg font-medium">
                {isDragActive ? 'Drop your file here' : 'Drop your PowerPoint file here'}
              </p>
              <p className="text-muted-foreground">or click to browse</p>
              <p className="text-xs text-muted-foreground mt-1">
                Supports .pptx files up to {maxFileSizeMB}MB
              </p>
            </div>
            {!isDragActive && (
              <Button variant="outline" className="cursor-pointer">
                Browse Files
              </Button>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}