package ai.bluefields.ppt2video.service;

import ai.bluefields.ppt2video.dto.*;
import ai.bluefields.ppt2video.entity.*;
import ai.bluefields.ppt2video.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for performing comprehensive preflight checks on presentations. Validates that all slides
 * have required components and are ready for video generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PreflightCheckService {

  private final SlideRepository slideRepository;
  private final SlideNarrativeRepository slideNarrativeRepository;
  private final SlideSpeechRepository slideSpeechRepository;
  private final AvatarVideoRepository avatarVideoRepository;
  private final AssetMetadataRepository assetMetadataRepository;
  private final R2AssetVerificationService r2AssetVerificationService;

  // Simple in-memory cache for recent checks (could be replaced with Redis)
  private final Map<UUID, PreflightCheckResponseDto> recentChecks = new ConcurrentHashMap<>();
  private static final long CACHE_TTL_MINUTES = 5;

  /**
   * Runs a comprehensive preflight check on a presentation.
   *
   * @param presentationId the ID of the presentation to check
   * @param request optional request parameters for the check
   * @return detailed check results
   */
  public PreflightCheckResponseDto runPreflightCheck(
      UUID presentationId, PreflightCheckRequestDto request) {
    log.info("Starting preflight check for presentation: {}", presentationId);

    // Check cache if not forcing refresh
    if (request != null && !request.isForceRefresh()) {
      PreflightCheckResponseDto cached = getCachedResult(presentationId);
      if (cached != null) {
        log.info("Returning cached preflight check result for presentation: {}", presentationId);
        return cached;
      }
    }

    boolean checkEnhanced = request != null && request.isCheckEnhancedNarrative();

    try {
      // Fetch all slides for the presentation
      List<Slide> slides = slideRepository.findByPresentationIdOrderBySlideNumber(presentationId);

      if (slides.isEmpty()) {
        log.warn("No slides found for presentation: {}", presentationId);
        return buildEmptyResponse(presentationId);
      }

      log.info("Found {} slides for presentation: {}", slides.size(), presentationId);

      // Fetch all related data in batch for performance
      Map<UUID, SlideNarrative> narrativeMap = fetchNarratives(presentationId);
      Map<UUID, SlideSpeech> speechMap = fetchSpeeches(presentationId);
      Map<UUID, AvatarVideo> videoMap = fetchAvatarVideos(presentationId);
      Map<UUID, List<AssetMetadata>> assetMap = fetchAssets(slides);

      // Validate each slide
      List<SlideCheckResult> slideResults = new ArrayList<>();
      for (Slide slide : slides) {
        SlideCheckResult result =
            validateSlide(
                slide,
                narrativeMap.get(slide.getId()),
                speechMap.get(slide.getId()),
                videoMap.get(slide.getId()),
                assetMap.get(slide.getId()),
                checkEnhanced);
        slideResults.add(result);
      }

      // Build summary
      PreflightSummary summary = buildSummary(slideResults);

      // Determine overall status
      PreflightStatus overallStatus = determineOverallStatus(summary, slideResults);

      // Build response
      PreflightCheckResponseDto response =
          PreflightCheckResponseDto.builder()
              .presentationId(presentationId)
              .overallStatus(overallStatus)
              .slideResults(slideResults)
              .summary(summary)
              .checkedAt(Instant.now())
              .build();

      // Cache the result
      cacheResult(presentationId, response);

      log.info(
          "Preflight check completed for presentation: {}. Status: {}",
          presentationId,
          overallStatus);
      return response;

    } catch (Exception e) {
      log.error("Error during preflight check for presentation: {}", presentationId, e);
      return buildErrorResponse(presentationId, e.getMessage());
    }
  }

  /**
   * Retrieves the latest preflight check status for a presentation.
   *
   * @param presentationId the ID of the presentation
   * @return the latest check results if available, null otherwise
   */
  public PreflightCheckResponseDto getLatestStatus(UUID presentationId) {
    return getCachedResult(presentationId);
  }

  private SlideCheckResult validateSlide(
      Slide slide,
      SlideNarrative narrative,
      SlideSpeech speech,
      AvatarVideo video,
      List<AssetMetadata> assets,
      boolean checkEnhanced) {

    List<String> issues = new ArrayList<>();
    Map<String, Object> metadata = new HashMap<>();

    // Check narrative
    CheckStatus narrativeStatus = CheckStatus.FAILED;
    if (narrative != null
        && narrative.getNarrativeText() != null
        && !narrative.getNarrativeText().isEmpty()) {
      narrativeStatus = CheckStatus.PASSED;
      metadata.put("narrativeId", narrative.getId());
      metadata.put("narrativeLength", narrative.getNarrativeText().length());
    } else {
      issues.add("Missing narrative text");
    }

    // Check enhanced narrative if requested
    CheckStatus enhancedNarrativeStatus = CheckStatus.NOT_APPLICABLE;
    if (checkEnhanced) {
      if (narrative != null
          && narrative.getEnhancedNarrativeText() != null
          && !narrative.getEnhancedNarrativeText().isEmpty()) {
        enhancedNarrativeStatus = CheckStatus.PASSED;
        metadata.put("enhancedNarrativeLength", narrative.getEnhancedNarrativeText().length());
      } else {
        enhancedNarrativeStatus = CheckStatus.FAILED;
        issues.add("Missing enhanced narrative text");
      }
    }

    // Check audio/speech
    CheckStatus audioStatus = CheckStatus.FAILED;
    if (speech != null && speech.getAudioFilePath() != null) {
      // Check if audio is published to R2
      AssetMetadata audioAsset = findAssetByType(assets, "SLIDE_AUDIO");
      if (audioAsset != null) {
        boolean isPublished = r2AssetVerificationService.verifyAssetPublished(audioAsset.getId());
        if (isPublished) {
          audioStatus = CheckStatus.PASSED;
          metadata.put("audioAssetId", audioAsset.getId());
          metadata.put("audioR2Key", audioAsset.getR2Key());
        } else {
          audioStatus = CheckStatus.WARNING;
          issues.add("Audio file exists but not published to R2");
        }
      } else {
        audioStatus = CheckStatus.WARNING;
        issues.add("Audio file generated but asset metadata missing");
      }
      metadata.put("speechId", speech.getId());
      metadata.put("audioPath", speech.getAudioFilePath());
    } else {
      issues.add("Missing TTS audio");
    }

    // Check avatar video
    CheckStatus avatarVideoStatus = CheckStatus.FAILED;
    if (video != null && video.getVideoUrl() != null) {
      // Check if video is published to R2
      if (video.getPublishedUrl() != null && !video.getPublishedUrl().isEmpty()) {
        avatarVideoStatus = CheckStatus.PASSED;
        metadata.put("videoPublishedUrl", video.getPublishedUrl());
      } else {
        avatarVideoStatus = CheckStatus.WARNING;
        issues.add("Avatar video exists but not published to R2");
      }
      metadata.put("avatarVideoId", video.getId());
      metadata.put("videoUrl", video.getVideoUrl());
      metadata.put("videoStatus", video.getStatus());
    } else {
      issues.add("Missing avatar video");
    }

    // Check slide image
    CheckStatus imageStatus = CheckStatus.FAILED;
    if (slide.getImagePath() != null && !slide.getImagePath().isEmpty()) {
      // Check if image is published to R2
      AssetMetadata imageAsset = findAssetByType(assets, "SLIDE_IMAGE");
      if (imageAsset != null) {
        boolean isPublished = r2AssetVerificationService.verifyAssetPublished(imageAsset.getId());
        if (isPublished) {
          imageStatus = CheckStatus.PASSED;
          metadata.put("imageAssetId", imageAsset.getId());
          metadata.put("imageR2Key", imageAsset.getR2Key());
        } else {
          imageStatus = CheckStatus.WARNING;
          issues.add("Slide image exists but not published to R2");
        }
      } else {
        imageStatus = CheckStatus.WARNING;
        issues.add("Slide image rendered but asset metadata missing");
      }
      metadata.put("imagePath", slide.getImagePath());
      metadata.put("imageWidth", slide.getImageWidth());
      metadata.put("imageHeight", slide.getImageHeight());
    } else {
      issues.add("Missing slide image");
    }

    return SlideCheckResult.builder()
        .slideId(slide.getId())
        .slideNumber(slide.getSlideNumber())
        .slideTitle(slide.getTitle() != null ? slide.getTitle() : "Slide " + slide.getSlideNumber())
        .narrativeStatus(narrativeStatus)
        .enhancedNarrativeStatus(enhancedNarrativeStatus)
        .audioStatus(audioStatus)
        .avatarVideoStatus(avatarVideoStatus)
        .imageStatus(imageStatus)
        .issues(issues)
        .metadata(metadata)
        .build();
  }

  private Map<UUID, SlideNarrative> fetchNarratives(UUID presentationId) {
    List<SlideNarrative> narratives =
        slideNarrativeRepository.findActiveNarrativesByPresentationId(presentationId);
    return narratives.stream().collect(Collectors.toMap(n -> n.getSlide().getId(), n -> n));
  }

  private Map<UUID, SlideSpeech> fetchSpeeches(UUID presentationId) {
    List<SlideSpeech> speeches =
        slideSpeechRepository.findActiveSpeechesByPresentationId(presentationId);
    return speeches.stream().collect(Collectors.toMap(SlideSpeech::getSlideId, s -> s));
  }

  private Map<UUID, AvatarVideo> fetchAvatarVideos(UUID presentationId) {
    List<AvatarVideo> videos =
        avatarVideoRepository.findByPresentationIdOrderByCreatedAtDesc(presentationId);

    // Get the latest video for each slide
    Map<UUID, AvatarVideo> videoMap = new HashMap<>();
    for (AvatarVideo video : videos) {
      if (!videoMap.containsKey(video.getSlideId())
          && AvatarGenerationStatusType.COMPLETED.equals(video.getStatus())) {
        videoMap.put(video.getSlideId(), video);
      }
    }
    return videoMap;
  }

  private Map<UUID, List<AssetMetadata>> fetchAssets(List<Slide> slides) {
    List<UUID> slideIds = slides.stream().map(Slide::getId).collect(Collectors.toList());
    List<AssetMetadata> assets = assetMetadataRepository.findBySlideIdIn(slideIds);

    return assets.stream().collect(Collectors.groupingBy(AssetMetadata::getSlideId));
  }

  private AssetMetadata findAssetByType(List<AssetMetadata> assets, String assetType) {
    if (assets == null) return null;
    return assets.stream()
        .filter(a -> AssetType.valueOf(assetType).equals(a.getAssetType()))
        .findFirst()
        .orElse(null);
  }

  private PreflightSummary buildSummary(List<SlideCheckResult> slideResults) {
    int totalSlides = slideResults.size();
    int slidesReady = 0;
    int slidesMissingNarrative = 0;
    int slidesMissingAudio = 0;
    int slidesMissingVideo = 0;
    int slidesMissingImages = 0;
    int slidesWithUnpublishedAssets = 0;

    for (SlideCheckResult result : slideResults) {
      boolean hasNarrative = result.getNarrativeStatus() == CheckStatus.PASSED;
      boolean hasAudio = result.getAudioStatus() == CheckStatus.PASSED;
      boolean hasVideo = result.getAvatarVideoStatus() == CheckStatus.PASSED;
      boolean hasImage = result.getImageStatus() == CheckStatus.PASSED;

      if (!hasNarrative) slidesMissingNarrative++;
      if (result.getAudioStatus() == CheckStatus.FAILED) slidesMissingAudio++;
      if (result.getAvatarVideoStatus() == CheckStatus.FAILED) slidesMissingVideo++;
      if (result.getImageStatus() == CheckStatus.FAILED) slidesMissingImages++;

      if (result.getAudioStatus() == CheckStatus.WARNING
          || result.getAvatarVideoStatus() == CheckStatus.WARNING
          || result.getImageStatus() == CheckStatus.WARNING) {
        slidesWithUnpublishedAssets++;
      }

      if (hasNarrative && hasAudio && hasVideo && hasImage) {
        slidesReady++;
      }
    }

    boolean allMandatoryChecksPassed =
        slidesMissingNarrative == 0
            && slidesMissingAudio == 0
            && slidesMissingVideo == 0
            && slidesMissingImages == 0;

    return PreflightSummary.builder()
        .totalSlides(totalSlides)
        .slidesReady(slidesReady)
        .slidesMissingNarrative(slidesMissingNarrative)
        .slidesMissingAudio(slidesMissingAudio)
        .slidesMissingVideo(slidesMissingVideo)
        .slidesMissingImages(slidesMissingImages)
        .slidesWithUnpublishedAssets(slidesWithUnpublishedAssets)
        .allMandatoryChecksPassed(allMandatoryChecksPassed)
        .build();
  }

  private PreflightStatus determineOverallStatus(
      PreflightSummary summary, List<SlideCheckResult> results) {
    if (summary.isAllMandatoryChecksPassed()) {
      if (summary.getSlidesWithUnpublishedAssets() > 0) {
        return PreflightStatus.HAS_WARNINGS;
      }
      return PreflightStatus.READY;
    }
    return PreflightStatus.INCOMPLETE;
  }

  private PreflightCheckResponseDto buildEmptyResponse(UUID presentationId) {
    return PreflightCheckResponseDto.builder()
        .presentationId(presentationId)
        .overallStatus(PreflightStatus.INCOMPLETE)
        .slideResults(new ArrayList<>())
        .summary(
            PreflightSummary.builder()
                .totalSlides(0)
                .slidesReady(0)
                .slidesMissingNarrative(0)
                .slidesMissingAudio(0)
                .slidesMissingVideo(0)
                .slidesMissingImages(0)
                .slidesWithUnpublishedAssets(0)
                .allMandatoryChecksPassed(false)
                .build())
        .checkedAt(Instant.now())
        .build();
  }

  private PreflightCheckResponseDto buildErrorResponse(UUID presentationId, String errorMessage) {
    return PreflightCheckResponseDto.builder()
        .presentationId(presentationId)
        .overallStatus(PreflightStatus.ERROR)
        .slideResults(new ArrayList<>())
        .summary(
            PreflightSummary.builder()
                .totalSlides(0)
                .slidesReady(0)
                .slidesMissingNarrative(0)
                .slidesMissingAudio(0)
                .slidesMissingVideo(0)
                .slidesMissingImages(0)
                .slidesWithUnpublishedAssets(0)
                .allMandatoryChecksPassed(false)
                .build())
        .checkedAt(Instant.now())
        .build();
  }

  private void cacheResult(UUID presentationId, PreflightCheckResponseDto result) {
    recentChecks.put(presentationId, result);
    // Clean up old entries (simple implementation)
    cleanupCache();
  }

  private PreflightCheckResponseDto getCachedResult(UUID presentationId) {
    PreflightCheckResponseDto cached = recentChecks.get(presentationId);
    if (cached != null) {
      long ageMinutes =
          (Instant.now().toEpochMilli() - cached.getCheckedAt().toEpochMilli()) / (1000 * 60);
      if (ageMinutes <= CACHE_TTL_MINUTES) {
        return cached;
      } else {
        recentChecks.remove(presentationId);
      }
    }
    return null;
  }

  private void cleanupCache() {
    Instant cutoff = Instant.now().minusSeconds(CACHE_TTL_MINUTES * 60);
    recentChecks.entrySet().removeIf(entry -> entry.getValue().getCheckedAt().isBefore(cutoff));
  }
}
