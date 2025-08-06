package ai.bluefields.ppt2video.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/** Configuration for AI services including retry policies and timeouts. */
@Configuration
public class AIConfiguration {

  /**
   * Configure retry template for AI API calls with exponential backoff.
   *
   * @return Configured RetryTemplate
   */
  @Bean
  public RetryTemplate retryTemplate() {
    RetryTemplate retryTemplate = new RetryTemplate();

    // Configure retry policy
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(3);
    retryTemplate.setRetryPolicy(retryPolicy);

    // Configure backoff policy
    ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
    backOffPolicy.setInitialInterval(2000); // 2 seconds
    backOffPolicy.setMultiplier(2.0);
    backOffPolicy.setMaxInterval(10000); // 10 seconds
    retryTemplate.setBackOffPolicy(backOffPolicy);

    return retryTemplate;
  }
}
