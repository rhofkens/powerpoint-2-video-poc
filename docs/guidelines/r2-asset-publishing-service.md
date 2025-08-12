# Cloudflare R2 Asset Publishing Service - Implementation Overview

## Executive Summary

This document outlines the architecture and implementation plan for integrating Cloudflare R2 storage into the PowerPoint-to-Video application. The service will manage asset storage, retrieval, and lifecycle management for presentation-related media files using AWS SDK v2 with Cloudflare R2's S3-compatible API.

## Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (React)                        │
└──────────────────────┬──────────────────────────────────────┘
                       │ REST API
┌──────────────────────▼──────────────────────────────────────┐
│                  Spring Boot Backend                         │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                 Controller Layer                        │ │
│  │  - AssetController                                      │ │
│  │  - PresignedUrlController                              │ │
│  └──────────────────┬─────────────────────────────────────┘ │
│  ┌──────────────────▼─────────────────────────────────────┐ │
│  │                  Service Layer                          │ │
│  │  - R2AssetService                                       │ │
│  │  - PresignedUrlService                                  │ │
│  │  - AssetMetadataService                                 │ │
│  └──────────────────┬─────────────────────────────────────┘ │
│  ┌──────────────────▼─────────────────────────────────────┐ │
│  │              Configuration Layer                        │ │
│  │  - R2Configuration                                      │ │
│  │  - R2ClientFactory                                      │ │
│  └──────────────────┬─────────────────────────────────────┘ │
│  ┌──────────────────▼─────────────────────────────────────┐ │
│  │              Repository Layer                           │ │
│  │  - AssetMetadataRepository                              │ │
│  │  - PresignedUrlRepository                               │ │
│  └──────────────────┬─────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                     PostgreSQL Database                      │
│  - asset_metadata                                            │
│  - presigned_urls                                            │
└──────────────────────────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│                   Cloudflare R2 Storage                      │
│  ┌────────────────────┐  ┌────────────────────┐            │
│  │  video-assets      │  │ video-generation   │            │
│  │  - slide_image     │  │ - presentation_    │            │
│  │  - slide_audio     │  │   full_video       │            │
│  │  - slide_avatar_   │  │                    │            │
│  │    video           │  │                    │            │
│  │  - presentation_   │  │                    │            │
│  │    intro_video     │  │                    │            │
│  └────────────────────┘  └────────────────────┘            │
└──────────────────────────────────────────────────────────────┘
```

## Database Schema Design

### 1. asset_metadata Table

```sql
CREATE TABLE asset_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id) ON DELETE CASCADE,
    slide_id UUID REFERENCES slides(id) ON DELETE CASCADE,
    asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN (
        'SLIDE_IMAGE', 
        'SLIDE_AUDIO', 
        'SLIDE_AVATAR_VIDEO', 
        'PRESENTATION_INTRO_VIDEO',
        'PRESENTATION_FULL_VIDEO'
    )),
    bucket_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    content_type VARCHAR(100),
    checksum VARCHAR(255),
    metadata JSONB,
    upload_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (upload_status IN (
        'PENDING', 
        'UPLOADING', 
        'COMPLETED', 
        'FAILED'
    )),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(bucket_name, object_key)
);

CREATE INDEX idx_asset_metadata_presentation_id ON asset_metadata(presentation_id);
CREATE INDEX idx_asset_metadata_slide_id ON asset_metadata(slide_id);
CREATE INDEX idx_asset_metadata_asset_type ON asset_metadata(asset_type);
CREATE INDEX idx_asset_metadata_upload_status ON asset_metadata(upload_status);
```

### 2. presigned_urls Table

```sql
CREATE TABLE presigned_urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_metadata_id UUID NOT NULL REFERENCES asset_metadata(id) ON DELETE CASCADE,
    url_type VARCHAR(50) NOT NULL CHECK (url_type IN ('UPLOAD', 'DOWNLOAD')),
    presigned_url TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    access_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    UNIQUE(asset_metadata_id, url_type, expires_at)
);

CREATE INDEX idx_presigned_urls_asset_metadata_id ON presigned_urls(asset_metadata_id);
CREATE INDEX idx_presigned_urls_expires_at ON presigned_urls(expires_at);
CREATE INDEX idx_presigned_urls_is_active ON presigned_urls(is_active);
```

## Phase 1 Implementation (Current Focus)

### Core Components to Implement

#### 1. Configuration Layer

##### R2Configuration.java
```java
@Configuration
@ConfigurationProperties(prefix = "cloudflare.r2")
@Data
public class R2Configuration {
    private String accessKeyId;
    private String secretAccessKey;
    private String endpoint;
    private String region = "auto";
    private Map<String, BucketConfig> buckets;
    private UrlConfig urls;
    
    @Data
    public static class BucketConfig {
        private String name;
        private String prefix;
    }
    
    @Data
    public static class UrlConfig {
        private Duration defaultExpiration = Duration.ofHours(48);
        private Duration maxExpiration = Duration.ofDays(7);
    }
}
```

##### R2ClientFactory.java
```java
@Component
public class R2ClientFactory {
    
