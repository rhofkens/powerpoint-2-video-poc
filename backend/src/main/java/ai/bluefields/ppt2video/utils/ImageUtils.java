package ai.bluefields.ppt2video.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/** Utility class for image processing operations. */
@Slf4j
public class ImageUtils {

  /**
   * Convert a base64 encoded PNG image to JPEG format. This is a workaround for Spring AI's current
   * PNG handling issues.
   *
   * @param base64Png Base64 encoded PNG image
   * @return Base64 encoded JPEG image
   * @throws IOException if image conversion fails
   */
  public static String convertPngToJpeg(String base64Png) throws IOException {
    // Decode base64 to bytes
    byte[] pngBytes = Base64.getDecoder().decode(base64Png);

    // Read PNG image
    ByteArrayInputStream pngInputStream = new ByteArrayInputStream(pngBytes);
    BufferedImage image = ImageIO.read(pngInputStream);

    if (image == null) {
      throw new IOException("Failed to read PNG image");
    }

    log.debug("Original PNG dimensions: {}x{}", image.getWidth(), image.getHeight());

    // Convert to RGB if necessary (remove alpha channel)
    BufferedImage rgbImage =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    rgbImage.getGraphics().drawImage(image, 0, 0, null);

    // Write as JPEG
    ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream();
    ImageIO.write(rgbImage, "jpg", jpegOutputStream);

    // Encode to base64
    byte[] jpegBytes = jpegOutputStream.toByteArray();
    String base64Jpeg = Base64.getEncoder().encodeToString(jpegBytes);

    log.info(
        "Converted PNG ({}x{}, {} bytes) to JPEG ({} bytes)",
        image.getWidth(),
        image.getHeight(),
        pngBytes.length,
        jpegBytes.length);

    return base64Jpeg;
  }

  /**
   * Detect if a base64 image is PNG format by checking the header.
   *
   * @param base64Image Base64 encoded image
   * @return true if the image is PNG format
   */
  public static boolean isPng(String base64Image) {
    if (base64Image == null || base64Image.isEmpty()) {
      return false;
    }

    try {
      byte[] imageBytes = Base64.getDecoder().decode(base64Image);
      // PNG signature: 137 80 78 71 13 10 26 10 (decimal)
      // or in hex: 89 50 4E 47 0D 0A 1A 0A
      return imageBytes.length >= 8
          && imageBytes[0] == (byte) 0x89
          && // 137 in decimal
          imageBytes[1] == (byte) 0x50
          && // 'P'
          imageBytes[2] == (byte) 0x4E
          && // 'N'
          imageBytes[3] == (byte) 0x47
          && // 'G'
          imageBytes[4] == (byte) 0x0D
          && // CR
          imageBytes[5] == (byte) 0x0A
          && // LF
          imageBytes[6] == (byte) 0x1A
          && // DOS EOF
          imageBytes[7] == (byte) 0x0A; // LF
    } catch (Exception e) {
      log.warn("Failed to detect image format", e);
      return false;
    }
  }
}
