package ai.bluefields.ppt2video.exception;

/** Exception thrown when an uploaded file is not in the expected PPTX format. */
public class InvalidFileFormatException extends RuntimeException {

  public InvalidFileFormatException(String message) {
    super(message);
  }

  public InvalidFileFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
