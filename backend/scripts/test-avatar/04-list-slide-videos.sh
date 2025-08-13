#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPT_DIR/test-config.env"

# Use slide ID from argument or default
SLIDE_ID="${1:-$SLIDE_ID}"

echo "=== Listing Avatar Videos for Slide ==="
echo
print_info "Slide ID: $SLIDE_ID"
echo

# List videos for slide
RESPONSE=$(curl -s -X GET "$BASE_URL/api/avatar-videos/slide/$SLIDE_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to list avatar videos"
        echo "$RESPONSE" | jq '.'
    else
        # Check if array is empty
        VIDEO_COUNT=$(echo "$RESPONSE" | jq '. | length')
        
        if [ "$VIDEO_COUNT" -eq 0 ]; then
            print_info "No avatar videos found for this slide"
        else
            print_success "Found $VIDEO_COUNT avatar video(s)"
            echo
            
            # Display each video
            echo "$RESPONSE" | jq -c '.[]' | while read -r video; do
                VIDEO_ID=$(echo "$video" | jq -r '.id')
                STATUS=$(echo "$video" | jq -r '.status')
                PROVIDER=$(echo "$video" | jq -r '.providerType')
                CREATED=$(echo "$video" | jq -r '.createdAt')
                VIDEO_URL=$(echo "$video" | jq -r '.videoUrl // "null"')
                
                echo "----------------------------------------"
                print_info "Video ID: $VIDEO_ID"
                echo "  Provider: $PROVIDER"
                echo "  Status: $STATUS"
                echo "  Created: $CREATED"
                
                if [ "$VIDEO_URL" != "null" ] && [ -n "$VIDEO_URL" ]; then
                    echo "  Video URL: $VIDEO_URL"
                fi
                echo
            done
            
            echo "----------------------------------------"
            echo
            echo "Full Response:"
            echo "$RESPONSE" | jq '.'
        fi
    fi
else
    print_error "Request failed"
fi