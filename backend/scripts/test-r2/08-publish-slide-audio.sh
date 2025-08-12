#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPT_DIR/test-data/test-config.env"

echo "=== Testing Publish Slide Audio ==="
echo
print_info "Publishing SLIDE_AUDIO for:"
print_info "  Presentation: $PRESENTATION_ID"
print_info "  Slide: $SLIDE_ID"
echo

# Call the publish endpoint for SLIDE_AUDIO
RESPONSE=$(curl -s -X POST "$BASE_URL/api/assets/publish/SLIDE_AUDIO?presentationId=$PRESENTATION_ID&slideId=$SLIDE_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to publish slide audio"
        echo "$RESPONSE" | jq '.'
    else
        print_success "Slide audio published successfully"
        echo
        echo "Asset details:"
        echo "$RESPONSE" | jq '.'
        
        # Extract download URL and file size
        DOWNLOAD_URL=$(echo "$RESPONSE" | jq -r '.downloadUrl')
        FILE_SIZE=$(echo "$RESPONSE" | jq -r '.fileSize')
        
        if [ "$DOWNLOAD_URL" != "null" ] && [ -n "$DOWNLOAD_URL" ]; then
            echo
            print_success "Audio asset uploaded to R2"
            print_info "File size: $FILE_SIZE bytes"
            
            # Test download URL
            echo
            print_info "Testing download URL..."
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$DOWNLOAD_URL")
            if [ "$HTTP_CODE" = "200" ]; then
                print_success "Download URL is working (HTTP $HTTP_CODE)"
                
                # Download a small portion to verify it's audio
                print_info "Verifying file type..."
                HEADER_CHECK=$(curl -s --range 0-10 "$DOWNLOAD_URL" | xxd -p | head -c 6)
                if [ "$HEADER_CHECK" = "494433" ]; then
                    print_success "Confirmed: File is an MP3 audio (ID3 header detected)"
                elif [ "$HEADER_CHECK" = "fffb" ] || [ "$HEADER_CHECK" = "fff3" ]; then
                    print_success "Confirmed: File is an MP3 audio (MPEG header detected)"
                else
                    print_info "File header: $HEADER_CHECK (may still be valid audio)"
                fi
            else
                print_error "Download URL returned HTTP $HTTP_CODE"
            fi
        fi
    fi
else
    print_error "Request failed"
fi