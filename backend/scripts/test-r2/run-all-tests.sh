#!/bin/bash

source test-data/test-config.env

echo "========================================="
echo "     R2 Asset Service Test Suite"
echo "========================================="
echo

# Check prerequisites
echo "Checking prerequisites..."
if ! command -v jq &> /dev/null; then
    print_error "jq is not installed. Please install it first:"
    echo "  brew install jq  # macOS"
    echo "  apt-get install jq  # Ubuntu/Debian"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    print_error "curl is not installed"
    exit 1
fi

# Check if server is running
print_info "Checking if server is running at $BASE_URL..."
if curl -s -f -o /dev/null "$BASE_URL/api/health" 2>/dev/null; then
    print_success "Server is running"
else
    print_error "Server is not running at $BASE_URL"
    echo "Please start the backend server first:"
    echo "  cd backend && mvn spring-boot:run"
    exit 1
fi

# Check configuration
if [ "$PRESENTATION_ID" = "YOUR_PRESENTATION_UUID_HERE" ]; then
    print_error "Please configure a valid PRESENTATION_ID in test-data/test-config.env"
    echo "You can get one by running:"
    echo "  psql -d ppt2video_poc -c \"SELECT id, title FROM presentations LIMIT 5;\""
    exit 1
fi

if [ "$SLIDE_ID" = "YOUR_SLIDE_UUID_HERE" ]; then
    print_error "Please configure a valid SLIDE_ID in test-data/test-config.env"
    echo "You can get one by running:"
    echo "  psql -d ppt2video_poc -c \"SELECT id, slide_number FROM slides WHERE presentation_id = '$PRESENTATION_ID' LIMIT 5;\""
    exit 1
fi

echo
echo "Configuration:"
print_info "Base URL: $BASE_URL"
print_info "Presentation ID: $PRESENTATION_ID"
print_info "Slide ID: $SLIDE_ID"
echo

# Run tests
TESTS_PASSED=0
TESTS_FAILED=0

run_test() {
    local test_script=$1
    local test_name=$2
    
    echo "----------------------------------------"
    echo "Running: $test_name"
    echo "----------------------------------------"
    
    if bash "$test_script"; then
        TESTS_PASSED=$((TESTS_PASSED + 1))
        print_success "$test_name completed"
    else
        TESTS_FAILED=$((TESTS_FAILED + 1))
        print_error "$test_name failed"
    fi
    echo
}

# Test 1: Upload Asset
run_test "./01-upload-asset.sh" "Upload Asset Test"

# Small delay between tests
sleep 1

# Test 2: Get Asset
if [ -f "test-data/last-asset.env" ]; then
    run_test "./02-get-asset.sh" "Get Asset Test"
else
    print_info "Skipping Get Asset test (no asset created)"
fi

# Test 3: List Presentation Assets
run_test "./03-list-presentation-assets.sh" "List Presentation Assets Test"

# Test 4: List Slide Assets
run_test "./04-list-slide-assets.sh" "List Slide Assets Test"

# Test 5: Re-sign URL
if [ -f "test-data/last-asset.env" ]; then
    run_test "./05-resign-url.sh" "Re-sign URL Test"
else
    print_info "Skipping Re-sign URL test (no asset created)"
fi

# Test 6: Delete Asset (optional)
if [ -f "test-data/last-asset.env" ]; then
    echo "----------------------------------------"
    echo "Optional: Delete Asset Test"
    echo "----------------------------------------"
    echo -n "Do you want to run the delete test? (y/N): "
    read -r RUN_DELETE
    
    if [ "$RUN_DELETE" = "y" ] || [ "$RUN_DELETE" = "Y" ]; then
        # Auto-confirm deletion for test suite
        echo "y" | bash ./06-delete-asset.sh
        if [ $? -eq 0 ]; then
            TESTS_PASSED=$((TESTS_PASSED + 1))
            print_success "Delete Asset Test completed"
        else
            TESTS_FAILED=$((TESTS_FAILED + 1))
            print_error "Delete Asset Test failed"
        fi
    else
        print_info "Skipping Delete Asset test"
    fi
    echo
fi

# Summary
echo "========================================="
echo "              Test Summary"
echo "========================================="
print_success "Tests Passed: $TESTS_PASSED"
if [ $TESTS_FAILED -gt 0 ]; then
    print_error "Tests Failed: $TESTS_FAILED"
else
    print_info "Tests Failed: $TESTS_FAILED"
fi
echo

if [ $TESTS_FAILED -eq 0 ]; then
    print_success "All tests completed successfully!"
else
    print_error "Some tests failed. Please check the output above."
    exit 1
fi