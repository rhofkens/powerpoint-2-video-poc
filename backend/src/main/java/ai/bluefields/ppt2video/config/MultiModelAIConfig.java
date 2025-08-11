package ai.bluefields.ppt2video.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
public class MultiModelAIConfig {

  @Autowired private OpenAiChatModel baseChatModel;

  @Value("${app.ai.models.vision.model:gpt-4o}")
  private String visionModel;

  @Value("${app.ai.models.vision.temperature:0.7}")
  private Double visionTemperature;

  @Value("${app.ai.models.vision.max-tokens:4000}")
  private Integer visionMaxTokens;

  @Value("${app.ai.models.text.model:gpt-5}")
  private String textModel;

  @Value("${app.ai.models.text.temperature:1}")
  private Double textTemperature;

  @Value("${app.ai.models.text.max-completion-tokens:8000}")
  private Integer textMaxCompletionTokens;

  /**
   * Vision model for processing images and slides (GPT-4o) Better for visual understanding tasks
   */
  @Bean(name = "visionChatModel")
  public ChatModel visionChatModel() {
    log.info(
        "Configuring Vision Model: {} with max tokens: {}, temperature: {}",
        visionModel,
        visionMaxTokens,
        visionTemperature);

    OpenAiChatOptions visionOptions =
        OpenAiChatOptions.builder()
            .model(visionModel)
            .temperature(visionTemperature)
            .maxTokens(visionMaxTokens)
            .build();

    // Log the actual configuration being used
    log.info(
        "Vision model options - Model: {}, Temperature: {}, MaxTokens: {}",
        visionOptions.getModel(),
        visionOptions.getTemperature(),
        visionOptions.getMaxTokens());

    // Create a mutated version with specific options
    // IMPORTANT: The mutate() method might not override the model properly
    // This is a known issue with Spring AI where base configuration takes precedence
    OpenAiChatModel visionChatModel = baseChatModel.mutate().defaultOptions(visionOptions).build();

    // Log warning about potential model override issue
    log.warn(
        "IMPORTANT: Vision model configured as '{}' but Spring AI may use base model instead. Check actual model in response metadata.",
        visionModel);

    return visionChatModel;
  }

  /**
   * Text model for deck analysis and narrative generation (GPT-5) Better for complex reasoning and
   * text generation
   */
  @Bean(name = "textChatModel")
  public ChatModel textChatModel() {
    log.info(
        "Configuring Text Model: {} with max completion tokens: {}, temperature: {}",
        textModel,
        textMaxCompletionTokens,
        textTemperature);

    log.info("TEXTMODEL: Creating text model with model identifier " + textModel);
    OpenAiChatOptions.Builder optionsBuilder =
        OpenAiChatOptions.builder().model(textModel).temperature(textTemperature);

    // GPT-5 uses maxCompletionTokens instead of maxTokens
    if (textModel.startsWith("gpt-5")) {
      optionsBuilder.maxCompletionTokens(textMaxCompletionTokens);
      log.debug("Using maxCompletionTokens for GPT-5 model");
    } else {
      optionsBuilder.maxTokens(textMaxCompletionTokens);
      log.debug("Using maxTokens for non-GPT-5 model");
    }

    // Create a mutated version with specific options
    // Using defaultOptions should override the base model configuration
    return baseChatModel.mutate().defaultOptions(optionsBuilder.build()).build();
  }

  /** Primary chat model (defaults to text model for backward compatibility) */
  @Bean
  @Primary
  public ChatModel defaultChatModel() {
    log.info("Creating default chat model (using text model for backward compatibility)");
    return textChatModel();
  }
}
