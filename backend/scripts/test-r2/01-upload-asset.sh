#!/bin/bash

source test-data/test-config.env

echo "=== Testing Asset Upload ==="
echo

# Step 1: Request upload URL
print_info "Step 1: Requesting upload URL..."
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

# Check if request was successful
if [ $? -ne 0 ]; then
    print_error "Failed to request upload URL"
    exit 1
fi

echo "Response: $RESPONSE"
echo

# Parse response
ASSET_ID=$(echo $RESPONSE | jq -r '.assetId')
UPLOAD_URL=$(echo $RESPONSE | jq -r '.uploadUrl')
EXPIRES_AT=$(echo $RESPONSE | jq -r '.expiresAt')
BUCKET_NAME=$(echo $RESPONSE | jq -r '.bucketName')
OBJECT_KEY=$(echo $RESPONSE | jq -r '.objectKey')

if [ "$ASSET_ID" = "null" ] || [ -z "$ASSET_ID" ]; then
    print_error "Failed to get asset ID from response"
    exit 1
fi

print_success "Asset ID: $ASSET_ID"
print_success "Bucket: $BUCKET_NAME"
print_success "Object Key: $OBJECT_KEY"
print_info "Upload URL expires at: $EXPIRES_AT"
echo

# Save asset ID for later tests
echo "export LAST_ASSET_ID=$ASSET_ID" > test-data/last-asset.env

# Step 2: Upload file to R2 using pre-signed URL
print_info "Step 2: Uploading file to R2..."

# Check if sample image exists
if [ ! -f "test-data/sample-image.png" ]; then
    print_info "Creating sample image..."
    # Create a simple 1x1 pixel PNG if it doesn't exist
    echo "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==" | base64 -d > test-data/sample-image.png
fi

# Upload to R2
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/png" \
  --data-binary "@test-data/sample-image.png")

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
    print_success "File uploaded successfully (HTTP $HTTP_CODE)"
else
    print_error "Failed to upload file (HTTP $HTTP_CODE)"
    echo "Try running with -v flag for more details:"
    echo "curl -v -X PUT \"$UPLOAD_URL\" -H \"Content-Type: image/png\" --data-binary \"@test-data/sample-image.png\""
    exit 1
fi
echo

# Step 3: Confirm upload completion
print_info "Step 3: Confirming upload completion..."
FILE_SIZE=$(stat -f%z test-data/sample-image.png 2>/dev/null || stat -c%s test-data/sample-image.png 2>/dev/null || echo "85")

CONFIRM_RESPONSE=$(curl -s -X POST "$BASE_URL/api/assets/$ASSET_ID/confirm-upload" \
  -H "Content-Type: application/json" \
  -d '{
    "fileSize": '$FILE_SIZE',
    "checksum": "test-checksum"
  }')

if [ $? -eq 0 ]; then
    print_success "Upload confirmed successfully"
    echo "Asset details:"
    echo "$CONFIRM_RESPONSE" | jq '.'
else
    print_error "Failed to confirm upload"
fi
echo

print_success "Asset upload test completed!"
print_info "Asset ID saved to test-data/last-asset.env"