package ai.bluefields.ppt2video.service.ai;

import ai.bluefields.ppt2video.service.FileStorageService;
import ai.bluefields.ppt2video.utils.ImageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * Service for interacting with OpenAI API through Spring AI framework. Handles chat completions and
 * multimodal (vision) requests with retry logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

  private final OpenAiChatModel chatModel;
  private final RetryTemplate retryTemplate;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;
  private ChatClient chatClient;

  @PostConstruct
  public void init() {
    this.chatClient = ChatClient.create(chatModel);
  }

  @Value("${app.ai.analysis.timeout-seconds:60}")
  private int timeoutSeconds;

  @Value("${app.ai.analysis.retry-attempts:3}")
  private int retryAttempts;

  @Value("${app.ai.analysis.retry-delay-ms:2000}")
  private long retryDelayMs;

  @Value("${app.ai.debug.save-prompts:false}")
  private boolean savePromptsGlobal;

  @Value("${app.ai.debug.save-prompts.deck-analysis:true}")
  private boolean savePromptsDeckAnalysis;

  @Value("${app.ai.debug.save-prompts.slide-analysis:true}")
  private boolean savePromptsSlideAnalysis;

  @Value("${app.ai.debug.save-prompts.narrative-generation:true}")
  private boolean savePromptsNarrativeGeneration;

  @Value("${spring.ai.openai.chat.options.model:gpt-4}")
  private String modelName;

  /**
   * Send a chat completion request to OpenAI.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @return The AI response as a string
   */
  public String generateChatCompletion(String systemPrompt, String userPrompt) {
    return generateChatCompletion(systemPrompt, userPrompt, null, null);
  }

  /**
   * Send a chat completion request to OpenAI with logging context.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param serviceName The name of the service calling this method (for logging)
   * @param presentationId The presentation ID for organizing logs (optional)
   * @return The AI response as a string
   */
  public String generateChatCompletion(
      String systemPrompt, String userPrompt, String serviceName, UUID presentationId) {
    long startTime = System.currentTimeMillis();
    log.debug(
        "Generating chat completion with system prompt length: {} and user prompt length: {}",
        systemPrompt.length(),
        userPrompt.length());

    try {
      return retryTemplate.execute(
          context -> {
            if (context.getRetryCount() > 0) {
              log.debug("Retry attempt {} for chat completion", context.getRetryCount());
            }

            log.debug("Sending chat request to OpenAI using ChatClient fluent API");

            ChatResponse response =
                chatClient.prompt().system(systemPrompt).user(userPrompt).call().chatResponse();

            String result = response.getResult().getOutput().getText();
            long duration = System.currentTimeMillis() - startTime;

            log.debug(
                "Chat completion successful - Response length: {} chars, Duration: {} ms",
                result.length(),
                duration);

            // Log token usage if available
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
              log.debug(
                  "Token usage - Prompt: {}, Generation: {}, Total: {}",
                  response.getMetadata().getUsage().getPromptTokens(),
                  response.getMetadata().getUsage().getCompletionTokens(),
                  response.getMetadata().getUsage().getTotalTokens());
            }

            // Save prompts and response if logging is enabled
            if (shouldSavePrompts(serviceName)) {
              savePromptAndResponse(
                  serviceName, presentationId, "chat", systemPrompt, userPrompt, null, result);
            }

            return result;
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("Failed to generate chat completion after {} ms", duration, e);
      throw new RuntimeException("Failed to generate chat completion: " + e.getMessage(), e);
    }
  }

  /**
   * Send a multimodal request (text + image) to OpenAI.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param imageBase64 The base64 encoded image
   * @param mimeType The MIME type of the image (e.g., "image/png")
   * @return The AI response as a string
   */
  public String generateVisionCompletion(
      String systemPrompt, String userPrompt, String imageBase64, String mimeType) {
    return generateVisionCompletion(systemPrompt, userPrompt, imageBase64, mimeType, null, null);
  }

  /**
   * Send a multimodal request (text + image) to OpenAI with logging context.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param imageBase64 The base64 encoded image
   * @param mimeType The MIME type of the image (e.g., "image/png")
   * @param serviceName The name of the service calling this method (for logging)
   * @param presentationId The presentation ID for organizing logs (optional)
   * @return The AI response as a string
   */
  public String generateVisionCompletion(
      String systemPrompt,
      String userPrompt,
      String imageBase64,
      String mimeType,
      String serviceName,
      UUID presentationId) {
    long startTime = System.currentTimeMillis();
    log.debug(
        "Generating vision completion with image of type: {}, image size: {} bytes",
        mimeType,
        imageBase64.length());

    try {
      // Convert PNG to JPEG if needed (workaround for Spring AI PNG issue)
      String processedImageBase64 = imageBase64;

      if (mimeType.contains("png") || ImageUtils.isPng(imageBase64)) {
        log.debug("Converting PNG to JPEG for Spring AI compatibility");
        processedImageBase64 = ImageUtils.convertPngToJpeg(imageBase64);
      }

      // Convert base64 to byte array
      byte[] imageBytes = java.util.Base64.getDecoder().decode(processedImageBase64);
      log.debug(
          "Decoded {} base64 characters to {} bytes",
          processedImageBase64.length(),
          imageBytes.length);

      // Create ByteArrayResource with filename override
      var byteArrayResource =
          new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
              return "image.jpg";
            }
          };

      // Use ChatClient fluent API for vision request
      log.debug("Sending vision request using ChatClient fluent API");

      return retryTemplate.execute(
          context -> {
            if (context.getRetryCount() > 0) {
              log.debug("Retry attempt {} for vision completion", context.getRetryCount());
            }

            ChatResponse response =
                chatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(
                        u -> u.text(userPrompt).media(MimeTypeUtils.IMAGE_JPEG, byteArrayResource))
                    .call()
                    .chatResponse();

            String result = response.getResult().getOutput().getText();
            long duration = System.currentTimeMillis() - startTime;

            log.debug(
                "Vision completion successful - Response length: {} chars, Duration: {} ms",
                result.length(),
                duration);

            // Log token usage if available
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
              log.debug(
                  "Token usage - Prompt: {}, Generation: {}, Total: {}",
                  response.getMetadata().getUsage().getPromptTokens(),
                  response.getMetadata().getUsage().getCompletionTokens(),
                  response.getMetadata().getUsage().getTotalTokens());
            }

            // Save prompts and response if logging is enabled (without image data)
            if (shouldSavePrompts(serviceName)) {
              savePromptAndResponse(
                  serviceName,
                  presentationId,
                  "vision",
                  systemPrompt,
                  userPrompt + " [IMAGE DATA OMITTED]",
                  null,
                  result);
            }

            return result;
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      log.error("Failed to generate vision completion after {} ms", duration, e);
      throw new RuntimeException("Failed to generate vision completion: " + e.getMessage(), e);
    }
  }

  /**
   * Generate structured JSON output from OpenAI.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param responseFormat The expected JSON structure format
   * @return The AI response as a string (JSON format)
   */
  public String generateJsonCompletion(
      String systemPrompt, String userPrompt, String responseFormat) {
    return generateJsonCompletion(systemPrompt, userPrompt, responseFormat, null, null);
  }

  /**
   * Generate structured JSON output from OpenAI with logging context.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param responseFormat The expected JSON structure format
   * @param serviceName The name of the service calling this method (for logging)
   * @param presentationId The presentation ID for organizing logs (optional)
   * @return The AI response as a string (JSON format)
   */
  public String generateJsonCompletion(
      String systemPrompt,
      String userPrompt,
      String responseFormat,
      String serviceName,
      UUID presentationId) {
    log.debug(
        "Generating JSON completion with expected format: {}",
        responseFormat.substring(0, Math.min(responseFormat.length(), 100)) + "...");

    String enhancedSystemPrompt =
        systemPrompt
            + "\n\nYou must respond with ONLY valid JSON that matches this format. "
            + "Do not include any markdown formatting, code blocks, or explanations:\n"
            + responseFormat;

    String response =
        generateChatCompletion(enhancedSystemPrompt, userPrompt, serviceName, presentationId);

    // Clean up the response to handle common issues
    String cleanedResponse = cleanJsonResponse(response);
    log.debug("Cleaned JSON response length: {} chars", cleanedResponse.length());

    return cleanedResponse;
  }

  /**
   * Clean up JSON response from AI to handle common formatting issues.
   *
   * @param response The raw response from AI
   * @return Cleaned JSON string
   */
  private String cleanJsonResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      log.warn("Received null or empty response from AI, returning empty JSON object");
      return "{}";
    }

    String cleaned = response.trim();
    int originalLength = cleaned.length();

    // Remove markdown code blocks if present
    if (cleaned.startsWith("```json")) {
      cleaned = cleaned.substring(7);
      log.debug("Removed ```json prefix from response");
    } else if (cleaned.startsWith("```")) {
      cleaned = cleaned.substring(3);
      log.debug("Removed ``` prefix from response");
    }

    if (cleaned.endsWith("```")) {
      cleaned = cleaned.substring(0, cleaned.length() - 3);
      log.debug("Removed ``` suffix from response");
    }

    // Remove any remaining backticks
    cleaned = cleaned.trim();

    // Ensure we have valid JSON brackets
    if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
      log.warn("Response doesn't start with valid JSON. Original response: {}", response);
    }

    if (cleaned.length() != originalLength) {
      log.debug(
          "Cleaned JSON response: reduced from {} to {} chars", originalLength, cleaned.length());
    }

    return cleaned;
  }

  /**
   * Generate structured JSON output from OpenAI with vision capabilities.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param imageBase64 The base64 encoded image
   * @param mimeType The MIME type of the image
   * @param responseFormat The expected JSON structure format
   * @return The AI response as a string (JSON format)
   */
  public String generateJsonVisionCompletion(
      String systemPrompt,
      String userPrompt,
      String imageBase64,
      String mimeType,
      String responseFormat) {
    return generateJsonVisionCompletion(
        systemPrompt, userPrompt, imageBase64, mimeType, responseFormat, null, null);
  }

  /**
   * Generate structured JSON output from OpenAI with vision capabilities and logging.
   *
   * @param systemPrompt The system prompt to set context
   * @param userPrompt The user prompt with the actual request
   * @param imageBase64 The base64 encoded image
   * @param mimeType The MIME type of the image
   * @param responseFormat The expected JSON structure format
   * @param serviceName The name of the service calling this method (for logging)
   * @param presentationId The presentation ID for organizing logs (optional)
   * @return The AI response as a string (JSON format)
   */
  public String generateJsonVisionCompletion(
      String systemPrompt,
      String userPrompt,
      String imageBase64,
      String mimeType,
      String responseFormat,
      String serviceName,
      UUID presentationId) {
    String enhancedSystemPrompt =
        systemPrompt
            + "\n\nYou must respond with ONLY valid JSON that matches this format. "
            + "Do not include any markdown formatting, code blocks, or explanations:\n"
            + responseFormat;

    String response =
        generateVisionCompletion(
            enhancedSystemPrompt, userPrompt, imageBase64, mimeType, serviceName, presentationId);

    // Clean up the response to handle common issues
    return cleanJsonResponse(response);
  }

  /**
   * Validate if the service is properly configured and operational.
   *
   * @return true if the service is operational
   */
  public boolean isOperational() {
    try {
      String response =
          generateChatCompletion(
              "You are a helpful assistant.", "Respond with 'OK' if you are operational.");
      return response != null && response.contains("OK");
    } catch (Exception e) {
      log.error("OpenAI service health check failed", e);
      return false;
    }
  }

  /** Determine if prompts should be saved based on global and service-specific settings. */
  private boolean shouldSavePrompts(String serviceName) {
    if (!savePromptsGlobal) {
      return false;
    }

    if (serviceName == null) {
      return true; // Save by default if global is enabled and no service specified
    }

    // Check service-specific settings
    return switch (serviceName.toLowerCase()) {
      case "deckanalysisservice", "deck-analysis" -> savePromptsDeckAnalysis;
      case "slideanalysisservice", "slide-analysis" -> savePromptsSlideAnalysis;
      case "narrativegenerationservice", "narrative-generation" -> savePromptsNarrativeGeneration;
      default -> true; // Save by default if global is enabled and service not specified
    };
  }

  /** Save prompt and response to file for debugging purposes. */
  private void savePromptAndResponse(
      String serviceName,
      UUID presentationId,
      String requestType,
      String systemPrompt,
      String userPrompt,
      String responseFormat,
      String response) {
    try {
      String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss-SSS"));
      String filename = String.format("%s-%s.json", requestType, timestamp);

      // Create JSON structure with all prompt data
      Map<String, Object> promptData = new HashMap<>();
      promptData.put("timestamp", LocalDateTime.now().toString());
      promptData.put("service", serviceName != null ? serviceName : "unknown");
      promptData.put("requestType", requestType);
      promptData.put("presentationId", presentationId != null ? presentationId.toString() : null);
      promptData.put("model", modelName);
      promptData.put("systemPrompt", systemPrompt);
      promptData.put("userPrompt", userPrompt);

      if (responseFormat != null) {
        promptData.put("responseFormat", responseFormat);
      }

      // Parse and include the response as structured data if it's JSON
      if (response != null) {
        try {
          promptData.put("response", objectMapper.readValue(response, Map.class));
        } catch (Exception e) {
          promptData.put("response", response); // Fall back to string if parsing fails
        }
      }

      // Write to file with pretty printing
      String jsonContent =
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptData);
      fileStorageService.storePromptJson(
          presentationId, serviceName != null ? serviceName : "general", filename, jsonContent);

    } catch (Exception e) {
      log.error("Failed to save prompt to file: {}", e.getMessage());
    }
  }
}
