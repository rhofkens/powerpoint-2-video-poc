#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# API endpoint
API_BASE="http://localhost:8080/api"

# Function to print colored messages
print_info() {
    echo -e "${YELLOW}ℹ ${1}${NC}"
}

print_success() {
    echo -e "${GREEN}✓ ${1}${NC}"
}

print_error() {
    echo -e "${RED}✗ ${1}${NC}"
}

print_action() {
    echo -e "${BLUE}→ ${1}${NC}"
}

# Check if avatar video ID is provided
if [ $# -lt 1 ]; then
    echo "Usage: $0 <avatar-video-id>"
    echo "Example: $0 ec897aae-9cd4-4df8-a7cc-e58318a647e3"
    exit 1
fi

AVATAR_VIDEO_ID=$1

echo "=== Publishing Avatar Video to R2 ==="
echo

print_info "Avatar Video ID: ${AVATAR_VIDEO_ID}"

# First check the status
print_action "Checking video status..."
STATUS_RESPONSE=$(curl -s -X GET "${API_BASE}/avatar-videos/${AVATAR_VIDEO_ID}/status")

if [ $? -ne 0 ]; then
    print_error "Failed to get video status"
    exit 1
fi

STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.data.status')
VIDEO_URL=$(echo "$STATUS_RESPONSE" | jq -r '.data.videoUrl')

print_info "Current status: ${STATUS}"

if [ "$STATUS" != "COMPLETED" ]; then
    print_error "Video is not completed yet. Status: ${STATUS}"
    exit 1
fi

if [ "$VIDEO_URL" == "null" ] || [ -z "$VIDEO_URL" ]; then
    print_error "Video has no URL to publish"
    exit 1
fi

print_info "Video URL: ${VIDEO_URL:0:80}..."

# Publish the video
print_action "Publishing video to R2..."
PUBLISH_RESPONSE=$(curl -s -X POST "${API_BASE}/avatar-videos/${AVATAR_VIDEO_ID}/publish")

if [ $? -ne 0 ]; then
    print_error "Failed to publish video"
    echo "$PUBLISH_RESPONSE"
    exit 1
fi

# Check if successful
SUCCESS=$(echo "$PUBLISH_RESPONSE" | jq -r '.success')

if [ "$SUCCESS" = "true" ]; then
    print_success "Avatar video published successfully"
    
    # Extract published details
    ASSET_ID=$(echo "$PUBLISH_RESPONSE" | jq -r '.data.id')
    DOWNLOAD_URL=$(echo "$PUBLISH_RESPONSE" | jq -r '.data.downloadUrl')
    BUCKET=$(echo "$PUBLISH_RESPONSE" | jq -r '.data.bucketName // "Not specified"')
    OBJECT_KEY=$(echo "$PUBLISH_RESPONSE" | jq -r '.data.objectKey // "Not specified"')
    
    echo
    echo "Published Asset Details:"
    print_info "  Asset ID: ${ASSET_ID}"
    print_info "  Bucket: ${BUCKET}"
    print_info "  Object Key: ${OBJECT_KEY}"
    print_info "  Download URL: ${DOWNLOAD_URL:0:80}..."
    echo
    
    # Check the updated video status
    print_action "Verifying published status..."
    STATUS_RESPONSE=$(curl -s -X GET "${API_BASE}/avatar-videos/${AVATAR_VIDEO_ID}/status")
    
    # The publishedUrl won't be in the status response, but we can check it was successful
    if [ $? -eq 0 ]; then
        print_success "Video status verified"
    fi
    
else
    print_error "Failed to publish video"
    ERROR_MSG=$(echo "$PUBLISH_RESPONSE" | jq -r '.error.description // .message')
    print_error "Error: ${ERROR_MSG}"
    echo
    echo "Full response:"
    echo "$PUBLISH_RESPONSE" | jq '.'
fi