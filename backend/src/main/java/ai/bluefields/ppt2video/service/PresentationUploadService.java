package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.dto.PresentationUploadResponseDto;
import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.exception.FileSizeExceededException;
import ai.bluefields.ppt2video.exception.InvalidFileFormatException;
import ai.bluefields.ppt2video.repository.PresentationRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service responsible for handling PowerPoint file uploads only. This service follows the separated
 * architecture where upload, parsing, and rendering are independent activities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresentationUploadService {

  private final PresentationRepository presentationRepository;
  private final PresentationParsingService parsingService;
  private final FileStorageService fileStorageService;

  @Value("${app.upload.max-file-size-bytes:262144000}")
  private long maxFileSize;

  /**
   * Handles PowerPoint file upload only. Does not perform parsing or rendering.
   *
   * @param file the uploaded PowerPoint file
   * @return response with presentation ID and upload status
   * @throws InvalidFileFormatException if file is not a valid PowerPoint format
   * @throws FileSizeExceededException if file exceeds size limit
   * @throws IOException if file storage fails
   */
  @Transactional
  public PresentationUploadResponseDto uploadPresentation(MultipartFile file)
      throws InvalidFileFormatException, FileSizeExceededException, IOException {

    log.info("Starting file upload: {}", file.getOriginalFilename());

    // 1. Validate file
    validateFile(file);

    // 2. Create database record with UPLOADED status first
    Presentation presentation = new Presentation();
    // Don't set ID - let Hibernate generate it
    presentation.setTitle(extractTitle(file.getOriginalFilename()));
    presentation.setOriginalFilename(file.getOriginalFilename());
    presentation.setFileSize(file.getSize());
    presentation.setUploadTimestamp(LocalDateTime.now());
    presentation.setStatus(Presentation.Status.UPLOADED);
    presentation.setProcessingStatus(Presentation.ProcessingStatus.UPLOADED);
    // @CreationTimestamp and @UpdateTimestamp will handle timestamps automatically

    // Save to get the generated ID
    presentation = presentationRepository.save(presentation);

    // 3. Store file on disk using the generated ID
    String storedFilePath = fileStorageService.storePresentation(presentation.getId(), file);

    // Update with file path
    presentation.setFilePath(storedFilePath);
    presentation = presentationRepository.save(presentation);

    // 4. Trigger async parsing after transaction commits
    // This ensures the presentation is visible to the async thread
    final UUID presentationId = presentation.getId();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            // Rendering will be triggered automatically after parsing completes
            parsingService.parsePresentation(presentationId);
          }
        });

    // 5. Return immediately
    PresentationUploadResponseDto response = new PresentationUploadResponseDto();
    response.setId(presentation.getId().toString());
    response.setOriginalFilename(presentation.getOriginalFilename());
    response.setFileSize(presentation.getFileSize());
    response.setStatus(presentation.getStatus().name());
    response.setUploadTimestamp(presentation.getUploadTimestamp());
    response.setMessage("File uploaded successfully. Processing started.");

    log.info("File upload completed. Presentation ID: {}", presentation.getId());
    return response;
  }

  /** Validates the uploaded file. */
  private void validateFile(MultipartFile file)
      throws InvalidFileFormatException, FileSizeExceededException {

    // Check if file is empty
    if (file.isEmpty()) {
      throw new InvalidFileFormatException("File is empty");
    }

    // Check file size
    if (file.getSize() > maxFileSize) {
      throw new FileSizeExceededException(
          String.format(
              "File size %d exceeds maximum allowed size %d", file.getSize(), maxFileSize));
    }

    // Check file extension
    String filename = file.getOriginalFilename();
    if (filename == null
        || (!filename.toLowerCase().endsWith(".pptx")
            && !filename.toLowerCase().endsWith(".ppt"))) {
      throw new InvalidFileFormatException(
          "Invalid file format. Only .pptx and .ppt files are allowed");
    }

    // Check content type
    String contentType = file.getContentType();
    if (contentType == null
        || (!contentType.equals(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation")
            && !contentType.equals("application/vnd.ms-powerpoint"))) {
      log.warn("Unexpected content type: {} for file: {}", contentType, filename);
    }
  }

  /** Extracts a title from the filename. */
  private String extractTitle(String filename) {
    if (filename == null) {
      return "Untitled Presentation";
    }

    // Remove extension
    int lastDot = filename.lastIndexOf('.');
    if (lastDot > 0) {
      filename = filename.substring(0, lastDot);
    }

    // Clean up the title
    return filename.replaceAll("[^a-zA-Z0-9\\s\\-_]", " ").trim();
  }
}
