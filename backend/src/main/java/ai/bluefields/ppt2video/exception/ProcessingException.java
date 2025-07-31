package ai.bluefields.ppt2video.exception;

/**
 * Custom exception for presentation processing errors. Used to wrap various processing-related
 * exceptions with meaningful messages.
 */
public class ProcessingException extends RuntimeException {

  public ProcessingException(String message) {
    super(message);
  }

  public ProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
