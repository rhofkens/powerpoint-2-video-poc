-- Add fields for tracking published avatar videos
ALTER TABLE avatar_videos
ADD COLUMN published_url VARCHAR(2048),
ADD COLUMN published_at TIMESTAMP;

-- Create index for finding unpublished completed videos
CREATE INDEX idx_avatar_videos_unpublished 
ON avatar_videos(status) 
WHERE status = 'COMPLETED' AND published_url IS NULL;