package ai.bluefields.ppt2video.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Microsoft Graph API integration. Configures authentication and client
 * setup for MS Graph operations.
 */
@Configuration
@ConditionalOnProperty(name = "app.msgraph.enabled", havingValue = "true")
@Slf4j
@Getter
public class MSGraphConfiguration {

  @Value("${app.msgraph.tenant-id}")
  private String tenantId;

  @Value("${app.msgraph.client-id}")
  private String clientId;

  @Value("${app.msgraph.client-secret}")
  private String clientSecret;

  @Value("${app.msgraph.scope}")
  private String scope;

  @Value("${app.msgraph.drive-id:}")
  private String driveId;

  @Value("${app.msgraph.site-id:}")
  private String siteId;

  @Value("${app.msgraph.connect-timeout:30000}")
  private int connectTimeout;

  @Value("${app.msgraph.read-timeout:60000}")
  private int readTimeout;

  @Value("${app.msgraph.retry-attempts:3}")
  private int retryAttempts;

  @Value("${app.msgraph.retry-delay:1000}")
  private long retryDelay;

  @Value("${app.msgraph.cleanup-enabled:true}")
  private boolean cleanupEnabled;

  @Value("${app.msgraph.pdf-quality:95}")
  private int pdfQuality;

  /**
   * Creates Azure AD client credentials for authentication.
   *
   * @return ClientSecretCredential for Azure AD authentication
   */
  @Bean
  public ClientSecretCredential azureClientCredential() {
    log.info("Configuring Azure AD client credentials for tenant: {}", tenantId);

    return new ClientSecretCredentialBuilder()
        .tenantId(tenantId)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .build();
  }

  /**
   * Creates the Microsoft Graph service client.
   *
   * @param credential Azure AD client credential
   * @return Configured GraphServiceClient
   */
  @Bean
  public GraphServiceClient graphServiceClient(ClientSecretCredential credential) {
    log.info("Creating Microsoft Graph service client");

    // Parse scopes
    List<String> scopes = Arrays.asList(scope.split(" "));

    // Build and return the Graph client with v6 API
    return new GraphServiceClient(credential, scopes.toArray(new String[0]));
  }

  /**
   * Validates MS Graph configuration.
   *
   * @return true if configuration is valid
   */
  public boolean isConfigurationValid() {
    return tenantId != null
        && !tenantId.isEmpty()
        && clientId != null
        && !clientId.isEmpty()
        && clientSecret != null
        && !clientSecret.isEmpty();
  }

  /**
   * Gets the configured OneDrive ID or default "me/drive".
   *
   * @return Drive ID for MS Graph operations
   */
  public String getEffectiveDriveId() {
    // When using application permissions, we can't use "me/drive"
    // Return the configured drive ID or null to signal that we need to find a drive
    return (driveId != null && !driveId.isEmpty()) ? driveId : null;
  }

  /**
   * Checks if SharePoint site is configured.
   *
   * @return true if site ID is configured
   */
  public boolean isSharePointConfigured() {
    return siteId != null && !siteId.isEmpty();
  }
}
