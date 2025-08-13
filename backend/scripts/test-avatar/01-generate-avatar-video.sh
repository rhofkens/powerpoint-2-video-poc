#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPT_DIR/test-config.env"

echo "=== Testing Avatar Video Generation ==="
echo
print_info "Generating avatar video for:"
print_info "  Presentation: $PRESENTATION_ID"
print_info "  Slide: $SLIDE_ID"
print_info "  Avatar: $AVATAR_ID"
print_info "  Provider: $PROVIDER_TYPE"
print_info "  Background: $BACKGROUND_COLOR"
echo

# Step 1: Generate avatar video
print_step "Initiating avatar video generation..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/avatar-videos/generate" \
  -H "Content-Type: application/json" \
  -d "{
    \"presentationId\": \"$PRESENTATION_ID\",
    \"slideId\": \"$SLIDE_ID\",
    \"avatarId\": \"$AVATAR_ID\",
    \"backgroundColor\": \"$BACKGROUND_COLOR\",
    \"providerType\": \"$PROVIDER_TYPE\"
  }")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to initiate avatar video generation"
        echo "$RESPONSE" | jq '.'
        exit 1
    else
        print_success "Avatar video generation initiated"
        echo
        echo "Response:"
        echo "$RESPONSE" | jq '.'
        
        # Extract video ID
        VIDEO_ID=$(echo "$RESPONSE" | jq -r '.id')
        STATUS=$(echo "$RESPONSE" | jq -r '.status')
        
        if [ "$VIDEO_ID" != "null" ] && [ -n "$VIDEO_ID" ]; then
            echo
            print_info "Video ID: $VIDEO_ID"
            print_info "Initial Status: $STATUS"
            
            # Save video ID for status check script
            echo "$VIDEO_ID" > "$SCRIPT_DIR/.last_video_id"
            
            echo
            print_success "Video generation job created successfully"
            print_info "Use 02-check-status.sh to check progress"
            print_info "Video ID saved to .last_video_id for convenience"
        else
            print_error "No video ID returned"
        fi
    fi
else
    print_error "Request failed"
fi