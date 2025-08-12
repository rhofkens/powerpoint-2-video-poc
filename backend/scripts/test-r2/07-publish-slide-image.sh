#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$SCRIPT_DIR/test-data/test-config.env"

echo "=== Testing Publish Slide Image ==="
echo
print_info "Publishing SLIDE_IMAGE for:"
print_info "  Presentation: $PRESENTATION_ID"
print_info "  Slide: $SLIDE_ID"
echo

# Call the publish endpoint
RESPONSE=$(curl -s -X POST "$BASE_URL/api/assets/publish/SLIDE_IMAGE?presentationId=$PRESENTATION_ID&slideId=$SLIDE_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response contains an error
    if echo "$RESPONSE" | grep -q '"error"\|"status":4\|"status":5'; then
        print_error "Failed to publish slide image"
        echo "$RESPONSE" | jq '.'
    else
        print_success "Slide image published successfully"
        echo
        echo "Asset details:"
        echo "$RESPONSE" | jq '.'
        
        # Extract download URL
        DOWNLOAD_URL=$(echo "$RESPONSE" | jq -r '.downloadUrl')
        FILE_SIZE=$(echo "$RESPONSE" | jq -r '.fileSize')
        
        if [ "$DOWNLOAD_URL" != "null" ] && [ -n "$DOWNLOAD_URL" ]; then
            echo
            print_success "Asset uploaded to R2"
            print_info "File size: $FILE_SIZE bytes"
            
            # Test download URL
            echo
            print_info "Testing download URL..."
            HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$DOWNLOAD_URL")
            if [ "$HTTP_CODE" = "200" ]; then
                print_success "Download URL is working (HTTP $HTTP_CODE)"
                
                # Download a small portion to verify it's an image
                print_info "Verifying file type..."
                CONTENT_TYPE=$(curl -s -I "$DOWNLOAD_URL" | grep -i "content-type" | cut -d' ' -f2 | tr -d '\r\n')
                if echo "$CONTENT_TYPE" | grep -q "image"; then
                    print_success "Confirmed: File is an image ($CONTENT_TYPE)"
                else
                    print_error "Unexpected content type: $CONTENT_TYPE"
                fi
            else
                print_error "Download URL returned HTTP $HTTP_CODE"
            fi
        fi
    fi
else
    print_error "Request failed"
fi