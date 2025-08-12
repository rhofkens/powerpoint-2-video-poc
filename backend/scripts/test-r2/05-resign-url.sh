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

echo "=== Testing Re-sign URL ==="
echo
print_info "Asset ID: $ASSET_ID"
echo

print_info "Getting current asset details..."
CURRENT_RESPONSE=$(curl -s -X GET "$BASE_URL/api/assets/$ASSET_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    OLD_URL=$(echo "$CURRENT_RESPONSE" | jq -r '.downloadUrl')
    OLD_EXPIRES=$(echo "$CURRENT_RESPONSE" | jq -r '.downloadUrlExpiresAt')
    
    if [ "$OLD_URL" != "null" ]; then
        print_info "Current URL expires at: $OLD_EXPIRES"
        echo
    fi
fi

print_info "Requesting new pre-signed URL..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/assets/$ASSET_ID/resign" \
  -H "Content-Type: application/json")

if [ $? -eq 0 ]; then
    print_success "URL re-signed successfully"
    echo
    echo "New URL details:"
    echo "$RESPONSE" | jq '.'
    
    NEW_URL=$(echo "$RESPONSE" | jq -r '.presignedUrl')
    NEW_EXPIRES=$(echo "$RESPONSE" | jq -r '.expiresAt')
    
    if [ "$NEW_URL" != "null" ] && [ -n "$NEW_URL" ]; then
        echo
        print_success "New URL generated"
        print_info "Expires at: $NEW_EXPIRES"
        
        # Test new URL
        echo
        print_info "Testing new download URL..."
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$NEW_URL")
        if [ "$HTTP_CODE" = "200" ]; then
            print_success "New URL is working (HTTP $HTTP_CODE)"
        else
            print_error "New URL returned HTTP $HTTP_CODE"
        fi
    fi
else
    print_error "Failed to re-sign URL"
fi