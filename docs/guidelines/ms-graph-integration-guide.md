# Microsoft Graph Integration Guide

This comprehensive guide covers everything you need to set up and use Microsoft Graph API integration for high-fidelity PowerPoint slide rendering.

## Overview

The MS Graph renderer uses Microsoft Graph API to:
- Upload PowerPoint files to OneDrive
- Convert presentations to PDF using OneDrive's conversion capabilities
- Extract individual slides at 1920x1080px resolution
- Optionally use SharePoint preview API as a fallback method

## Prerequisites

- Azure Active Directory (Azure AD) tenant
- Azure subscription (free tier works)
- Admin access to register an application in Azure AD

## Step 1: Register an Azure AD Application

1. Sign in to the [Azure Portal](https://portal.azure.com)
2. Navigate to **Azure Active Directory** > **App registrations**
3. Click **New registration**
4. Configure the application:
   - **Name**: `PowerPoint2Video-MSGraph` (or your preferred name)
   - **Supported account types**: Select "Accounts in this organizational directory only"
   - **Redirect URI**: Leave blank (not needed for client credentials flow)
5. Click **Register**
6. Note down the following from the Overview page:
   - **Application (client) ID**
   - **Directory (tenant) ID**

## Step 2: Configure API Permissions

1. In your app registration, go to **API permissions**
2. Click **Add a permission**
3. Select **Microsoft Graph**
4. Choose **Application permissions** (not Delegated)
5. Add the following permissions:
   - `Files.ReadWrite.All` - For OneDrive file operations
   - `Sites.ReadWrite.All` - For SharePoint operations (optional)
6. Click **Grant admin consent** for your organization
7. Confirm all permissions show "Granted" status

## Step 3: Create Client Secret

1. Go to **Certificates & secrets**
2. Click **New client secret**
3. Add a description (e.g., "PPT2Video Client Secret")
4. Select expiration (recommend 24 months)
5. Click **Add**
6. **IMPORTANT**: Copy the secret value immediately (it won't be shown again)

## Step 4: Configure the Application

Set the following environment variables or update `application.properties`:

```bash
# Required MS Graph configuration
export AZURE_TENANT_ID="your-tenant-id"
export AZURE_CLIENT_ID="your-client-id"
export AZURE_CLIENT_SECRET="your-client-secret"

# Optional - for specific OneDrive/SharePoint locations
export MSGRAPH_DRIVE_ID=""  # Leave empty for default drive
export MSGRAPH_SITE_ID=""   # Only needed for SharePoint
```

Or update `application.properties`:

```properties
# Enable MS Graph renderer
app.msgraph.enabled=true
app.msgraph.tenant-id=${AZURE_TENANT_ID}
app.msgraph.client-id=${AZURE_CLIENT_ID}
app.msgraph.client-secret=${AZURE_CLIENT_SECRET}

# Optional configuration
app.msgraph.drive-id=
app.msgraph.site-id=
app.msgraph.cleanup-enabled=true
app.msgraph.pdf-quality=95
```

## Step 5: Update Renderer Priority

To use MS Graph as the primary renderer:

```properties
app.slide-rendering.renderer-priority=MSGRAPH,ASPOSE,ENHANCED_POI,DEFAULT_POI
```

## Understanding Drive IDs and Site IDs

### Drive ID

A Drive ID identifies a specific OneDrive or SharePoint document library.

#### When Do You Need a Drive ID?

- **Leave it empty** (recommended): The app will use the default OneDrive for the application
- **Specify a drive ID**: Only if you want to use a specific shared drive or SharePoint library

#### How to Find Your Drive ID

**Method 1: Using Graph Explorer (Easiest)**

1. Go to [Microsoft Graph Explorer](https://developer.microsoft.com/en-us/graph/graph-explorer)
2. Sign in with your Microsoft account
3. Run this query: `GET https://graph.microsoft.com/v1.0/me/drive`
4. Look for the `"id"` field in the response - that's your drive ID

Example response:
```json
{
  "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#drives/$entity",
  "id": "b!5O2H4...",  // This is your drive ID
  "driveType": "business",
  "owner": {
    "user": {
      "displayName": "Your Name",
      "id": "..."
    }
  }
}
```

**Method 2: For SharePoint Document Libraries**

If you want to use a SharePoint document library instead:

1. Find your SharePoint site ID first:
   ```
   GET https://graph.microsoft.com/v1.0/sites/{hostname}:/{site-path}
   ```

2. Then get the drive ID for that site:
   ```
   GET https://graph.microsoft.com/v1.0/sites/{site-id}/drive
   ```

#### Important Notes About Drive IDs

- The drive ID is NOT a folder ID
- You don't need to create any folders - the app will create them automatically
- The app uploads to a temporary location and cleans up after processing
- For most use cases, leaving drive-id empty is the best approach

### Site ID

A Site ID identifies a specific SharePoint site in your Microsoft 365 tenant.

#### What is a Site ID?

SharePoint sites include:
- Team sites (created with Microsoft Teams)
- Communication sites
- Classic team sites
- OneDrive for Business sites

#### When Do You Need a Site ID?

- **For OneDrive operations**: You DON'T need a site ID (leave it empty)
- **For SharePoint operations**: You need a site ID if you want to upload files to a specific SharePoint document library

#### How to Find Your Site ID

**Method 1: Using Graph Explorer (Easiest)**

1. Go to [Microsoft Graph Explorer](https://developer.microsoft.com/en-us/graph/graph-explorer)
2. Sign in with your Microsoft account
3. Use one of these queries:

   **For your organization's root site:**
   ```
   GET https://graph.microsoft.com/v1.0/sites/root
   ```

   **For a specific site by URL:**
   ```
   GET https://graph.microsoft.com/v1.0/sites/{hostname}:/{site-path}
   ```
   
   Example for a site at `https://contoso.sharepoint.com/sites/Engineering`:
   ```
   GET https://graph.microsoft.com/v1.0/sites/contoso.sharepoint.com:/sites/Engineering
   ```

4. Look for the `"id"` field in the response - that's your site ID

Example response:
```json
{
  "@odata.context": "https://graph.microsoft.com/v1.0/$metadata#sites/$entity",
  "id": "contoso.sharepoint.com,5a58bb09-1fba-41c3-8946-9a2b9d510b56,9b0b5a2f-48ac-4445-9f47-f3b9d6deb930",
  "name": "Engineering Team",
  "displayName": "Engineering",
  "webUrl": "https://contoso.sharepoint.com/sites/Engineering"
}
```

**Method 2: Using SharePoint Admin Center**

1. Go to SharePoint Admin Center
2. Click on "Sites" > "Active sites"
3. Click on the site name
4. The URL will contain the site ID (though it's encoded)

**Method 3: List All Sites**

To see all available sites:
```
GET https://graph.microsoft.com/v1.0/sites?search=*
```

#### Common Site ID Formats

- **Root site**: Often looks like `{hostname},{guid},{guid}`
- **Team sites**: Include the full path in the ID
- **Personal sites**: OneDrive sites have their own format

## Configuration Examples

### Using OneDrive (Recommended for most cases):
```properties
app.msgraph.drive-id=
app.msgraph.site-id=
# Both empty - uses default OneDrive
```

### Using a specific SharePoint site:
```properties
app.msgraph.drive-id=
app.msgraph.site-id=contoso.sharepoint.com,5a58bb09-1fba-41c3-8946-9a2b9d510b56,9b0b5a2f-48ac-4445-9f47-f3b9d6deb930
```

### Using both (app will prefer SharePoint):
```properties
app.msgraph.drive-id=b!5O2H4...
app.msgraph.site-id=contoso.sharepoint.com,5a58bb09-1fba-41c3-8946-9a2b9d510b56,9b0b5a2f-48ac-4445-9f47-f3b9d6deb930
```

## When to Use SharePoint vs OneDrive

**Use OneDrive (leave site-id empty) when:**
- You're doing personal or small-scale conversions
- You want simpler setup
- You don't have SharePoint access

**Use SharePoint (specify site-id) when:**
- You're in an enterprise environment with SharePoint
- You want to use a team's shared storage
- You need to comply with specific data governance policies
- You want better audit trails and compliance features

## Testing the Configuration

1. Start the application
2. Check logs for:
   ```
   Configuring Azure AD client credentials for tenant: <your-tenant-id>
   Creating Microsoft Graph service client
   ```
3. Upload a PowerPoint file through the UI
4. Verify MS Graph renderer is being used in the logs

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Verify tenant ID, client ID, and secret are correct
   - Ensure admin consent was granted for permissions
   - Check the secret hasn't expired

2. **File Upload Errors**
   - Verify `Files.ReadWrite.All` permission is granted
   - Check network connectivity to Microsoft Graph API
   - Review logs for specific error messages

3. **PDF Conversion Failures**
   - Ensure the uploaded file is a valid PowerPoint format
   - Check OneDrive storage quota isn't exceeded
   - Verify the file isn't password-protected

4. **Performance Issues**
   - Initial uploads may be slower due to authentication
   - Large files take longer to convert
   - Consider enabling file caching in production

### Debug Mode

Enable debug logging for MS Graph operations:

```properties
logging.level.ai.bluefields.ppt2video.service.MSGraphService=DEBUG
logging.level.ai.bluefields.ppt2video.service.rendering.msgraph=DEBUG
```

## Security Considerations

1. **Credentials Storage**
   - Never commit credentials to source control
   - Use environment variables or secure vaults
   - Rotate secrets regularly

2. **File Cleanup**
   - The renderer automatically deletes uploaded files after processing
   - Set `app.msgraph.cleanup-enabled=false` to disable (not recommended)

3. **API Limits**
   - Microsoft Graph has rate limits
   - The implementation includes retry logic with exponential backoff
   - Monitor usage to avoid throttling

## Production Recommendations

1. Use Azure Key Vault for storing credentials
2. Implement caching for frequently used presentations
3. Monitor API usage and set up alerts
4. Consider using a dedicated OneDrive account for conversions
5. Enable application insights for performance monitoring

## Important Notes

1. **You usually don't need Site ID**: For most PowerPoint-to-video conversions, using OneDrive (default) is simpler and works fine
2. **SharePoint requires additional permissions**: Make sure your app has `Sites.ReadWrite.All` permission
3. **The app creates temporary folders**: Whether using OneDrive or SharePoint, the app creates temporary folders and cleans up after itself
4. **Site ID format**: It's typically `{hostname},{site-guid},{web-guid}` - use the full string

## Additional Resources

- [Microsoft Graph API Documentation](https://docs.microsoft.com/en-us/graph/)
- [Azure AD App Registration Guide](https://docs.microsoft.com/en-us/azure/active-directory/develop/quickstart-register-app)
- [Graph API Permissions Reference](https://docs.microsoft.com/en-us/graph/permissions-reference)