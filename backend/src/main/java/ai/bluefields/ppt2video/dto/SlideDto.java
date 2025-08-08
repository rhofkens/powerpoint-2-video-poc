package ai.bluefields.ppt2video.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for slide information without circular references. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlideDto {

  private String id;
  private String presentationId;
  private Integer slideNumber;
  private String title;
  private String content;
  private String contentText;
  private String speakerNotes;
  private String layoutType;
  private String imagePath;
  private LocalDateTime imageGeneratedAt;
  private Integer imageWidth;
  private Integer imageHeight;
  private String renderingStatus;
  private String renderingErrorMessage;
  private String audioPath;
  private String videoPath;
  private String generatedNarrative;
  private Integer contentWordCount;
  private String processingStatus;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private SlideAnalysisDto slideAnalysis;
  private SlideNarrativeDto slideNarrative;
}
