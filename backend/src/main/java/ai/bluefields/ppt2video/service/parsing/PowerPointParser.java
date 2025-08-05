package ai.bluefields.ppt2video.service.parsing;

import ai.bluefields.ppt2video.entity.Presentation;
import ai.bluefields.ppt2video.entity.Slide;
import ai.bluefields.ppt2video.exception.PowerPointParsingException;
import java.io.File;
import java.util.List;

/**
 * Interface for PowerPoint parsing services. Implementations can use different libraries (POI,
 * Aspose, etc.) to parse presentations.
 */
public interface PowerPointParser {

  /**
   * Parses a PowerPoint presentation and extracts all slides with their content.
   *
   * @param filePath the path to the PPTX file
   * @param presentation the presentation entity to associate slides with
   * @return list of parsed slides
   * @throws PowerPointParsingException if parsing fails
   */
  List<Slide> parsePresentation(String filePath, Presentation presentation)
      throws PowerPointParsingException;

  /**
   * Parses a PowerPoint presentation from a File object.
   *
   * @param file the PowerPoint file
   * @param presentation the presentation entity to associate slides with
   * @return list of parsed slides
   * @throws PowerPointParsingException if parsing fails
   */
  default List<Slide> parsePresentation(File file, Presentation presentation)
      throws PowerPointParsingException {
    return parsePresentation(file.getAbsolutePath(), presentation);
  }

  /**
   * Checks if the parser is available and can be used.
   *
   * @return true if available, false otherwise
   */
  boolean isAvailable();

  /**
   * Gets the name of this parser.
   *
   * @return the parser name
   */
  String getParserName();
}