    private final R2Configuration config;
    private final S3Client s3Client;
    private final S3Presigner presigner;
    
    @PostConstruct
    public void initialize() {
        AwsCredentials credentials = AwsBasicCredentials.create(
            config.getAccessKeyId(),
            config.getSecretAccessKey()
        );
        
        this.s3Client = S3Client.builder()
            .region(Region.of(config.getRegion()))
            .endpointOverride(URI.create(config.getEndpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
            
        this.presigner = S3Presigner.builder()
            .region(Region.of(config.getRegion()))
            .endpointOverride(URI.create(config.getEndpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }
}
```

#### 2. Service Layer

##### R2AssetService.java
Core service for asset management operations:
- Upload assets to R2 buckets
- Download assets from R2
- Delete assets
- List assets by presentation/slide
- Generate object keys with proper structure

##### PresignedUrlService.java
Manages pre-signed URL generation and lifecycle:
- Generate upload/download URLs
- Track URL expiration
- Re-sign expired URLs
- Validate URL access

##### AssetMetadataService.java
Handles database operations for asset metadata:
- Create/update asset records
- Query assets by various criteria
- Track upload status

#### 3. Entity Layer

##### AssetMetadata.java
```java
@Entity
@Table(name = "asset_metadata")
@Data
public class AssetMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slide_id")
    private Slide slide;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;
    
    @Column(name = "bucket_name", nullable = false)
    private String bucketName;
    
    @Column(name = "object_key", nullable = false)
    private String objectKey;
    
    // Additional fields...
}
```

#### 4. REST API Endpoints

##### Asset Management Endpoints

```
POST   /api/assets/upload
       - Initiate asset upload with pre-signed URL
       
GET    /api/assets/{assetId}
       - Get asset metadata with download URL
       
DELETE /api/assets/{assetId}
       - Delete asset from R2 and database
       
GET    /api/presentations/{presentationId}/assets
       - List all assets for a presentation
       
GET    /api/slides/{slideId}/assets
       - List all assets for a slide
```

##### Pre-signed URL Endpoints

```
POST   /api/assets/{assetId}/presigned-url
       - Generate new pre-signed URL
       
POST   /api/assets/{assetId}/resign
       - Re-sign expired URL
       
GET    /api/assets/{assetId}/urls
       - Get all URLs for an asset
```

### Asset Upload Flow

```
1. Client requests upload URL
2. Backend generates pre-signed upload URL
3. Backend creates asset metadata record (PENDING)
4. Client uploads directly to R2
5. Client confirms upload completion
6. Backend updates metadata (COMPLETED)
7. Backend generates download URL
```

### Asset Retrieval Flow

```
1. Client requests asset
2. Backend checks existing pre-signed URLs
3. If valid URL exists, return it
4. If expired, generate new URL
5. Return pre-signed download URL
6. Track access for analytics
```

## Security Considerations

### Access Control
- Pre-signed URLs with limited lifetime (48 hours default)
- URL regeneration on expiration
- Per-presentation access validation
- Rate limiting on URL generation

### Environment Variables
```bash
CLOUDFLARE_R2_ACCESS_KEY_ID=<access_key>
CLOUDFLARE_R2_SECRET_ACCESS_KEY=<secret_key>
CLOUDFLARE_R2_EU_ENDPOINT=https://<account_id>.r2.cloudflarestorage.com
CLOUDFLARE_R2_TOKEN=<optional_token>
```

## Error Handling Strategy

### Retry Logic
- Exponential backoff for transient failures
- Maximum 3 retry attempts
- Circuit breaker for R2 service failures

### Fallback Mechanisms
- Queue failed uploads for retry
- Maintain upload status in database
- Alert on persistent failures

## Manual Testing Strategy

### Test Scripts Directory Structure
```
backend/scripts/test-r2/
├── 01-upload-asset.sh
├── 02-get-asset.sh
├── 03-list-presentation-assets.sh
├── 04-list-slide-assets.sh
├── 05-resign-url.sh
├── 06-delete-asset.sh
├── test-data/
│   ├── sample-image.png
│   ├── sample-audio.mp3
│   └── test-config.env
└── run-all-tests.sh
```

### Sample CURL Test Scripts

#### 01-upload-asset.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing Asset Upload ==="

# Step 1: Request upload URL
echo "1. Requesting upload URL..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/assets/upload" \
  -H "Content-Type: application/json" \
  -d '{
    "presentationId": "'$PRESENTATION_ID'",
    "slideId": "'$SLIDE_ID'",
    "assetType": "SLIDE_IMAGE",
    "fileName": "test-slide.png",
    "contentType": "image/png",
    "fileSize": 1024000
  }')

echo "Response: $RESPONSE"
ASSET_ID=$(echo $RESPONSE | jq -r '.assetId')
UPLOAD_URL=$(echo $RESPONSE | jq -r '.uploadUrl')

echo "Asset ID: $ASSET_ID"
echo "Upload URL: $UPLOAD_URL"

# Step 2: Upload file to R2 using pre-signed URL
echo "2. Uploading file to R2..."
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/png" \
  --data-binary "@test-data/sample-image.png"

# Step 3: Confirm upload completion
echo "3. Confirming upload completion..."
curl -X POST "$BASE_URL/api/assets/$ASSET_ID/confirm-upload" \
  -H "Content-Type: application/json" | jq '.'
```

#### 02-get-asset.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing Get Asset ==="

curl -X GET "$BASE_URL/api/assets/$1" \
  -H "Accept: application/json" | jq '.'
```

#### 03-list-presentation-assets.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing List Presentation Assets ==="

curl -X GET "$BASE_URL/api/presentations/$PRESENTATION_ID/assets" \
  -H "Accept: application/json" | jq '.'
```

#### 04-list-slide-assets.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing List Slide Assets ==="

curl -X GET "$BASE_URL/api/slides/$SLIDE_ID/assets" \
  -H "Accept: application/json" | jq '.'
```

#### 05-resign-url.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing Re-sign URL ==="

curl -X POST "$BASE_URL/api/assets/$1/resign" \
  -H "Content-Type: application/json" | jq '.'
```

#### 06-delete-asset.sh
```bash
#!/bin/bash
source test-data/test-config.env

echo "=== Testing Delete Asset ==="

curl -X DELETE "$BASE_URL/api/assets/$1" \
  -H "Accept: application/json" | jq '.'
```

#### test-config.env
```bash
# Test Configuration
BASE_URL="http://localhost:8080"
PRESENTATION_ID="<valid-presentation-uuid>"
SLIDE_ID="<valid-slide-uuid>"
```

#### run-all-tests.sh
```bash
#!/bin/bash
echo "Running R2 Asset Service Tests..."

# Run upload test and capture asset ID
./01-upload-asset.sh

echo "Enter Asset ID from upload test: "
read ASSET_ID

# Run remaining tests with the asset ID
./02-get-asset.sh $ASSET_ID
./03-list-presentation-assets.sh
./04-list-slide-assets.sh
./05-resign-url.sh $ASSET_ID
./06-delete-asset.sh $ASSET_ID

echo "All tests completed!"
```

## Dependencies Required

Add to `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.32.20</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- AWS SDK v2 for S3 -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
    </dependency>
    
    <!-- AWS SDK v2 for S3 Transfer Manager -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3-transfer-manager</artifactId>
    </dependency>
    
    <!-- Apache Tika for content type detection -->
    <dependency>
        <groupId>org.apache.tika</groupId>
        <artifactId>tika-core</artifactId>
        <version>2.9.2</version>
    </dependency>
</dependencies>
```

## Sample Implementation Code

### Upload with Pre-signed URL

```java
@Service
@Transactional
public class R2AssetService {
    
    public AssetUploadResponse initiateUpload(AssetUploadRequest request) {
        // Generate object key
        String objectKey = generateObjectKey(request);
        
        // Create metadata record
        AssetMetadata metadata = assetMetadataService.create(
            AssetMetadata.builder()
                .presentationId(request.getPresentationId())
                .slideId(request.getSlideId())
                .assetType(request.getAssetType())
                .bucketName(determineBucket(request.getAssetType()))
                .objectKey(objectKey)
                .uploadStatus(UploadStatus.PENDING)
                .build()
        );
        
        // Generate pre-signed upload URL
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(30))
                .putObjectRequest(PutObjectRequest.builder()
                    .bucket(metadata.getBucketName())
                    .key(objectKey)
                    .contentType(request.getContentType())
                    .build())
                .build()
        );
        
        // Save URL record
        presignedUrlService.create(metadata, presignedRequest.url(), UrlType.UPLOAD);
        
        return AssetUploadResponse.builder()
            .assetId(metadata.getId())
            .uploadUrl(presignedRequest.url().toString())
            .expiresAt(Instant.now().plus(Duration.ofMinutes(30)))
            .build();
    }
}
```

## Monitoring and Observability

### Metrics to Track
- Upload/download success rates
- Average file sizes by type
- URL generation frequency
- Storage usage per presentation
- API response times

### Logging Strategy
- Structured logging with correlation IDs
- Asset operation audit trail
- Error tracking with context
- Performance metrics logging

## Future Phases

### Phase 2: Integration (Future)
- Integrate with existing rendering pipeline
- Migrate existing local assets to R2
- Implement audio file uploads from TTS service
- Add monitoring and metrics dashboard

### Phase 3: Advanced Features (Future)
- Video file support for avatar and intro videos
- Multipart upload for large files
- CDN integration for global distribution
- Asset versioning and lifecycle policies

## Conclusion

This Phase 1 implementation provides the core asset publishing service for Cloudflare R2. The design focuses on establishing the fundamental infrastructure for asset storage and retrieval with pre-signed URLs, proper error handling, and manual testing capabilities. The service operates independently and can be integrated with existing services in future phases.

## Next Steps for Phase 1

1. Review and approve this architecture
2. Set up R2 buckets in Cloudflare dashboard
3. Configure environment variables
4. Implement core services
5. Create database migrations
6. Develop REST endpoints
7. Create and test with CURL scripts
8. Deploy to staging environment