package ai.bluefields.ppt2video.exception;

/** Exception thrown when there's an error parsing a PowerPoint presentation file. */
public class PowerPointParsingException extends RuntimeException {

  public PowerPointParsingException(String message) {
    super(message);
  }

  public PowerPointParsingException(String message, Throwable cause) {
    super(message, cause);
  }
}
