package ai.bluefields.ppt2video.service;

import com.microsoft.graph.models.DriveItem;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to demonstrate how MS Graph upload progress could be tracked. This shows the proper
 * implementation of chunk uploads with progress monitoring.
 */
@Slf4j
public class MSGraphUploadProgress {

  private static final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

  /**
   * Uploads a chunk of data and returns the upload progress.
   *
   * @param uploadUrl The upload session URL
   * @param data The chunk data
   * @param length The actual length of data to upload
   * @param offset The offset in the file
   * @param totalSize The total file size
   * @return UploadResult with progress information
   */
  public static UploadResult uploadChunkWithProgress(
      String uploadUrl, byte[] data, int length, long offset, long totalSize, String accessToken)
      throws IOException, InterruptedException {

    // Create the chunk data
    byte[] chunkData = new byte[length];
    System.arraycopy(data, 0, chunkData, 0, length);

    // Build the request with proper headers
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(uploadUrl))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Length", String.valueOf(length))
            .header(
                "Content-Range",
                String.format("bytes %d-%d/%d", offset, offset + length - 1, totalSize))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(chunkData))
            .timeout(Duration.ofMinutes(5))
            .build();

    log.debug(
        "Uploading chunk: bytes {}-{}/{} ({} bytes)",
        offset,
        offset + length - 1,
        totalSize,
        length);

    // Send the request
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    // Parse the response
    if (response.statusCode() == 202) {
      // Upload in progress - parse the progress from response
      String body = response.body();
      log.debug("Upload progress response: {}", body);

      // The response contains nextExpectedRanges showing what's left to upload
      return new UploadResult(false, offset + length, totalSize, "In progress");

    } else if (response.statusCode() == 200 || response.statusCode() == 201) {
      // Upload completed - response contains the DriveItem
      log.info("Upload completed successfully");
      return new UploadResult(true, totalSize, totalSize, "Completed");

    } else {
      // Error occurred
      throw new IOException(
          "Upload chunk failed with status " + response.statusCode() + ": " + response.body());
    }
  }

  /**
   * Checks if a file is fully processed and ready for operations. Some operations (like PDF
   * conversion) may fail if the file is still being processed.
   */
  public static boolean isFileProcessingComplete(DriveItem item) {
    // Check various indicators that the file is ready
    if (item == null) {
      return false;
    }

    // File should have size
    if (item.getSize() == null || item.getSize() == 0) {
      log.debug("File size not yet available");
      return false;
    }

    // File should have content hash (indicates processing is complete)
    if (item.getFile() != null && item.getFile().getHashes() != null) {
      if (item.getFile().getHashes().getQuickXorHash() != null) {
        log.debug("File hash available - processing likely complete");
        return true;
      }
    }

    // For Office files, check if preview is available
    // This is a good indicator that server-side processing is done
    if (item.getFile() != null && item.getFile().getMimeType() != null) {
      if (item.getFile().getMimeType().contains("officedocument")) {
        // Could check for thumbnail availability here
        log.debug("Office file detected - may need additional processing time");
      }
    }

    return true; // Assume ready if we can't determine otherwise
  }

  public static class UploadResult {
    public final boolean isComplete;
    public final long bytesUploaded;
    public final long totalBytes;
    public final String status;

    public UploadResult(boolean isComplete, long bytesUploaded, long totalBytes, String status) {
      this.isComplete = isComplete;
      this.bytesUploaded = bytesUploaded;
      this.totalBytes = totalBytes;
      this.status = status;
    }

    public double getProgressPercentage() {
      return (double) bytesUploaded / totalBytes * 100;
    }
  }
}
