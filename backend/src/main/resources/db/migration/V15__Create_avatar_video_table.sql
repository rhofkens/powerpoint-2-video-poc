-- Create avatar_videos table for tracking avatar video generation jobs
CREATE TABLE avatar_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL,
    slide_id UUID NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    provider_video_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    avatar_id VARCHAR(255),
    background_color VARCHAR(50),
    audio_url TEXT,
    video_url TEXT,
    r2_asset_id UUID,
    duration_seconds DOUBLE PRECISION,
    progress_percentage INTEGER,
    error_message TEXT,
    request_payload TEXT,
    response_payload TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    
    CONSTRAINT fk_avatar_video_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_avatar_video_slide 
        FOREIGN KEY (slide_id) 
        REFERENCES slides(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_avatar_video_asset 
        FOREIGN KEY (r2_asset_id) 
        REFERENCES asset_metadata(id) 
        ON DELETE SET NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_avatar_videos_presentation ON avatar_videos(presentation_id);
CREATE INDEX idx_avatar_videos_slide ON avatar_videos(slide_id);
CREATE INDEX idx_avatar_videos_status ON avatar_videos(status);
CREATE INDEX idx_avatar_videos_provider ON avatar_videos(provider_type);
CREATE INDEX idx_avatar_videos_created ON avatar_videos(created_at);

-- Add comment on table
COMMENT ON TABLE avatar_videos IS 'Tracks avatar video generation jobs for presentation slides';