package ai.bluefields.ppt2video.service.intro;

import ai.bluefields.ppt2video.dto.ColorPaletteDto;
import ai.bluefields.ppt2video.entity.Slide;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for extracting dominant colors from slide images using K-means clustering. Analyzes slide
 * images to create color palettes for intro video generation.
 */
@Service
@Slf4j
public class ColorExtractionService {

  private static final int DEFAULT_COLOR_COUNT = 5;
  private static final int MAX_ITERATIONS = 50;
  private static final int SAMPLE_SIZE = 10000;
  private static final double CONVERGENCE_THRESHOLD = 1.0;

  /**
   * Extract dominant colors from a slide's rendered image.
   *
   * @param slide the slide to extract colors from
   * @return color palette with dominant colors and statistics
   */
  public ColorPaletteDto extractColors(Slide slide) {
    try {
      BufferedImage image = loadSlideImage(slide);
      if (image == null) {
        log.warn("Could not load image for slide {}", slide.getId());
        return createDefaultPalette();
      }

      List<Color> dominantColors = performKMeansClustering(image, DEFAULT_COLOR_COUNT);

      return buildColorPalette(dominantColors, image);
    } catch (Exception e) {
      log.error("Error extracting colors from slide {}: {}", slide.getId(), e.getMessage());
      return createDefaultPalette();
    }
  }

  /** Load the slide image from storage. */
  private BufferedImage loadSlideImage(Slide slide) {
    try {
      String imagePath = slide.getImagePath();
      if (imagePath == null || imagePath.isEmpty()) {
        log.warn("No image path for slide {}", slide.getId());
        return null;
      }

      // The image path is already absolute or relative to the working directory
      Path fullPath = Paths.get(imagePath);
      File imageFile = fullPath.toFile();

      if (!imageFile.exists()) {
        log.warn("Image file not found: {}", fullPath);
        return null;
      }

      return ImageIO.read(imageFile);
    } catch (IOException e) {
      log.error("Error loading slide image: {}", e.getMessage());
      return null;
    }
  }

  /** Perform K-means clustering to find dominant colors. */
  private List<Color> performKMeansClustering(BufferedImage image, int k) {
    // Sample pixels from the image
    List<int[]> pixels = samplePixels(image, SAMPLE_SIZE);

    if (pixels.isEmpty()) {
      return Collections.emptyList();
    }

    // Initialize centroids using K-means++ algorithm
    List<int[]> centroids = initializeCentroidsKMeansPlusPlus(pixels, k);

    // Iterate until convergence
    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
      Map<Integer, List<int[]>> clusters = assignToClusters(pixels, centroids);
      List<int[]> newCentroids = recalculateCentroids(clusters, k);

      if (hasConverged(centroids, newCentroids)) {
        log.debug("K-means converged after {} iterations", iteration);
        break;
      }
      centroids = newCentroids;
    }

