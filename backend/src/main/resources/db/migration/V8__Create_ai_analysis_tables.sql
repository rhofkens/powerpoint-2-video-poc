-- Create deck_analysis table for storing AI analysis of entire presentations
CREATE TABLE deck_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL UNIQUE,
    overall_story TEXT,
    communication_intent TEXT,
    key_themes TEXT,
    target_audience VARCHAR(255),
    tone VARCHAR(100),
    analysis_metadata JSONB,
    model_used VARCHAR(100),
    prompt_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deck_analysis_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE
);

-- Create index on presentation_id for deck_analysis
CREATE INDEX idx_deck_analysis_presentation_id ON deck_analysis(presentation_id);

-- Create slide_analysis table for storing AI analysis of individual slides
CREATE TABLE slide_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL UNIQUE,
    general_message TEXT,
    visual_concepts JSONB,
    key_points JSONB,
    data_insights TEXT,
    transition_context TEXT,
    emphasis_level VARCHAR(50),
    analysis_metadata JSONB,
    model_used VARCHAR(100),
    prompt_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slide_analysis_slide 
        FOREIGN KEY (slide_id) 
        REFERENCES slides(id) 
        ON DELETE CASCADE
);

-- Create index on slide_id for slide_analysis
CREATE INDEX idx_slide_analysis_slide_id ON slide_analysis(slide_id);

-- Create slide_narratives table for storing AI-generated narratives
CREATE TABLE slide_narratives (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slide_id UUID NOT NULL,
    narrative_text TEXT NOT NULL,
    emotion_indicators JSONB,
    avatar_instructions JSONB,
    speech_markers JSONB,
    duration_seconds INTEGER,
    transition_phrase TEXT,
    emphasis_words JSONB,
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    generation_metadata JSONB,
    model_used VARCHAR(100),
    prompt_version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slide_narratives_slide 
        FOREIGN KEY (slide_id) 
        REFERENCES slides(id) 
        ON DELETE CASCADE
);

-- Create indexes for slide_narratives
CREATE INDEX idx_slide_narratives_slide_id ON slide_narratives(slide_id);
CREATE INDEX idx_slide_narratives_active ON slide_narratives(slide_id, is_active) WHERE is_active = TRUE;
CREATE INDEX idx_slide_narratives_version ON slide_narratives(slide_id, version);

-- Create function to update updated_at column (must be created before triggers)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Add trigger to update updated_at timestamp for deck_analysis
CREATE TRIGGER update_deck_analysis_updated_at
    BEFORE UPDATE ON deck_analysis
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add trigger to update updated_at timestamp for slide_analysis
CREATE TRIGGER update_slide_analysis_updated_at
    BEFORE UPDATE ON slide_analysis
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add trigger to update updated_at timestamp for slide_narratives
CREATE TRIGGER update_slide_narratives_updated_at
    BEFORE UPDATE ON slide_narratives
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();