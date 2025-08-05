package ai.bluefields.ppt2video.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous processing in the application. Provides both traditional thread
 * pool and virtual thread executors.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  /**
   * Configures the default task executor for backward compatibility. Uses traditional platform
   * threads with a fixed pool size.
   *
   * @return configured thread pool task executor
   */
  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(10);
    executor.setThreadNamePrefix("async-");
    executor.initialize();
    return executor;
  }

  /**
   * Creates an executor that uses virtual threads for async operations. Virtual threads are
   * lightweight and perfect for I/O-bound tasks like: - Database operations - File I/O - Network
   * calls (MS Graph API) - PowerPoint parsing and rendering
   *
   * @return AsyncTaskExecutor that creates a new virtual thread per task
   */
  @Bean(name = "virtualThreadExecutor")
  public AsyncTaskExecutor virtualThreadExecutor() {
    // Creates a new virtual thread for each task
    // No need to configure pool sizes - virtual threads scale automatically
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
