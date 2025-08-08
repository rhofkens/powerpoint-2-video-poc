-- Create table for storing generated speech data
CREATE TABLE slide_speeches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL,
    slide_id UUID NOT NULL,
    slide_narrative_id UUID NOT NULL,
    audio_file_path VARCHAR(500) NOT NULL,
    voice_id VARCHAR(100) NOT NULL,
    voice_style VARCHAR(50),
    model_used VARCHAR(100) NOT NULL,
    duration_seconds DOUBLE PRECISION,
    timing_data JSONB,
    request_id VARCHAR(100),
    output_format VARCHAR(50),
    generation_metadata JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_slide_speech_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_slide_speech_slide 
        FOREIGN KEY (slide_id) 
        REFERENCES slides(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT fk_slide_speech_narrative 
        FOREIGN KEY (slide_narrative_id) 
        REFERENCES slide_narratives(id) 
        ON DELETE CASCADE
);

-- Create indexes for better query performance
CREATE INDEX idx_slide_speeches_presentation_id ON slide_speeches(presentation_id);
CREATE INDEX idx_slide_speeches_slide_id ON slide_speeches(slide_id);
CREATE INDEX idx_slide_speeches_narrative_id ON slide_speeches(slide_narrative_id);
CREATE INDEX idx_slide_speeches_active ON slide_speeches(is_active);
CREATE INDEX idx_slide_speeches_created_at ON slide_speeches(created_at DESC);

-- Add unique constraint to prevent duplicate speeches for the same narrative
CREATE UNIQUE INDEX idx_slide_speeches_narrative_active 
    ON slide_speeches(slide_narrative_id, is_active) 
    WHERE is_active = true;

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_slide_speeches_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER slide_speeches_updated_at_trigger
    BEFORE UPDATE ON slide_speeches
    FOR EACH ROW
    EXECUTE FUNCTION update_slide_speeches_updated_at();

-- Add comment on table
COMMENT ON TABLE slide_speeches IS 'Stores generated speech audio files and timing data for slide narratives';
COMMENT ON COLUMN slide_speeches.timing_data IS 'JSON array of character-level timestamps from ElevenLabs API';
COMMENT ON COLUMN slide_speeches.generation_metadata IS 'Metadata about the generation process including voice settings';
COMMENT ON COLUMN slide_speeches.request_id IS 'ElevenLabs request ID for request stitching';