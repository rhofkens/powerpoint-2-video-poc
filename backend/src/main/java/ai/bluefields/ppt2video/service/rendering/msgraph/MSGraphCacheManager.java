package ai.bluefields.ppt2video.service.rendering.msgraph;

import ai.bluefields.ppt2video.service.MSGraphService;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Manages caching for MS Graph rendering operations. Handles both file upload references and
 * converted PDF data.
 */
@Component
@ConditionalOnProperty(name = "app.msgraph.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MSGraphCacheManager {

  private final MSGraphService msGraphService;

  // Cache for uploaded file references (file key -> OneDrive item ID)
  private final Map<String, String> fileUploadCache = new ConcurrentHashMap<>();

  // Cache for converted PDF data (file key -> PDF bytes)
  private final Map<String, byte[]> pdfCache = new ConcurrentHashMap<>();

  /**
   * Generates a cache key for a file based on its path and modification time.
   *
   * @param file the file to generate a key for
   * @return the cache key
   */
  public String generateCacheKey(File file) {
    return file.getAbsolutePath() + "_" + file.lastModified();
  }

  /**
   * Gets the OneDrive item ID for a cached file upload.
   *
   * @param cacheKey the cache key
   * @return the OneDrive item ID, or null if not cached
   */
  public String getUploadedFileId(String cacheKey) {
    return fileUploadCache.get(cacheKey);
  }

  /**
   * Caches an uploaded file's OneDrive item ID.
   *
   * @param cacheKey the cache key
   * @param driveItemId the OneDrive item ID
   */
  public void cacheUploadedFile(String cacheKey, String driveItemId) {
    fileUploadCache.put(cacheKey, driveItemId);
    log.debug("Cached uploaded file with key: {}", cacheKey);
  }

  /**
   * Gets cached PDF data for a file.
   *
   * @param cacheKey the cache key
   * @return the PDF data, or null if not cached
   */
  public byte[] getCachedPdf(String cacheKey) {
    return pdfCache.get(cacheKey);
  }

  /**
   * Caches PDF data for a file.
   *
   * @param cacheKey the cache key
   * @param pdfData the PDF data
   */
  public void cachePdf(String cacheKey, byte[] pdfData) {
    pdfCache.put(cacheKey, pdfData);
    log.debug("Cached PDF data for key: {} (size: {} bytes)", cacheKey, pdfData.length);
  }

  /**
   * Clears cached data for a specific file.
   *
   * @param file the file to clear from cache
   */
  public void clearCacheForFile(File file) {
    String cacheKey = generateCacheKey(file);

    // Remove PDF data from cache
    byte[] pdfData = pdfCache.remove(cacheKey);
    if (pdfData != null) {
      log.debug("Cleared PDF cache for file: {}", file.getName());
    }

    // Clean up OneDrive file if it exists
    String driveItemId = fileUploadCache.remove(cacheKey);
    if (driveItemId != null) {
      try {
        msGraphService.deleteFromOneDrive(driveItemId);
        log.debug("Deleted OneDrive file for: {}", file.getName());
      } catch (Exception e) {
        log.warn("Failed to delete OneDrive file: {}", e.getMessage());
      }
    }
  }

  /** Clears all caches and deletes all OneDrive files. */
  public void clearAllCaches() {
    log.info("Clearing all MS Graph caches");

    // Delete all OneDrive files
    for (Map.Entry<String, String> entry : fileUploadCache.entrySet()) {
      try {
        msGraphService.deleteFromOneDrive(entry.getValue());
      } catch (Exception e) {
        log.warn("Failed to delete OneDrive file {}: {}", entry.getValue(), e.getMessage());
      }
    }

    fileUploadCache.clear();
    pdfCache.clear();

    log.info("Cleared {} file uploads and {} PDF caches", fileUploadCache.size(), pdfCache.size());
  }

  /**
   * Gets the current cache statistics.
   *
   * @return cache statistics
   */
  public CacheStatistics getStatistics() {
    long totalPdfSize = pdfCache.values().stream().mapToLong(pdf -> pdf.length).sum();
    return new CacheStatistics(fileUploadCache.size(), pdfCache.size(), totalPdfSize);
  }

  /** Cache statistics. */
  public record CacheStatistics(int uploadedFiles, int cachedPdfs, long totalPdfBytes) {}
}
