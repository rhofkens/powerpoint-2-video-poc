-- Add columns to track LLM prompt generation in intro_videos table
ALTER TABLE intro_videos 
ADD COLUMN IF NOT EXISTS prompt_generation_method VARCHAR(50) DEFAULT 'TEMPLATE',
ADD COLUMN IF NOT EXISTS prompt_generation_time_ms BIGINT,
ADD COLUMN IF NOT EXISTS prompt_text_full TEXT;

-- Add index for analytics queries
CREATE INDEX IF NOT EXISTS idx_intro_videos_prompt_method 
ON intro_videos(prompt_generation_method);

-- Add comment on columns
COMMENT ON COLUMN intro_videos.prompt_generation_method IS 'Method used to generate prompt: LLM or TEMPLATE';
COMMENT ON COLUMN intro_videos.prompt_generation_time_ms IS 'Time taken to generate prompt in milliseconds';
COMMENT ON COLUMN intro_videos.prompt_text_full IS 'Full prompt text used for video generation';