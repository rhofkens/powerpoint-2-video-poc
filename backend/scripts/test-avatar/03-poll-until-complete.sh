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

echo "=== Polling Avatar Video Status Until Complete ==="
echo
print_info "Video ID: $VIDEO_ID"
print_info "Polling interval: 5 seconds"
print_info "Max wait time: 5 minutes"
echo

# Configuration
POLL_INTERVAL=5  # seconds
MAX_WAIT=300     # 5 minutes
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
    # Check status
    RESPONSE=$(curl -s -X GET "$BASE_URL/api/avatar-videos/$VIDEO_ID/status" \
      -H "Accept: application/json")
    
    if [ $? -eq 0 ]; then
        STATUS=$(echo "$RESPONSE" | jq -r '.status // "UNKNOWN"')
        PROGRESS=$(echo "$RESPONSE" | jq -r '.progress // 0')
        
        # Clear line and print status
        printf "\r[%3d/%3d sec] Status: %-12s Progress: %3d%%" $ELAPSED $MAX_WAIT "$STATUS" "$PROGRESS"
        
        case "$STATUS" in
            "COMPLETED")
                echo
                echo
                print_success "Video generation completed!"
                echo
                echo "Final Response:"
                echo "$RESPONSE" | jq '.'
                
                VIDEO_URL=$(echo "$RESPONSE" | jq -r '.videoUrl // "null"')
                if [ "$VIDEO_URL" != "null" ] && [ -n "$VIDEO_URL" ]; then
                    echo
                    print_success "Video URL: $VIDEO_URL"
                    
                    # Save video URL
                    echo "$VIDEO_URL" > "$SCRIPT_DIR/.last_video_url"
                    print_info "Video URL saved to .last_video_url"
                fi
                exit 0
                ;;
                
            "FAILED")
                echo
                echo
                print_error "Video generation failed!"
                ERROR_MSG=$(echo "$RESPONSE" | jq -r '.errorMessage // "Unknown error"')
                print_error "Error: $ERROR_MSG"
                echo
                echo "Full Response:"
                echo "$RESPONSE" | jq '.'
                exit 1
                ;;
                
            "CANCELLED")
                echo
                echo
                print_info "Video generation was cancelled"
                exit 1
                ;;
        esac
    else
        printf "\r[%3d/%3d sec] Status check failed, retrying..." $ELAPSED $MAX_WAIT
    fi
    
    # Wait before next poll
    sleep $POLL_INTERVAL
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
done

echo
echo
print_error "Timeout reached after $MAX_WAIT seconds"
print_info "Video generation may still be in progress. Use 02-check-status.sh to check later."
exit 1