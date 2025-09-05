#!/bin/bash

# Upload local avatar videos to R2 storage
# This script checks if videos exist in R2 and uploads missing ones

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PRESENTATION_ID="0684b101-efa4-429e-acf7-6129098959e7"
STORAGE_BASE="./storage/presentations/${PRESENTATION_ID}/slides"
R2_BUCKET="video-assets"
R2_ENDPOINT="${CLOUDFLARE_R2_EU_ENDPOINT:-https://d843ed67180ad598c29bec798d317f6a.eu.r2.cloudflarestorage.com}"
API_BASE="http://localhost:8080/api"

# Parse command line arguments
OVERWRITE=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --overwrite)
      OVERWRITE=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --help)
      echo "Usage: $0 [--overwrite] [--dry-run]"
      echo "  --overwrite  Overwrite existing videos in R2"
      echo "  --dry-run    Show what would be uploaded without actually uploading"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

echo -e "${BLUE}=== Uploading Local Avatar Videos to R2 ===${NC}"
echo -e "${YELLOW}ℹ Presentation: ${PRESENTATION_ID}${NC}"
echo -e "${YELLOW}ℹ Overwrite mode: ${OVERWRITE}${NC}"
echo -e "${YELLOW}ℹ Dry run: ${DRY_RUN}${NC}"
echo ""

# Function to check if file exists in R2
check_r2_file_exists() {
  local object_key=$1
  
  # Use AWS CLI to check if object exists
  if aws s3api head-object \
    --bucket "${R2_BUCKET}" \
    --key "${object_key}" \
    --endpoint-url "${R2_ENDPOINT}" \
    --no-verify-ssl \
    >/dev/null 2>&1; then
    return 0
  else
    return 1
  fi
}

# Function to upload file to R2
upload_to_r2() {
  local local_path=$1
  local object_key=$2
  
  if [ "$DRY_RUN" = true ]; then
    echo -e "${BLUE}[DRY RUN] Would upload: ${local_path} → ${object_key}${NC}"
    return 0
  fi
  
  # Upload using AWS CLI
  if aws s3 cp "${local_path}" "s3://${R2_BUCKET}/${object_key}" \
    --endpoint-url "${R2_ENDPOINT}" \
    --no-verify-ssl \
    --content-type "video/mp4" \
    >/dev/null 2>&1; then
    return 0
  else
    return 1
  fi
}

# Get all local video files
echo -e "${BLUE}→ Finding local video files...${NC}"
VIDEO_COUNT=0
UPLOADED_COUNT=0
SKIPPED_COUNT=0
FAILED_COUNT=0

