# R2 Asset Service Test Scripts

This directory contains manual test scripts for the Cloudflare R2 Asset Publishing Service.

## Prerequisites

1. **Backend server must be running**:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **Required tools**:
   - `curl` - for making HTTP requests
   - `jq` - for JSON parsing
   - `bash` - for running scripts

   Install on macOS:
   ```bash
   brew install jq
   ```

3. **Database with test data**:
   You need at least one presentation and slide in the database.

4. **R2 credentials configured**:
   Set the following environment variables before starting the backend:
   ```bash
   export CLOUDFLARE_R2_ACCESS_KEY_ID=your_access_key
   export CLOUDFLARE_R2_SECRET_ACCESS_KEY=your_secret_key
   export CLOUDFLARE_R2_EU_ENDPOINT=https://your-account.r2.cloudflarestorage.com
   ```

## Configuration

1. Edit `test-data/test-config.env` and set valid IDs:
   ```bash
   PRESENTATION_ID="valid-presentation-uuid"
   SLIDE_ID="valid-slide-uuid"
   ```

2. Get valid IDs from your database:
   ```sql
   -- Get presentation IDs
   psql -d ppt2video_poc -c "SELECT id, title FROM presentations LIMIT 5;"
   
   -- Get slide IDs for a presentation
   psql -d ppt2video_poc -c "SELECT id, slide_number FROM slides WHERE presentation_id = 'your-presentation-id' LIMIT 5;"
   ```

## Running Tests

### Run all tests:
```bash
./run-all-tests.sh
```

### Run individual tests:

1. **Upload an asset**:
   ```bash
   ./01-upload-asset.sh
   ```

2. **Get asset details**:
   ```bash
   ./02-get-asset.sh [asset-id]
   ```

3. **List presentation assets**:
   ```bash
   ./03-list-presentation-assets.sh [presentation-id]
   ```

4. **List slide assets**:
   ```bash
   ./04-list-slide-assets.sh [slide-id]
   ```

5. **Re-sign expired URL**:
   ```bash
   ./05-resign-url.sh [asset-id]
   ```

6. **Delete asset**:
   ```bash
   ./06-delete-asset.sh [asset-id]
   ```

## Test Flow

1. **Upload Test** - Creates a new asset and gets a pre-signed upload URL
2. **Get Test** - Retrieves the asset metadata and download URL
3. **List Tests** - Lists assets for presentation and slide
4. **Re-sign Test** - Generates a new pre-signed URL
5. **Delete Test** - Removes the asset from R2 and database

## Troubleshooting

### Server not running
```
Error: Server is not running at http://localhost:8080
```
Solution: Start the backend server with `mvn spring-boot:run`

### Invalid IDs
```
Error: Please configure a valid PRESENTATION_ID
```
Solution: Update `test-data/test-config.env` with valid IDs from your database

### R2 upload fails
```
Error: Failed to upload file (HTTP 403)
```
Solution: Check your R2 credentials and bucket permissions

### Missing jq
```
Error: jq is not installed
```
Solution: Install jq with `brew install jq` (macOS) or `apt-get install jq` (Linux)

## Sample Output

Successful test run should show:
```
✓ Server is running
✓ Asset ID: 123e4567-e89b-12d3-a456-426614174000
✓ File uploaded successfully (HTTP 200)
✓ Upload confirmed successfully
✓ All tests completed successfully!
```

## Notes

- The test scripts create a small sample PNG image automatically
- Asset IDs are saved to `test-data/last-asset.env` for subsequent tests
- All scripts support colored output for better readability
- The delete test requires confirmation to prevent accidental deletion