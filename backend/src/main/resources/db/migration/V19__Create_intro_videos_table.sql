-- Create intro_videos table for tracking Veo-generated intro videos
CREATE TABLE intro_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL,
    veo_generation_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    prompt_text TEXT,
    negative_prompt TEXT,
    color_palette JSONB,
    veo_model_version VARCHAR(50),
    aspect_ratio VARCHAR(20),
    resolution VARCHAR(20),
    duration_seconds DOUBLE PRECISION,
    google_video_url TEXT,
    published_url TEXT,
    r2_asset_id UUID,
    generation_metadata JSONB,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_intro_video_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_intro_video_asset 
        FOREIGN KEY (r2_asset_id) 
        REFERENCES asset_metadata(id) 
        ON DELETE SET NULL
);

-- Create indexes for performance
CREATE INDEX idx_intro_videos_presentation ON intro_videos(presentation_id);
CREATE INDEX idx_intro_videos_status ON intro_videos(status);
CREATE INDEX idx_intro_videos_created ON intro_videos(created_at);
CREATE INDEX idx_intro_videos_veo_generation_id ON intro_videos(veo_generation_id);

-- Add comment
COMMENT ON TABLE intro_videos IS 'Tracks AI-generated intro videos using Google Veo API';
COMMENT ON COLUMN intro_videos.veo_generation_id IS 'External ID from Google Veo API';
COMMENT ON COLUMN intro_videos.color_palette IS 'JSONB containing extracted dominant colors from first slide';
COMMENT ON COLUMN intro_videos.generation_metadata IS 'JSONB containing additional generation parameters and metadata';
COMMENT ON COLUMN intro_videos.google_video_url IS 'Temporary URL from Google (expires after 2 days)';
COMMENT ON COLUMN intro_videos.published_url IS 'Permanent URL from R2 storage';