# Find all avatar video files
for slide_dir in ${STORAGE_BASE}/*/avatar_videos; do
  if [ ! -d "$slide_dir" ]; then
    continue
  fi
  
  SLIDE_ID=$(basename $(dirname "$slide_dir"))
  
  # Get all mp4 files and sort by modification time (most recent first)
  LATEST_VIDEO=""
  for video_file in $(ls -t ${slide_dir}/*.mp4 2>/dev/null); do
    if [ ! -f "$video_file" ]; then
      continue
    fi
    
    # Take only the most recent video for this slide
    if [ -z "$LATEST_VIDEO" ]; then
      LATEST_VIDEO="$video_file"
    else
      # Count as found but skip older versions
      VIDEO_COUNT=$((VIDEO_COUNT + 1))
      echo -e "${BLUE}  Skipping older version: $(basename "$video_file")${NC}"
    fi
  done
  
  # Process only the latest video
  if [ ! -z "$LATEST_VIDEO" ]; then
    video_file="$LATEST_VIDEO"
    VIDEO_COUNT=$((VIDEO_COUNT + 1))
    FILENAME=$(basename "$video_file")
    
    echo ""
    echo -e "${YELLOW}ℹ Processing most recent video for slide${NC}"
    
    # Get the expected R2 object key from database
    OBJECT_KEY=$(psql "$DATABASE_URL" -t -c "
      SELECT object_key 
      FROM asset_metadata 
      WHERE slide_id = '${SLIDE_ID}' 
        AND asset_type = 'SLIDE_AVATAR_VIDEO'
        AND object_key LIKE '%${FILENAME}%'
      LIMIT 1
    " 2>/dev/null | xargs)
    
    if [ -z "$OBJECT_KEY" ]; then
      # If no exact match, construct the object key
      TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.%NZ" | cut -c1-26)Z
      OBJECT_KEY="presentations/${PRESENTATION_ID}/slides/${SLIDE_ID}/slide_avatar_video/${TIMESTAMP}_${FILENAME}"
    fi
    
    echo ""
    echo -e "${YELLOW}ℹ Slide: ${SLIDE_ID}${NC}"
    echo -e "${YELLOW}ℹ File: ${FILENAME}${NC}"
    echo -e "${YELLOW}ℹ Size: $(du -h "$video_file" | cut -f1)${NC}"
    
    # Check if file exists in R2
    if check_r2_file_exists "$OBJECT_KEY"; then
      if [ "$OVERWRITE" = true ]; then
        echo -e "${YELLOW}⚠ File exists in R2, overwriting...${NC}"
      else
        echo -e "${BLUE}✓ File already exists in R2, skipping${NC}"
        SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
        continue
      fi
    else
      echo -e "${YELLOW}→ File not in R2, uploading...${NC}"
    fi
    
    # Upload the file
    if upload_to_r2 "$video_file" "$OBJECT_KEY"; then
      echo -e "${GREEN}✓ Successfully uploaded to R2${NC}"
      UPLOADED_COUNT=$((UPLOADED_COUNT + 1))
      
      # Update database if not dry run
      if [ "$DRY_RUN" = false ]; then
        # Check if asset_metadata exists, if not create it
        ASSET_ID=$(psql "$DATABASE_URL" -t -c "
          SELECT id FROM asset_metadata 
          WHERE slide_id = '${SLIDE_ID}' 
            AND asset_type = 'SLIDE_AVATAR_VIDEO'
            AND object_key = '${OBJECT_KEY}'
        " 2>/dev/null | xargs)
        
        if [ -z "$ASSET_ID" ]; then
          # Create new asset_metadata record
          ASSET_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
          FILE_SIZE=$(stat -f%z "$video_file" 2>/dev/null || stat -c%s "$video_file" 2>/dev/null)
          
          psql "$DATABASE_URL" -c "
            INSERT INTO asset_metadata (
              id, presentation_id, slide_id, asset_type, 
              bucket_name, object_key, file_name, file_size, 
              content_type, upload_status, created_at, updated_at
            ) VALUES (
              '${ASSET_ID}', '${PRESENTATION_ID}', '${SLIDE_ID}', 'SLIDE_AVATAR_VIDEO',
              '${R2_BUCKET}', '${OBJECT_KEY}', '${FILENAME}', ${FILE_SIZE},
              'video/mp4', 'COMPLETED', NOW(), NOW()
            )
            ON CONFLICT DO NOTHING
          " >/dev/null 2>&1
          
          echo -e "${GREEN}✓ Created asset_metadata record${NC}"
        fi
        
        # Update avatar_videos to link to this asset
        psql "$DATABASE_URL" -c "
          UPDATE avatar_videos 
          SET r2_asset_id = '${ASSET_ID}'
          WHERE slide_id = '${SLIDE_ID}'
            AND r2_asset_id IS NULL
            AND status = 'COMPLETED'
        " >/dev/null 2>&1
      fi
    else
      echo -e "${RED}✗ Failed to upload to R2${NC}"
      FAILED_COUNT=$((FAILED_COUNT + 1))
    fi
  fi  # End of LATEST_VIDEO check
done

echo ""
echo -e "${BLUE}=== Summary ===${NC}"
echo -e "${YELLOW}ℹ Total videos found: ${VIDEO_COUNT}${NC}"
echo -e "${GREEN}✓ Uploaded: ${UPLOADED_COUNT}${NC}"
echo -e "${BLUE}✓ Skipped (already in R2): ${SKIPPED_COUNT}${NC}"
if [ $FAILED_COUNT -gt 0 ]; then
  echo -e "${RED}✗ Failed: ${FAILED_COUNT}${NC}"
fi

if [ "$DRY_RUN" = true ]; then
  echo ""
  echo -e "${YELLOW}This was a dry run. No files were actually uploaded.${NC}"
  echo -e "${YELLOW}Run without --dry-run to perform actual upload.${NC}"
fi

# Test one of the URLs if we uploaded anything
if [ $UPLOADED_COUNT -gt 0 ] && [ "$DRY_RUN" = false ]; then
  echo ""
  echo -e "${BLUE}→ Testing URL generation for uploaded videos...${NC}"
  
  # Pick a random slide to test
  TEST_SLIDE=$(psql "$DATABASE_URL" -t -c "
    SELECT DISTINCT slide_id 
    FROM avatar_videos 
    WHERE presentation_id = '${PRESENTATION_ID}' 
      AND r2_asset_id IS NOT NULL
    LIMIT 1
  " 2>/dev/null | xargs)
  
  if [ ! -z "$TEST_SLIDE" ]; then
    RESPONSE=$(curl -s -X GET "${API_BASE}/avatar-videos/slide/${TEST_SLIDE}")
    if echo "$RESPONSE" | grep -q "publishedUrl"; then
      echo -e "${GREEN}✓ API is generating presigned URLs successfully${NC}"
    else
      echo -e "${YELLOW}⚠ API response doesn't contain publishedUrl${NC}"
    fi
  fi
fi

echo ""
echo -e "${GREEN}Done!${NC}"