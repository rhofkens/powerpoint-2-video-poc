-- Add content word count to slides table for tracking content metrics
ALTER TABLE slides
ADD COLUMN IF NOT EXISTS content_word_count INTEGER;

-- Add target duration to slide_narratives table for tracking intended narrative length
ALTER TABLE slide_narratives
ADD COLUMN IF NOT EXISTS target_duration_seconds INTEGER;

-- Add slide type to slide_analysis table for categorizing slides
ALTER TABLE slide_analysis
ADD COLUMN IF NOT EXISTS slide_type VARCHAR(20);

-- Create indexes for new fields that might be queried
CREATE INDEX IF NOT EXISTS idx_slides_content_word_count ON slides(content_word_count);
CREATE INDEX IF NOT EXISTS idx_slide_narratives_target_duration ON slide_narratives(target_duration_seconds);
CREATE INDEX IF NOT EXISTS idx_slide_analysis_slide_type ON slide_analysis(slide_type);

-- Add comments to document the new columns
COMMENT ON COLUMN slides.content_word_count IS 'Number of words in the slide content text';
COMMENT ON COLUMN slide_narratives.target_duration_seconds IS 'Target duration in seconds for the narrative';
COMMENT ON COLUMN slide_analysis.slide_type IS 'Type of slide: INTRO, CONTENT, SEPARATOR, THANK_YOU, UNKNOWN';