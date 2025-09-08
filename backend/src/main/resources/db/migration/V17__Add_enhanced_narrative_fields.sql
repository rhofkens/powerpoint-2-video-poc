-- Add new columns for enhanced narratives storage
-- This migration adds separate storage for emotionally enhanced narratives
-- while preserving the original narrative text for subtitles and translation

-- Add new columns to slide_narratives table
ALTER TABLE slide_narratives 
ADD COLUMN enhanced_narrative_text TEXT,
ADD COLUMN enhancement_metadata JSONB,
ADD COLUMN enhancement_timestamp TIMESTAMP,
ADD COLUMN enhancement_model_used VARCHAR(100);

-- Create performance index for queries filtering by enhancement status
CREATE INDEX idx_slide_narratives_enhanced 
ON slide_narratives(id) 
WHERE enhanced_narrative_text IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN slide_narratives.enhanced_narrative_text IS 
  'Emotionally enhanced version of narrative_text for TTS engines (e.g., with CAPS, ellipses for ElevenLabs)';
COMMENT ON COLUMN slide_narratives.enhancement_metadata IS 
  'Metadata about the enhancement process including engine type, original/enhanced lengths, and other parameters';
COMMENT ON COLUMN slide_narratives.enhancement_timestamp IS 
  'Timestamp when the emotional enhancement was applied';
COMMENT ON COLUMN slide_narratives.enhancement_model_used IS 
  'The AI model or enhancer used for emotional enhancement (e.g., elevenlabs, gpt-4o-mini)';

-- Note: narrative_text column remains as the original, unenhanced narrative
COMMENT ON COLUMN slide_narratives.narrative_text IS 
  'Original narrative text without emotional enhancements - used for subtitles and translation';