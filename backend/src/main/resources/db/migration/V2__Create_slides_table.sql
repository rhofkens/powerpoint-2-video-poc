-- Create slides table
CREATE TABLE IF NOT EXISTS slides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL,
    slide_number INTEGER NOT NULL,
    title VARCHAR(500),
    content TEXT,
    content_text TEXT,
    speaker_notes TEXT,
    layout_type VARCHAR(100),
    image_path VARCHAR(500),
    audio_path VARCHAR(500),
    video_path VARCHAR(500),
    generated_narrative TEXT,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'EXTRACTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on presentation_id for joins
CREATE INDEX IF NOT EXISTS idx_slides_presentation_id ON slides(presentation_id);

-- Create index on slide_number for ordering
CREATE INDEX IF NOT EXISTS idx_slides_slide_number ON slides(presentation_id, slide_number);

-- Create index on processing_status for filtering
CREATE INDEX IF NOT EXISTS idx_slides_processing_status ON slides(processing_status);