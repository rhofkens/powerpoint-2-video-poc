-- V21: Create Video Story Service tables for video composition and rendering
-- This migration adds support for Shotstack-based video composition

-- Create ENUM types for status tracking
CREATE TYPE video_story_status AS ENUM ('DRAFT', 'READY', 'RENDERING', 'COMPLETED', 'FAILED');
CREATE TYPE render_job_status AS ENUM ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED');
CREATE TYPE render_job_type AS ENUM ('INTRO_ONLY', 'FULL_PRESENTATION');
CREATE TYPE publish_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED', 'EXPIRED', 'DELETED');
CREATE TYPE video_provider_type AS ENUM ('SHOTSTACK', 'REMOTION', 'CUSTOM');

-- Story Templates table
CREATE TABLE story_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    category VARCHAR(100),
    template_data JSONB NOT NULL,
    thumbnail_url TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Video Stories table (main composition tracking)
CREATE TABLE video_stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    template_id UUID REFERENCES story_templates(id),
    script_data JSONB NOT NULL, -- Stores the complete composition JSON
    duration_seconds NUMERIC(10,2),
    status video_story_status NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT fk_video_story_presentation FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) ON DELETE CASCADE,
    CONSTRAINT fk_video_story_template FOREIGN KEY (template_id)
        REFERENCES story_templates(id) ON DELETE SET NULL
);

-- Render Jobs table (tracks rendering operations)
CREATE TABLE render_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id) ON DELETE CASCADE,
    video_story_id UUID REFERENCES video_stories(id) ON DELETE CASCADE,
    type render_job_type NOT NULL DEFAULT 'INTRO_ONLY',
    provider video_provider_type NOT NULL DEFAULT 'SHOTSTACK',
    provider_job_id VARCHAR(255) UNIQUE,
    status render_job_status NOT NULL DEFAULT 'QUEUED',
    progress INTEGER DEFAULT 0 CHECK (progress >= 0 AND progress <= 100),
    output_url TEXT,
    output_metadata JSONB,
    composition_data JSONB,
    error_message TEXT,
    webhook_url TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_render_job_presentation FOREIGN KEY (presentation_id)
        REFERENCES presentations(id) ON DELETE CASCADE,
    CONSTRAINT fk_render_job_story FOREIGN KEY (video_story_id) 
        REFERENCES video_stories(id) ON DELETE CASCADE
);

-- Published Assets table (tracks assets uploaded to video service providers)
CREATE TABLE published_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_metadata_id UUID NOT NULL REFERENCES asset_metadata(id) ON DELETE CASCADE,
    video_story_id UUID REFERENCES video_stories(id) ON DELETE CASCADE,
    presentation_id UUID NOT NULL REFERENCES presentations(id) ON DELETE CASCADE,
    provider video_provider_type NOT NULL,
    provider_asset_id VARCHAR(255),
    provider_url TEXT NOT NULL,
    publish_status publish_status NOT NULL DEFAULT 'PENDING',
    metadata JSONB,
    published_at TIMESTAMP,
    expires_at TIMESTAMP,
    scheduled_deletion_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_published_asset_metadata FOREIGN KEY (asset_metadata_id) 
        REFERENCES asset_metadata(id) ON DELETE CASCADE,
    CONSTRAINT fk_published_asset_story FOREIGN KEY (video_story_id)
        REFERENCES video_stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_published_asset_presentation FOREIGN KEY (presentation_id)
        REFERENCES presentations(id) ON DELETE CASCADE
);

-- Webhook Events table (tracks callbacks from video service providers)
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    render_job_id UUID REFERENCES render_jobs(id) ON DELETE CASCADE,
    provider video_provider_type NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_render_job FOREIGN KEY (render_job_id) 
        REFERENCES render_jobs(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_video_stories_presentation ON video_stories(presentation_id);
CREATE INDEX idx_video_stories_status ON video_stories(status);
CREATE INDEX idx_video_stories_template ON video_stories(template_id);

CREATE INDEX idx_render_jobs_presentation ON render_jobs(presentation_id);
CREATE INDEX idx_render_jobs_story ON render_jobs(video_story_id);
CREATE INDEX idx_render_jobs_status ON render_jobs(status);
CREATE INDEX idx_render_jobs_provider_id ON render_jobs(provider_job_id);
CREATE INDEX idx_render_jobs_type ON render_jobs(type);
CREATE INDEX idx_render_jobs_created_at ON render_jobs(created_at DESC);

CREATE INDEX idx_published_assets_metadata ON published_assets(asset_metadata_id);
CREATE INDEX idx_published_assets_story ON published_assets(video_story_id);
CREATE INDEX idx_published_assets_presentation ON published_assets(presentation_id);
CREATE INDEX idx_published_assets_provider ON published_assets(provider);
CREATE INDEX idx_published_assets_status ON published_assets(publish_status);
CREATE INDEX idx_published_assets_scheduled_deletion ON published_assets(scheduled_deletion_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_webhook_events_job ON webhook_events(render_job_id);
CREATE INDEX idx_webhook_events_processed ON webhook_events(processed);
CREATE INDEX idx_webhook_events_provider ON webhook_events(provider);
CREATE INDEX idx_webhook_events_created_at ON webhook_events(created_at DESC);

-- Add comments for documentation
COMMENT ON TABLE video_stories IS 'Stores video composition scripts and metadata for presentation videos';
COMMENT ON TABLE render_jobs IS 'Tracks video rendering jobs submitted to external providers';
COMMENT ON TABLE published_assets IS 'Tracks assets uploaded to video service providers with auto-deletion support';
COMMENT ON TABLE story_templates IS 'Reusable templates for video story compositions';
COMMENT ON TABLE webhook_events IS 'Logs webhook callbacks from video service providers';

COMMENT ON COLUMN video_stories.script_data IS 'Complete composition JSON for the video timeline';
COMMENT ON COLUMN render_jobs.composition_data IS 'Snapshot of composition used for this render';
COMMENT ON COLUMN published_assets.scheduled_deletion_at IS 'When this asset should be deleted for privacy/cost control';
COMMENT ON COLUMN published_assets.deleted_at IS 'When the asset was actually deleted from the provider';