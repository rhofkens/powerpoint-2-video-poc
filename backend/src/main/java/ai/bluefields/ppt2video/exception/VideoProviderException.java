package ai.bluefields.ppt2video.exception;

/** Exception thrown when video provider operations fail. */
public class VideoProviderException extends RuntimeException {

  public VideoProviderException(String message) {
    super(message);
  }

  public VideoProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
