#!/bin/bash

source test-data/test-config.env

# Check if slide ID is provided as argument or use config
if [ -n "$1" ]; then
    SLIDE_ID=$1
fi

echo "=== Testing List Slide Assets ==="
echo
print_info "Slide ID: $SLIDE_ID"
echo

RESPONSE=$(curl -s -X GET "$BASE_URL/api/assets/slides/$SLIDE_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    # Check if response is an array
    if echo "$RESPONSE" | jq -e 'type == "array"' > /dev/null 2>&1; then
        COUNT=$(echo "$RESPONSE" | jq '. | length')
        print_success "Retrieved $COUNT assets for slide"
        echo
        
        if [ "$COUNT" -gt 0 ]; then
            echo "Assets:"
            echo "$RESPONSE" | jq '.[] | {
                id: .id,
                assetType: .assetType,
                fileName: .fileName,
                uploadStatus: .uploadStatus,
                fileSize: .fileSize,
                createdAt: .createdAt
            }'
        else
            print_info "No assets found for this slide"
        fi
    else
        print_error "Unexpected response format"
        echo "$RESPONSE" | jq '.'
    fi
else
    print_error "Failed to list slide assets"
fi