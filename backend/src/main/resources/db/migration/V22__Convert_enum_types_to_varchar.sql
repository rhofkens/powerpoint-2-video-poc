-- Convert PostgreSQL ENUM types to VARCHAR for consistency with rest of codebase

-- 1. Convert video_stories.status from video_story_status ENUM to VARCHAR
ALTER TABLE video_stories 
    ALTER COLUMN status TYPE VARCHAR(50) 
    USING status::text;

-- Add CHECK constraint for video_stories.status
ALTER TABLE video_stories 
    ADD CONSTRAINT video_stories_status_check 
    CHECK (status IN ('DRAFT', 'READY', 'RENDERING', 'COMPLETED', 'FAILED'));

-- 2. Convert render_jobs.status from render_job_status ENUM to VARCHAR
ALTER TABLE render_jobs 
    ALTER COLUMN status TYPE VARCHAR(50) 
    USING status::text;

-- Add CHECK constraint for render_jobs.status  
ALTER TABLE render_jobs
    ADD CONSTRAINT render_jobs_status_check
    CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- 3. Convert render_jobs.type from render_job_type ENUM to VARCHAR
ALTER TABLE render_jobs 
    ALTER COLUMN type TYPE VARCHAR(50) 
    USING type::text;

-- Add CHECK constraint for render_jobs.type
ALTER TABLE render_jobs
    ADD CONSTRAINT render_jobs_type_check
    CHECK (type IN ('INTRO_ONLY', 'FULL_PRESENTATION', 'CUSTOM'));

-- 4. Convert render_jobs.provider from video_provider_type ENUM to VARCHAR
ALTER TABLE render_jobs 
    ALTER COLUMN provider TYPE VARCHAR(50) 
    USING provider::text;

-- Add CHECK constraint for render_jobs.provider
ALTER TABLE render_jobs
    ADD CONSTRAINT render_jobs_provider_check
    CHECK (provider IN ('SHOTSTACK', 'REMOTION', 'CUSTOM'));

-- 5. Drop the custom ENUM types (they are no longer needed)
-- Use CASCADE to drop any dependent objects (like default values)
DROP TYPE IF EXISTS video_story_status CASCADE;
DROP TYPE IF EXISTS render_job_status CASCADE;
DROP TYPE IF EXISTS render_job_type CASCADE;
DROP TYPE IF EXISTS video_provider_type CASCADE;

-- Note: publish_status type is not used in any table columns currently, so we can drop it too
DROP TYPE IF EXISTS publish_status CASCADE;