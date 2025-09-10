-- Add fields for storing rendered video information in video_stories table

ALTER TABLE video_stories 
    ADD COLUMN IF NOT EXISTS r2_video_url TEXT,
    ADD COLUMN IF NOT EXISTS video_file_size BIGINT,
    ADD COLUMN IF NOT EXISTS video_duration_seconds DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS downloaded_at TIMESTAMP;

-- Add index for quick lookup of videos needing download
CREATE INDEX IF NOT EXISTS idx_video_stories_needs_download 
    ON video_stories(status) 
    WHERE status = 'COMPLETED' AND r2_video_url IS NULL;

-- Add comment for documentation
COMMENT ON COLUMN video_stories.r2_video_url IS 'URL of the rendered video stored in R2 storage';
COMMENT ON COLUMN video_stories.video_file_size IS 'Size of the video file in bytes';
COMMENT ON COLUMN video_stories.video_duration_seconds IS 'Duration of the video in seconds';
COMMENT ON COLUMN video_stories.downloaded_at IS 'Timestamp when the video was downloaded and stored in R2';