#!/bin/bash

# Upload local avatar videos to R2 via the backend API
# This script uploads the most recent video for each slide

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
API_BASE="http://localhost:8080/api"

echo -e "${BLUE}=== Uploading Local Avatar Videos to R2 via API ===${NC}"
echo -e "${YELLOW}ℹ Presentation: ${PRESENTATION_ID}${NC}"
echo ""

# Get list of slides with avatar videos
echo -e "${BLUE}→ Finding slides with local video files...${NC}"

SLIDES_WITH_VIDEOS=""
for slide_dir in ${STORAGE_BASE}/*/avatar_videos; do
  if [ -d "$slide_dir" ]; then
    SLIDE_ID=$(basename $(dirname "$slide_dir"))
    # Check if there are any mp4 files
    if ls ${slide_dir}/*.mp4 >/dev/null 2>&1; then
      SLIDES_WITH_VIDEOS="${SLIDES_WITH_VIDEOS} ${SLIDE_ID}"
    fi
  fi
done

if [ -z "$SLIDES_WITH_VIDEOS" ]; then
  echo -e "${RED}No video files found${NC}"
  exit 1
fi

echo -e "${GREEN}✓ Found videos for slides:${NC}"
for slide in $SLIDES_WITH_VIDEOS; do
  echo "  - $slide"
done
echo ""

# Process each slide
UPLOADED_COUNT=0
FAILED_COUNT=0

for SLIDE_ID in $SLIDES_WITH_VIDEOS; do
  echo -e "${BLUE}Processing slide: ${SLIDE_ID}${NC}"
  
  # Find the most recent video file for this slide
  VIDEO_DIR="${STORAGE_BASE}/${SLIDE_ID}/avatar_videos"
  LATEST_VIDEO=$(ls -t ${VIDEO_DIR}/*.mp4 2>/dev/null | head -1)
  
  if [ -z "$LATEST_VIDEO" ]; then
    echo -e "${YELLOW}  No video files found${NC}"
    continue
  fi
  
  FILENAME=$(basename "$LATEST_VIDEO")
  FILE_SIZE=$(du -h "$LATEST_VIDEO" | cut -f1)
  
  echo -e "${YELLOW}  Latest video: ${FILENAME}${NC}"
  echo -e "${YELLOW}  Size: ${FILE_SIZE}${NC}"
  
  # Check if there are multiple videos (show which ones we're skipping)
  VIDEO_COUNT=$(ls ${VIDEO_DIR}/*.mp4 2>/dev/null | wc -l | tr -d ' ')
  if [ "$VIDEO_COUNT" -gt 1 ]; then
    echo -e "${BLUE}  Note: Found ${VIDEO_COUNT} videos, using most recent${NC}"
    for old_video in $(ls -t ${VIDEO_DIR}/*.mp4 2>/dev/null | tail -n +2); do
      echo -e "${BLUE}    Skipping: $(basename "$old_video")${NC}"
    done
  fi
  
  # Try to publish via API (this will handle upload if needed)
  echo -e "${YELLOW}  → Publishing to R2...${NC}"
  
  RESPONSE=$(curl -s -X POST \
    "${API_BASE}/r2/presentations/${PRESENTATION_ID}/slides/${SLIDE_ID}/assets/SLIDE_AVATAR_VIDEO/publish" \
    2>/dev/null)
  
  if echo "$RESPONSE" | grep -q '"success":true'; then
    echo -e "${GREEN}  ✓ Successfully published to R2${NC}"
    
    # Extract the download URL if available
    DOWNLOAD_URL=$(echo "$RESPONSE" | grep -o '"downloadUrl":"[^"]*' | cut -d'"' -f4)
    if [ ! -z "$DOWNLOAD_URL" ]; then
      # Test if the URL works
      HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -I "$DOWNLOAD_URL" 2>/dev/null)
      if [ "$HTTP_STATUS" = "200" ]; then
        echo -e "${GREEN}  ✓ Video is accessible (HTTP 200)${NC}"
      else
        echo -e "${YELLOW}  ⚠ Video returned HTTP ${HTTP_STATUS}${NC}"
      fi
    fi
    
    UPLOADED_COUNT=$((UPLOADED_COUNT + 1))
  else
    # Check if it's already published
    if echo "$RESPONSE" | grep -q "already exists"; then
      echo -e "${BLUE}  ✓ Already published to R2${NC}"
      
      # Force refresh the URL
      echo -e "${YELLOW}  → Getting fresh URL...${NC}"
      VIDEO_RESPONSE=$(curl -s -X GET "${API_BASE}/avatar-videos/slide/${SLIDE_ID}" 2>/dev/null)
      
      if echo "$VIDEO_RESPONSE" | grep -q "publishedUrl"; then
        echo -e "${GREEN}  ✓ Fresh URL generated${NC}"
      fi
      
      UPLOADED_COUNT=$((UPLOADED_COUNT + 1))
    else
      echo -e "${RED}  ✗ Failed to publish${NC}"
      ERROR_MSG=$(echo "$RESPONSE" | grep -o '"description":"[^"]*' | cut -d'"' -f4)
      if [ ! -z "$ERROR_MSG" ]; then
        echo -e "${RED}    Error: ${ERROR_MSG}${NC}"
      fi
      FAILED_COUNT=$((FAILED_COUNT + 1))
    fi
  fi
  
  echo ""
done

echo -e "${BLUE}=== Summary ===${NC}"
echo -e "${GREEN}✓ Successfully processed: ${UPLOADED_COUNT} slides${NC}"
if [ $FAILED_COUNT -gt 0 ]; then
  echo -e "${RED}✗ Failed: ${FAILED_COUNT} slides${NC}"
fi

# Test API endpoints
echo ""
echo -e "${BLUE}→ Testing API endpoints...${NC}"

# Pick a random slide to test
TEST_SLIDE=$(echo $SLIDES_WITH_VIDEOS | tr ' ' '\n' | head -1)
if [ ! -z "$TEST_SLIDE" ]; then
  echo -e "${YELLOW}  Testing slide: ${TEST_SLIDE}${NC}"
  
  RESPONSE=$(curl -s -X GET "${API_BASE}/avatar-videos/slide/${TEST_SLIDE}" 2>/dev/null)
  
  if echo "$RESPONSE" | grep -q '"publishedUrl"'; then
    # Extract the date from the URL
    URL_DATE=$(echo "$RESPONSE" | grep -o 'X-Amz-Date=[^&]*' | cut -d'=' -f2)
    if [ ! -z "$URL_DATE" ]; then
      echo -e "${GREEN}  ✓ API is generating fresh presigned URLs${NC}"
      echo -e "${GREEN}    URL Date: ${URL_DATE}${NC}"
    fi
  else
    echo -e "${YELLOW}  ⚠ No publishedUrl in response${NC}"
  fi
fi

echo ""
echo -e "${GREEN}Done!${NC}"
echo -e "${YELLOW}You can now test the videos in the frontend.${NC}"