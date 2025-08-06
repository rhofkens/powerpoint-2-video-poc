package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.exception.FileSizeExceededException;
import ai.bluefields.ppt2video.exception.InvalidFileFormatException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for handling file storage operations including validation, unique naming, and secure
 * storage of uploaded PowerPoint presentations.
 */
@Service
@Slf4j
public class FileStorageService {

  private static final String PPTX_MIME_TYPE =
      "application/vnd.openxmlformats-officedocument.presentationml.presentation";

  @Value("${app.storage.presentations-path}")
  private String presentationsPath;

  @Value("${app.upload.max-file-size-bytes:262144000}")
  private long maxFileSizeBytes;

  /**
   * Validates and stores an uploaded PowerPoint file.
   *
   * @param file the uploaded multipart file
   * @return the absolute path where the file was stored
   * @throws FileSizeExceededException if the file exceeds the size limit
   * @throws InvalidFileFormatException if the file is not a valid PPTX file
   */
  public String storeFile(MultipartFile file) {
    log.info("Starting file storage process for file: {}", file.getOriginalFilename());

    validateFile(file);

    try {
      // Create presentations directory if it doesn't exist
      Path presentationsDir = Paths.get(presentationsPath);
      Files.createDirectories(presentationsDir);

      // Generate unique filename
      String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());
      Path targetPath = presentationsDir.resolve(uniqueFilename);

      // Store the file
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

      String storedPath = targetPath.toAbsolutePath().toString();
      log.info("File stored successfully at: {}", storedPath);

      return storedPath;

    } catch (IOException e) {
      log.error("Failed to store file: {}", file.getOriginalFilename(), e);
      throw new RuntimeException("Failed to store file", e);
    }
  }

  /**
   * Validates the uploaded file for size and MIME type.
   *
   * @param file the file to validate
   * @throws FileSizeExceededException if file size exceeds limit
   * @throws InvalidFileFormatException if file is not PPTX format
   */
  private void validateFile(MultipartFile file) {
    // Validate file size
    if (file.getSize() > maxFileSizeBytes) {
      log.warn("File size exceeded: {} bytes (max: {} bytes)", file.getSize(), maxFileSizeBytes);
      throw new FileSizeExceededException(
          String.format(
              "File size %d bytes exceeds maximum allowed size of %d bytes",
              file.getSize(), maxFileSizeBytes));
    }

    // Validate MIME type
    String contentType = file.getContentType();
    if (!PPTX_MIME_TYPE.equals(contentType)) {
      log.warn("Invalid file format: {} (expected: {})", contentType, PPTX_MIME_TYPE);
      throw new InvalidFileFormatException(
          String.format("Invalid file format: %s. Only PPTX files are supported.", contentType));
    }

    // Validate filename extension
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pptx")) {
      log.warn("Invalid file extension for file: {}", originalFilename);
      throw new InvalidFileFormatException(
          "Invalid file extension. Only .pptx files are supported.");
    }

    log.info("File validation successful for: {}", originalFilename);
  }

  /**
   * Generates a unique filename using UUID while preserving the original extension.
   *
   * @param originalFilename the original filename
   * @return unique filename with UUID prefix
   */
  private String generateUniqueFilename(String originalFilename) {
    String uuid = UUID.randomUUID().toString();
    String extension = getFileExtension(originalFilename);
    return uuid + "." + extension;
  }

  /**
   * Extracts the file extension from a filename.
   *
   * @param filename the filename
   * @return the file extension without the dot
   */
  private String getFileExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "";
    }
    int lastDotIndex = filename.lastIndexOf('.');
    return lastDotIndex > 0 ? filename.substring(lastDotIndex + 1) : "";
  }

  /**
   * Creates a directory for storing presentation-related files.
   *
   * @param presentationId the presentation UUID
   * @return the path to the created directory
   */
  public Path createPresentationDirectory(UUID presentationId) {
    try {
      Path presentationDir = Paths.get(presentationsPath, presentationId.toString());
      Files.createDirectories(presentationDir);
      Files.createDirectories(presentationDir.resolve("slides"));
      Files.createDirectories(presentationDir.resolve("images"));
      Files.createDirectories(presentationDir.resolve("audio"));
      Files.createDirectories(presentationDir.resolve("videos"));

      log.info("Created presentation directory structure at: {}", presentationDir);
      return presentationDir;

    } catch (IOException e) {
      log.error("Failed to create presentation directory for ID: {}", presentationId, e);
      throw new RuntimeException("Failed to create presentation directory", e);
    }
  }

  /**
   * Stores a JSON string to a file in the prompts directory.
   *
   * @param presentationId the presentation UUID (optional)
   * @param serviceName the name of the service generating the prompt
   * @param filename the filename to use
   * @param jsonContent the JSON content to store
   * @return the path where the file was stored
   */
  public Path storePromptJson(
      UUID presentationId, String serviceName, String filename, String jsonContent) {
    try {
      Path promptsDir;
      if (presentationId != null) {
        // Store with presentation: storage/presentations/{presentationId}/prompts/{serviceName}/
        promptsDir =
            Paths.get(
                presentationsPath, presentationId.toString(), "prompts", serviceName.toLowerCase());
      } else {
        // Store globally: storage/prompts/{serviceName}/
        promptsDir =
            Paths.get(presentationsPath, "..", "prompts", serviceName.toLowerCase()).normalize();
      }

      Files.createDirectories(promptsDir);
      Path filePath = promptsDir.resolve(filename);
      Files.writeString(filePath, jsonContent);

      log.debug("Stored prompt JSON at: {}", filePath);
      return filePath;

    } catch (IOException e) {
      log.error("Failed to store prompt JSON: {}", e.getMessage());
      throw new RuntimeException("Failed to store prompt JSON", e);
    }
  }

  /**
   * Stores a presentation file with a specific ID.
   *
   * @param presentationId the presentation UUID
   * @param file the uploaded file
   * @return the absolute path where the file was stored
   * @throws IOException if storage fails
   */
  public String storePresentation(UUID presentationId, MultipartFile file) throws IOException {
    log.info(
        "Storing presentation {} with filename: {}", presentationId, file.getOriginalFilename());

    try {
      // Create presentation directory
      Path presentationDir = createPresentationDirectory(presentationId);

      // Store the file with original extension
      String extension = getFileExtension(file.getOriginalFilename());
      String filename = presentationId + "." + extension;
      Path targetPath = presentationDir.resolve(filename);

      // Store the file
      Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

      String storedPath = targetPath.toAbsolutePath().toString();
      log.info("Presentation stored successfully at: {}", storedPath);

      return storedPath;

    } catch (IOException e) {
      log.error("Failed to store presentation: {}", presentationId, e);
      throw e;
    }
  }
}
