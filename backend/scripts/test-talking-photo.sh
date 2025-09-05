#!/bin/bash

# Test script to verify talking photo support
# This script simulates what happens when an avatar ID with TP- prefix is used

echo "=== Testing Talking Photo Support ==="
echo

# Test 1: Regular avatar (no TP- prefix)
echo "Test 1: Regular Avatar Request"
echo "Avatar ID: Brandon_expressive2_public"
echo "Expected: Should use regular avatar API format"
echo

curl -X POST http://localhost:8080/api/avatar-videos/test-payload \
  -H "Content-Type: application/json" \
  -d '{
    "avatarId": "Brandon_expressive2_public",
    "audioUrl": "https://example.com/test-audio.mp3"
  }' | jq '.'

echo
echo "----------------------------------------"
echo

# Test 2: Talking photo (with TP- prefix)
echo "Test 2: Talking Photo Request"
echo "Avatar ID: TP-bb450948c12f47d6b2742edc8ebc102e"
echo "Expected: Should use talking_photo API format with expressive settings"
echo

curl -X POST http://localhost:8080/api/avatar-videos/test-payload \
  -H "Content-Type: application/json" \
  -d '{
    "avatarId": "TP-bb450948c12f47d6b2742edc8ebc102e",
    "audioUrl": "https://example.com/test-audio.mp3"
  }' | jq '.'

echo
echo "=== End of Tests ==="