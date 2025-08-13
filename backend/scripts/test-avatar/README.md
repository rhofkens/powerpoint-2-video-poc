# Avatar Video Generation Test Scripts

This directory contains test scripts for the avatar video generation service using HeyGen API.

## Prerequisites

1. Ensure the backend is running on `http://localhost:8080`
2. Set the HeyGen API key environment variable: `export HEYGEN_API_KEY=your_key_here`
3. Make sure audio has been published to R2 for the test slide (use R2 test scripts first)

## Scripts

### 01-generate-avatar-video.sh
Initiates avatar video generation for a slide using HeyGen.
- Uses Brandon_expressive2_public avatar
- Sets beige background for chroma key
- Automatically uses published audio from R2

```bash
./01-generate-avatar-video.sh
```

### 02-check-status.sh
Checks the status of a video generation job.

```bash
# Use last generated video ID
./02-check-status.sh

# Or specify video ID
./02-check-status.sh <video_id>
```

### 03-poll-until-complete.sh
Polls the status until video generation is complete (max 5 minutes).

```bash
# Use last generated video ID
./03-poll-until-complete.sh

# Or specify video ID
./03-poll-until-complete.sh <video_id>
```

### 04-list-slide-videos.sh
Lists all avatar videos for a slide.

```bash
# Use default slide ID from config
./04-list-slide-videos.sh

# Or specify slide ID
./04-list-slide-videos.sh <slide_id>
```

### 05-publish-to-r2.sh
Publishes a completed avatar video to R2 storage.

```bash
# Use last generated video ID
./05-publish-to-r2.sh

# Or specify video ID
./05-publish-to-r2.sh <video_id>
```

## Typical Workflow

1. First, ensure audio is published to R2:
   ```bash
   cd ../test-r2
   ./08-publish-slide-audio.sh
   cd ../test-avatar
   ```

2. Generate avatar video:
   ```bash
   ./01-generate-avatar-video.sh
   ```

3. Poll until complete:
   ```bash
   ./03-poll-until-complete.sh
   ```

4. Optionally publish to R2:
   ```bash
   ./05-publish-to-r2.sh
   ```

## Configuration

Edit `test-config.env` to change:
- Presentation and slide IDs
- Avatar ID (default: Brandon_expressive2_public)
- Background color (default: #F5DEB3 - beige for chroma key)
- Provider type (default: HEYGEN)

## Notes

- Video generation typically takes 1-3 minutes
- The scripts save the last video ID to `.last_video_id` for convenience
- Generated videos are stored by HeyGen and accessible via their URLs
- Publishing to R2 creates a local copy for consistent storage