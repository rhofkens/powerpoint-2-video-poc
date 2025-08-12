package ai.bluefields.ppt2video.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Factory component for creating and managing S3 clients for Cloudflare R2. Provides configured
 * S3Client and S3Presigner instances with R2-specific settings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Getter
public class R2ClientFactory {

  private final R2Configuration config;
  private S3Client s3Client;
  private S3Presigner presigner;

  @PostConstruct
  public void initialize() {
    log.info("Initializing R2 client with endpoint: {}", config.getEndpoint());

    AwsCredentials credentials =
        AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());

    StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(credentials);

    S3Configuration s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

    this.s3Client =
        S3Client.builder()
            .region(Region.of(config.getRegion()))
            .endpointOverride(URI.create(config.getEndpoint()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(s3Config)
            .build();

    this.presigner =
        S3Presigner.builder()
            .region(Region.of(config.getRegion()))
            .endpointOverride(URI.create(config.getEndpoint()))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(s3Config)
            .build();

    log.info("R2 client initialized successfully");
  }

  @PreDestroy
  public void cleanup() {
    if (s3Client != null) {
      s3Client.close();
      log.info("S3 client closed");
    }
    if (presigner != null) {
      presigner.close();
      log.info("S3 presigner closed");
    }
  }
}
