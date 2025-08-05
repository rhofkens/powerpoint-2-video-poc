package ai.bluefields.ppt2video.exception;

/** Exception thrown when slide rendering fails. */
public class RenderingException extends Exception {

  public RenderingException(String message) {
    super(message);
  }

  public RenderingException(String message, Throwable cause) {
    super(message, cause);
  }

  public RenderingException(Throwable cause) {
    super(cause);
  }
}
