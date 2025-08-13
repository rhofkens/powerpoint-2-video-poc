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

echo "=== Checking Avatar Video Status ==="
echo
print_info "Video ID: $VIDEO_ID"
echo

# Check status
RESPONSE=$(curl -s -X GET "$BASE_URL/api/avatar-videos/$VIDEO_ID/status" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to get status"
        echo "$RESPONSE" | jq '.'
    else
        STATUS=$(echo "$RESPONSE" | jq -r '.status')
        PROGRESS=$(echo "$RESPONSE" | jq -r '.progress // 0')
        VIDEO_URL=$(echo "$RESPONSE" | jq -r '.videoUrl // "null"')
        ERROR_MSG=$(echo "$RESPONSE" | jq -r '.errorMessage // "null"')
        
        echo "Status Details:"
        echo "$RESPONSE" | jq '.'
        echo
        
        case "$STATUS" in
            "PENDING")
                print_info "Status: PENDING - Video generation queued"
                ;;
            "PROCESSING")
                print_info "Status: PROCESSING - Video is being generated"
                print_info "Progress: ${PROGRESS}%"
                ;;
            "COMPLETED")
                print_success "Status: COMPLETED - Video generation finished!"
                if [ "$VIDEO_URL" != "null" ] && [ -n "$VIDEO_URL" ]; then
                    echo
                    print_success "Video URL: $VIDEO_URL"
                    
                    # Test if URL is accessible
                    echo
                    print_step "Testing video URL accessibility..."
                    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X HEAD "$VIDEO_URL")
                    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
                        print_success "Video URL is accessible (HTTP $HTTP_CODE)"
                    else
                        print_error "Video URL returned HTTP $HTTP_CODE"
                    fi
                fi
                ;;
            "FAILED")
                print_error "Status: FAILED - Video generation failed"
                if [ "$ERROR_MSG" != "null" ]; then
                    print_error "Error: $ERROR_MSG"
                fi
                ;;
            "CANCELLED")
                print_info "Status: CANCELLED - Video generation was cancelled"
                ;;
            *)
                print_info "Status: $STATUS"
                ;;
        esac
    fi
else
    print_error "Request failed"
fi