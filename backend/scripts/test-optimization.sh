#!/bin/bash

# Example script showing the narrative optimization workflow
# This demonstrates how to generate and optimize narratives

PRESENTATION_ID="0684b101-efa4-429e-acf7-6129098959e7"  # Replace with your presentation ID

echo "=== Narrative Generation and Optimization Test ==="
echo ""

# Step 1: Generate narratives (if not already done)
echo "Step 1: Generate narratives"
echo "curl -X POST http://localhost:8080/api/presentations/${PRESENTATION_ID}/generate-all-narratives \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"style\": \"business\"}'"
echo ""

# Step 2: Trigger optimization (without force)
echo "Step 2: Optimize narratives (first time)"
echo "./optimize-narratives.sh ${PRESENTATION_ID}"
echo ""

# Step 3: Force re-optimization
echo "Step 3: Force re-optimization (if needed)"
echo "./optimize-narratives.sh ${PRESENTATION_ID} force"
echo ""

# Step 4: Check the results
echo "Step 4: Get optimized narratives"
echo "curl http://localhost:8080/api/presentations/${PRESENTATION_ID}/complete-narrative"
echo ""

echo "=== Additional useful endpoints ==="
echo ""
echo "Check optimization status:"
echo "curl http://localhost:8080/api/presentations/${PRESENTATION_ID}/analysis-status"
echo ""
echo "Get specific slide narrative:"
echo "curl http://localhost:8080/api/slides/{slide-id}/narrative"