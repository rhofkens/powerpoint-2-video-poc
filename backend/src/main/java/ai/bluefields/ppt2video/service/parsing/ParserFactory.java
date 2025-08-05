package ai.bluefields.ppt2video.service.parsing;

import ai.bluefields.ppt2video.service.parsing.aspose.AsposePowerPointParser;
import ai.bluefields.ppt2video.service.parsing.poi.POIPowerPointParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting the appropriate PowerPoint parser based on availability and configuration.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParserFactory {

  @Value("${app.parser.priority:ASPOSE,POI}")
  private String parserPriority;

  @Autowired(required = false)
  private AsposePowerPointParser asposeParser;

  @Autowired private POIPowerPointParser poiParser;

  /**
   * Gets the best available parser based on priority and availability.
   *
   * @return the selected parser
   * @throws IllegalStateException if no parser is available
   */
  public PowerPointParser getParser() {
    // Parse priority list
    String[] priorities = parserPriority.split(",");
    log.info("Parser priority order: {}", parserPriority);

    for (String priority : priorities) {
      PowerPointParser parser = getParserByName(priority.trim());
      if (parser != null && parser.isAvailable()) {
        log.info("Selected parser: {}", parser.getParserName());
        return parser;
      }
    }

    // Fallback to POI parser if available
    if (poiParser != null) {
      log.warn("Using POI parser as fallback");
      return poiParser;
    }

    throw new IllegalStateException("No PowerPoint parser available");
  }

  /**
   * Gets a specific parser by name.
   *
   * @param name the parser name
   * @return the parser or null if not found
   */
  private PowerPointParser getParserByName(String name) {
    switch (name.toUpperCase()) {
      case "ASPOSE":
        return asposeParser;
      case "POI":
        return poiParser;
      default:
        log.debug("Unknown parser name: {}", name);
        return null;
    }
  }
}
