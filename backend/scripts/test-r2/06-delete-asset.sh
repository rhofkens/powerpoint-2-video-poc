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

echo "=== Testing Delete Asset ==="
echo
print_info "Asset ID: $ASSET_ID"
echo

# First, get asset details before deletion
print_info "Getting asset details before deletion..."
BEFORE_RESPONSE=$(curl -s -X GET "$BASE_URL/api/assets/$ASSET_ID" \
  -H "Accept: application/json")

if [ $? -eq 0 ]; then
    echo "$BEFORE_RESPONSE" | jq '{
        id: .id,
        fileName: .fileName,
        bucketName: .bucketName,
        objectKey: .objectKey
    }'
    echo
fi

# Confirm deletion
echo -n "Are you sure you want to delete this asset? (y/N): "
read -r CONFIRM

if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    print_info "Deletion cancelled"
    exit 0
fi

echo
print_info "Deleting asset..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/assets/$ASSET_ID" \
  -H "Accept: application/json")

if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
    print_success "Asset deleted successfully (HTTP $HTTP_CODE)"
    
    # Verify deletion
    echo
    print_info "Verifying deletion..."
    VERIFY_RESPONSE=$(curl -s -X GET "$BASE_URL/api/assets/$ASSET_ID" \
      -H "Accept: application/json")
    
    # Check if response contains error or not found
    if echo "$VERIFY_RESPONSE" | grep -q "not found\|404\|error"; then
        print_success "Deletion verified - asset no longer exists"
    else
        print_error "Asset may still exist after deletion"
        echo "$VERIFY_RESPONSE" | jq '.'
    fi
    
    # Clean up last asset file
    if [ -f "test-data/last-asset.env" ]; then
        rm test-data/last-asset.env
        print_info "Cleared last asset reference"
    fi
else
    print_error "Failed to delete asset (HTTP $HTTP_CODE)"
fi