    // Sort colors by cluster size (most dominant first)
    final List<int[]> finalCentroids = centroids;
    Map<Integer, List<int[]>> finalClusters = assignToClusters(pixels, finalCentroids);
    return finalCentroids.stream()
        .sorted(
            (c1, c2) -> {
              int idx1 = finalCentroids.indexOf(c1);
              int idx2 = finalCentroids.indexOf(c2);
              int size1 = finalClusters.getOrDefault(idx1, Collections.emptyList()).size();
              int size2 = finalClusters.getOrDefault(idx2, Collections.emptyList()).size();
              return Integer.compare(size2, size1);
            })
        .map(rgb -> new Color(rgb[0], rgb[1], rgb[2]))
        .collect(Collectors.toList());
  }

  /** Sample pixels from the image for clustering. */
  private List<int[]> samplePixels(BufferedImage image, int sampleSize) {
    List<int[]> pixels = new ArrayList<>();
    int width = image.getWidth();
    int height = image.getHeight();
    int totalPixels = width * height;

    // Calculate sampling interval
    int interval = Math.max(1, totalPixels / sampleSize);

    for (int i = 0; i < totalPixels && pixels.size() < sampleSize; i += interval) {
      int x = i % width;
      int y = i / width;
      int rgb = image.getRGB(x, y);

      // Extract RGB components
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;

      pixels.add(new int[] {r, g, b});
    }

    return pixels;
  }

  /** Initialize centroids using K-means++ algorithm for better initial placement. */
  private List<int[]> initializeCentroidsKMeansPlusPlus(List<int[]> pixels, int k) {
    List<int[]> centroids = new ArrayList<>();
    Random random = new Random();

    // Choose first centroid randomly
    centroids.add(pixels.get(random.nextInt(pixels.size())));

    // Choose remaining centroids with probability proportional to squared distance
    for (int i = 1; i < k; i++) {
      double[] distances = new double[pixels.size()];
      double totalDistance = 0;

      for (int j = 0; j < pixels.size(); j++) {
        double minDist = Double.MAX_VALUE;
        for (int[] centroid : centroids) {
          double dist = calculateDistance(pixels.get(j), centroid);
          minDist = Math.min(minDist, dist);
        }
        distances[j] = minDist * minDist;
        totalDistance += distances[j];
      }

      // Choose next centroid
      double randomValue = random.nextDouble() * totalDistance;
      double cumulative = 0;
      for (int j = 0; j < pixels.size(); j++) {
        cumulative += distances[j];
        if (cumulative >= randomValue) {
          centroids.add(pixels.get(j).clone());
          break;
        }
      }
    }

    return centroids;
  }

  /** Assign pixels to their nearest centroid. */
  private Map<Integer, List<int[]>> assignToClusters(List<int[]> pixels, List<int[]> centroids) {
    Map<Integer, List<int[]>> clusters = new HashMap<>();

    for (int[] pixel : pixels) {
      int nearestCentroid = 0;
      double minDistance = Double.MAX_VALUE;

      for (int i = 0; i < centroids.size(); i++) {
        double distance = calculateDistance(pixel, centroids.get(i));
        if (distance < minDistance) {
          minDistance = distance;
          nearestCentroid = i;
        }
      }

      clusters.computeIfAbsent(nearestCentroid, k -> new ArrayList<>()).add(pixel);
    }

    return clusters;
  }

  /** Recalculate centroids as the mean of their clusters. */
  private List<int[]> recalculateCentroids(Map<Integer, List<int[]>> clusters, int k) {
    List<int[]> newCentroids = new ArrayList<>();

    for (int i = 0; i < k; i++) {
      List<int[]> cluster = clusters.getOrDefault(i, Collections.emptyList());

      if (cluster.isEmpty()) {
        // Keep the old centroid if cluster is empty
        newCentroids.add(new int[] {128, 128, 128}); // Gray as default
      } else {
        int sumR = 0, sumG = 0, sumB = 0;
        for (int[] pixel : cluster) {
          sumR += pixel[0];
          sumG += pixel[1];
          sumB += pixel[2];
        }
        int size = cluster.size();
        newCentroids.add(new int[] {sumR / size, sumG / size, sumB / size});
      }
    }

    return newCentroids;
  }

  /** Calculate Euclidean distance between two RGB colors. */
  private double calculateDistance(int[] color1, int[] color2) {
    double dr = color1[0] - color2[0];
    double dg = color1[1] - color2[1];
    double db = color1[2] - color2[2];
    return Math.sqrt(dr * dr + dg * dg + db * db);
  }

  /** Check if centroids have converged. */
  private boolean hasConverged(List<int[]> oldCentroids, List<int[]> newCentroids) {
    for (int i = 0; i < oldCentroids.size(); i++) {
      if (calculateDistance(oldCentroids.get(i), newCentroids.get(i)) > CONVERGENCE_THRESHOLD) {
        return false;
      }
    }
    return true;
  }

  /** Build the color palette DTO from dominant colors. */
  private ColorPaletteDto buildColorPalette(List<Color> dominantColors, BufferedImage image) {
    List<String> hexColors =
        dominantColors.stream().map(this::toHexString).collect(Collectors.toList());

    // Calculate overall brightness and saturation
    double brightness = calculateAverageBrightness(dominantColors);
    double saturation = calculateAverageSaturation(dominantColors);

    // Determine color scheme
    String colorScheme = determineColorScheme(dominantColors);

    return ColorPaletteDto.builder()
        .dominantColors(hexColors)
        .primaryColor(hexColors.isEmpty() ? null : hexColors.get(0))
        .secondaryColor(hexColors.size() > 1 ? hexColors.get(1) : null)
        .accentColor(hexColors.size() > 2 ? hexColors.get(2) : null)
        .brightness(brightness)
        .saturation(saturation)
        .colorScheme(colorScheme)
        .build();
  }

  /** Convert Color to hex string. */
  private String toHexString(Color color) {
    return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
  }

  /** Calculate average brightness of colors (0.0 to 1.0). */
  private double calculateAverageBrightness(List<Color> colors) {
    if (colors.isEmpty()) return 0.5;

    double totalBrightness =
        colors.stream()
            .mapToDouble(
                color -> {
                  float[] hsb =
                      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                  return hsb[2]; // Brightness component
                })
            .sum();

    return totalBrightness / colors.size();
  }

  /** Calculate average saturation of colors (0.0 to 1.0). */
  private double calculateAverageSaturation(List<Color> colors) {
    if (colors.isEmpty()) return 0.5;

    double totalSaturation =
        colors.stream()
            .mapToDouble(
                color -> {
                  float[] hsb =
                      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                  return hsb[1]; // Saturation component
                })
            .sum();

    return totalSaturation / colors.size();
  }

  /** Determine the color scheme type based on dominant colors. */
  private String determineColorScheme(List<Color> colors) {
    if (colors.size() < 2) {
      return "monochromatic";
    }

    // Calculate hue variance
    List<Float> hues =
        colors.stream()
            .map(
                color -> {
                  float[] hsb =
                      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                  return hsb[0] * 360; // Convert to degrees
                })
            .collect(Collectors.toList());

    double hueVariance = calculateHueVariance(hues);

    if (hueVariance < 30) {
      return "monochromatic";
    } else if (hueVariance < 60) {
      return "analogous";
    } else if (hueVariance > 150) {
      return "complementary";
    } else {
      return "triadic";
    }
  }

  /** Calculate variance in hue values. */
  private double calculateHueVariance(List<Float> hues) {
    if (hues.size() < 2) return 0;

    double mean = hues.stream().mapToDouble(Float::doubleValue).average().orElse(0);
    double variance = hues.stream().mapToDouble(hue -> Math.pow(hue - mean, 2)).average().orElse(0);

    return Math.sqrt(variance);
  }

  /** Create a default color palette when extraction fails. */
  private ColorPaletteDto createDefaultPalette() {
    return ColorPaletteDto.builder()
        .dominantColors(Arrays.asList("#2563eb", "#3b82f6", "#60a5fa", "#93bbfc", "#c3d9fe"))
        .primaryColor("#2563eb")
        .secondaryColor("#3b82f6")
        .accentColor("#60a5fa")
        .brightness(0.6)
        .saturation(0.8)
        .colorScheme("monochromatic")
        .build();
  }
}
