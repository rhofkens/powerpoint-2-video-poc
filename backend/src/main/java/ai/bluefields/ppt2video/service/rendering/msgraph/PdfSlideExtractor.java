package ai.bluefields.ppt2video.service.rendering.msgraph;

import ai.bluefields.ppt2video.exception.RenderingException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting slides from PDF documents. Handles PDF rendering and page extraction
 * operations.
 */
@Component
@ConditionalOnProperty(name = "app.msgraph.enabled", havingValue = "true")
@Slf4j
public class PdfSlideExtractor {

  private static final float DEFAULT_DPI = 150f;
  private static final float BASE_DPI = 72f;

  /**
   * Extracts a specific slide/page from PDF data.
   *
   * @param pdfData the PDF data
   * @param pageNumber the page number (1-based)
   * @param targetWidth desired width
   * @param targetHeight desired height
   * @return the extracted image
   * @throws IOException if PDF processing fails
   * @throws RenderingException if page number is invalid
   */
  public BufferedImage extractPage(
      byte[] pdfData, int pageNumber, int targetWidth, int targetHeight)
      throws IOException, RenderingException {
    return extractPage(pdfData, pageNumber, targetWidth, targetHeight, DEFAULT_DPI);
  }

  /**
   * Extracts a specific slide/page from PDF data with custom DPI.
   *
   * @param pdfData the PDF data
   * @param pageNumber the page number (1-based)
   * @param targetWidth desired width
   * @param targetHeight desired height
   * @param dpi the DPI to use for rendering
   * @return the extracted image
   * @throws IOException if PDF processing fails
   * @throws RenderingException if page number is invalid
   */
  public BufferedImage extractPage(
      byte[] pdfData, int pageNumber, int targetWidth, int targetHeight, float dpi)
      throws IOException, RenderingException {

    if (pdfData == null || pdfData.length == 0) {
      throw new RenderingException("PDF data is null or empty");
    }

    try (PDDocument document = Loader.loadPDF(pdfData)) {
      int totalPages = document.getNumberOfPages();

      if (pageNumber < 1 || pageNumber > totalPages) {
        throw new RenderingException(
            String.format("Invalid page number %d. PDF has %d pages", pageNumber, totalPages));
      }

      PDFRenderer pdfRenderer = new PDFRenderer(document);

      // Calculate scale factor to achieve target resolution
      float scale = calculateOptimalScale(targetWidth, targetHeight, dpi);

      log.debug("Extracting page {} with scale factor: {}", pageNumber, scale);

      // Render the page (0-based index for PDFBox)
      BufferedImage image = pdfRenderer.renderImage(pageNumber - 1, scale);

      log.debug(
          "Rendered page {} at {}x{} pixels", pageNumber, image.getWidth(), image.getHeight());

      return image;
    }
  }

  /**
   * Gets the number of pages in a PDF.
   *
   * @param pdfData the PDF data
   * @return the number of pages
   * @throws IOException if PDF processing fails
   */
  public int getPageCount(byte[] pdfData) throws IOException {
    if (pdfData == null || pdfData.length == 0) {
      return 0;
    }

    try (PDDocument document = Loader.loadPDF(pdfData)) {
      return document.getNumberOfPages();
    }
  }

  /**
   * Extracts all pages from a PDF as images.
   *
   * @param pdfData the PDF data
   * @param targetWidth desired width for each page
   * @param targetHeight desired height for each page
   * @return array of extracted images
   * @throws IOException if PDF processing fails
   */
  public BufferedImage[] extractAllPages(byte[] pdfData, int targetWidth, int targetHeight)
      throws IOException {

    if (pdfData == null || pdfData.length == 0) {
      return new BufferedImage[0];
    }

    try (PDDocument document = Loader.loadPDF(pdfData)) {
      int pageCount = document.getNumberOfPages();
      BufferedImage[] images = new BufferedImage[pageCount];

      PDFRenderer pdfRenderer = new PDFRenderer(document);
      float scale = calculateOptimalScale(targetWidth, targetHeight, DEFAULT_DPI);

      for (int i = 0; i < pageCount; i++) {
        images[i] = pdfRenderer.renderImage(i, scale);
        log.debug("Extracted page {} of {}", i + 1, pageCount);
      }

      return images;
    }
  }

  /**
   * Calculates the optimal scale factor for PDF rendering.
   *
   * @param targetWidth desired width
   * @param targetHeight desired height
   * @param dpi desired DPI
   * @return the scale factor
   */
  private float calculateOptimalScale(int targetWidth, int targetHeight, float dpi) {
    // Calculate scale based on target dimensions and DPI
    // Assuming standard HD resolution as base (1920x1080)
    float widthScale = targetWidth / 1920f;
    float heightScale = targetHeight / 1080f;
    float dimensionScale = Math.max(widthScale, heightScale);

    // Combine dimension scale with DPI scale
    float dpiScale = dpi / BASE_DPI;

    return dimensionScale * dpiScale;
  }
}
