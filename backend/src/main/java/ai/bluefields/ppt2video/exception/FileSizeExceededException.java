package ai.bluefields.ppt2video.exception;

/** Exception thrown when an uploaded file exceeds the maximum allowed size. */
public class FileSizeExceededException extends RuntimeException {

  public FileSizeExceededException(String message) {
    super(message);
  }

  public FileSizeExceededException(String message, Throwable cause) {
    super(message, cause);
  }
}
