package ai.bluefields.ppt2video.service.rendering.msgraph;

import ai.bluefields.ppt2video.service.MSGraphService;
import ai.bluefields.ppt2video.service.rendering.RenderingStrategy;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MS Graph-based rendering strategy that uploads presentations to SharePoint, converts to PDF, and
 * extracts slides.
 */
@Component("MSGRAPH")
@ConditionalOnProperty(name = "app.msgraph.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class MSGraphRenderingStrategy implements RenderingStrategy {

  private final MSGraphService msGraphService;
  private final MSGraphCacheManager cacheManager;
  private final PdfSlideExtractor pdfExtractor;

  private String uploadedFileId;
  private byte[] cachedPdfData;

  @Override
  public void prepareForRendering(File presentationFile) throws IOException {
    log.info("Preparing MS Graph rendering for file: {}", presentationFile.getName());

    try {
      // 1. Upload to SharePoint (once)
      uploadedFileId =
          msGraphService.uploadToOneDrive(
              presentationFile.getName(), Files.readAllBytes(presentationFile.toPath()));

      log.info("Uploaded file to SharePoint with ID: {}", uploadedFileId);

      // 2. Convert to PDF (once)
      cachedPdfData = msGraphService.convertToPdf(uploadedFileId);

      log.info("Successfully converted to PDF, size: {} bytes", cachedPdfData.length);

      // 3. Cache PDF data
      String cacheKey = cacheManager.generateCacheKey(presentationFile);
      cacheManager.cachePdf(cacheKey, cachedPdfData);

    } catch (Exception e) {
      log.error("Failed to prepare MS Graph rendering: {}", e.getMessage());
      throw new IOException("MS Graph preparation failed", e);
    }
  }

  @Override
  public RenderedSlide renderSlide(int slideNumber, int width, int height) throws IOException {
    if (cachedPdfData == null) {
      throw new IllegalStateException("Renderer not prepared - call prepareForRendering first");
    }

    try {
      // Extract single slide from cached PDF
      BufferedImage slideImage =
          pdfExtractor.extractPage(cachedPdfData, slideNumber, width, height);

      return new RenderedSlide(slideImage, width, height);

    } catch (Exception e) {
      log.error("Failed to render slide {}: {}", slideNumber, e.getMessage());
      throw new IOException("Failed to render slide " + slideNumber, e);
    }
  }

  @Override
  public void cleanup() {
    try {
      // Delete from OneDrive if uploaded
      if (uploadedFileId != null) {
        msGraphService.deleteFromOneDrive(uploadedFileId);
        log.info("Cleaned up file from OneDrive: {}", uploadedFileId);
      }
    } catch (Exception e) {
      log.warn("Failed to cleanup OneDrive file: {}", e.getMessage());
    }

    // Clear cached data
    uploadedFileId = null;
    cachedPdfData = null;
  }

  @Override
  public String getStrategyName() {
    return "MS Graph SharePoint/OneDrive Renderer";
  }
}
