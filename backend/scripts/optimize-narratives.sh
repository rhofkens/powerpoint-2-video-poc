#!/bin/bash

# Script to trigger narrative optimization for a presentation
# Usage: ./optimize-narratives.sh <presentation-id> [force]

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if presentation ID is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Presentation ID is required${NC}"
    echo "Usage: $0 <presentation-id> [force]"
    echo "Example: $0 0684b101-efa4-429e-acf7-6129098959e7"
    echo "Example with force: $0 0684b101-efa4-429e-acf7-6129098959e7 force"
    exit 1
fi

PRESENTATION_ID=$1
FORCE_PARAM=""

# Check if force parameter is provided
if [ "$2" = "force" ]; then
    FORCE_PARAM="?force=true"
    echo -e "${YELLOW}Force mode enabled - will re-optimize even if already optimized${NC}"
fi

# API endpoint
API_URL="http://localhost:8080/api/presentations/${PRESENTATION_ID}/optimize-narratives${FORCE_PARAM}"

echo -e "${GREEN}=== Narrative Optimization Script ===${NC}"
echo "Presentation ID: $PRESENTATION_ID"
echo "API URL: $API_URL"
echo ""

# Make the API call
echo -e "${YELLOW}Triggering narrative optimization...${NC}"
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  "$API_URL")

# Check if curl was successful
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Response:${NC}"
    echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
    
    # Check if the response indicates success
    if echo "$RESPONSE" | grep -q '"success":true'; then
        echo ""
        echo -e "${GREEN}✓ Optimization started successfully!${NC}"
        echo ""
        echo "The optimization process includes:"
        echo "  1. Transition optimization - Improves flow between slides"
        echo "  2. Emotional enhancement - Adds TTS engine-specific markers"
        echo ""
        echo "You can check the status at:"
        echo "  http://localhost:8080/api/presentations/${PRESENTATION_ID}/analysis-status"
        echo ""
        echo "Prompt logs will be saved in:"
        echo "  /backend/storage/presentations/${PRESENTATION_ID}/prompts/TransitionOptimizationService/"
        echo "  /backend/storage/presentations/${PRESENTATION_ID}/prompts/ElevenLabsEmotionalEnhancer/"
    else
        echo ""
        echo -e "${RED}✗ Optimization request failed${NC}"
        echo "Please check the response above for error details"
    fi
else
    echo -e "${RED}✗ Failed to connect to API${NC}"
    echo "Please ensure the backend server is running on port 8080"
    exit 1
fi