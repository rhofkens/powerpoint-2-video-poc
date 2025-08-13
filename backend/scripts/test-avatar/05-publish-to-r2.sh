#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPT_DIR/test-config.env"

# Check if video ID is provided as argument or use last saved
if [ $# -eq 1 ]; then
    VIDEO_ID="$1"
elif [ -f "$SCRIPT_DIR/.last_video_id" ]; then
    VIDEO_ID=$(cat "$SCRIPT_DIR/.last_video_id")
else
    print_error "No video ID provided and no saved ID found"
    echo "Usage: $0 [video_id]"
    exit 1
fi

echo "=== Publishing Avatar Video to R2 ==="
echo
print_info "Video ID: $VIDEO_ID"
echo

# Step 1: First check if video is completed
print_step "Checking video status..."
STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/avatar-videos/$VIDEO_ID/status" \
  -H "Accept: application/json")

STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.status // "UNKNOWN"')

if [ "$STATUS" != "COMPLETED" ]; then
    print_error "Video is not completed yet (Status: $STATUS)"
    print_info "Please wait for video generation to complete before publishing"
    exit 1
fi

print_success "Video status: COMPLETED"
echo

# Step 2: Publish to R2
print_step "Publishing avatar video to R2..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/avatar-videos/$VIDEO_ID/publish-to-r2" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to publish video to R2"
        echo "$RESPONSE" | jq '.'
    else
        print_success "Avatar video published to R2"
        echo
        echo "Asset details:"
        echo "$RESPONSE" | jq '.'
        
        # Extract R2 details
        ASSET_ID=$(echo "$RESPONSE" | jq -r '.id // "null"')
        BUCKET=$(echo "$RESPONSE" | jq -r '.bucketName // "null"')
        OBJECT_KEY=$(echo "$RESPONSE" | jq -r '.objectKey // "null"')
        DOWNLOAD_URL=$(echo "$RESPONSE" | jq -r '.downloadUrl // "null"')
        
        if [ "$DOWNLOAD_URL" != "null" ] && [ -n "$DOWNLOAD_URL" ]; then
            echo
            print_success "Video published successfully!"
            print_info "Asset ID: $ASSET_ID"
            print_info "Bucket: $BUCKET"
            print_info "Object Key: $OBJECT_KEY"
            echo
            print_success "R2 Download URL:"
            echo "$DOWNLOAD_URL"
            
            # Test download URL
            echo
            print_step "Testing R2 download URL..."
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X HEAD "$DOWNLOAD_URL")
            if [ "$HTTP_CODE" = "200" ]; then
                print_success "R2 URL is working (HTTP $HTTP_CODE)"
            else
                print_error "R2 URL returned HTTP $HTTP_CODE"
            fi
        fi
    fi
else
    print_error "Request failed"
fi