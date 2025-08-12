#!/bin/bash

source test-data/test-config.env

# Check if asset ID is provided as argument or use last asset
if [ -n "$1" ]; then
    ASSET_ID=$1
elif [ -f "test-data/last-asset.env" ]; then
    source test-data/last-asset.env
    ASSET_ID=$LAST_ASSET_ID
else
    print_error "No asset ID provided and no last asset found"
    echo "Usage: $0 <asset-id>"
    exit 1
fi

echo "=== Testing Get Asset ==="
echo
print_info "Asset ID: $ASSET_ID"
echo

RESPONSE=$(curl -s -X GET "$BASE_URL/api/assets/$ASSET_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    print_success "Asset retrieved successfully"
    echo
    echo "Asset details:"
    echo "$RESPONSE" | jq '.'
    
    # Extract download URL
    DOWNLOAD_URL=$(echo "$RESPONSE" | jq -r '.downloadUrl')
    if [ "$DOWNLOAD_URL" != "null" ] && [ -n "$DOWNLOAD_URL" ]; then
        echo
        print_success "Download URL available"
        print_info "URL: $DOWNLOAD_URL"
        
        # Test download URL
        echo
        print_info "Testing download URL..."
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$DOWNLOAD_URL")
        if [ "$HTTP_CODE" = "200" ]; then
            print_success "Download URL is working (HTTP $HTTP_CODE)"
        else
            print_error "Download URL returned HTTP $HTTP_CODE"
        fi
    else
        print_info "No download URL available (asset may still be uploading)"
    fi
else
    print_error "Failed to get asset"
fi