-- Add new columns to presentations table for separated architecture
ALTER TABLE presentations
  ADD COLUMN IF NOT EXISTS parsing_started_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS parsing_completed_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS rendering_started_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS rendering_completed_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS renderer_used VARCHAR(50),
  ADD COLUMN IF NOT EXISTS rendering_error_message TEXT;

-- Update status column to support new states
ALTER TABLE presentations
  DROP CONSTRAINT IF EXISTS presentations_status_check;

ALTER TABLE presentations
  ADD CONSTRAINT presentations_status_check 
  CHECK (status IN ('UPLOADED', 'PARSING', 'PARSED', 'RENDERING', 'COMPLETED', 'FAILED'));

-- Add new columns to slides table for image information
ALTER TABLE slides
  ADD COLUMN IF NOT EXISTS image_path VARCHAR(500),
  ADD COLUMN IF NOT EXISTS image_generated_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS image_width INTEGER,
  ADD COLUMN IF NOT EXISTS image_height INTEGER,
  ADD COLUMN IF NOT EXISTS rendering_status VARCHAR(50) DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS rendering_error_message TEXT;

-- Add constraint for rendering_status
ALTER TABLE slides
  ADD CONSTRAINT slides_rendering_status_check 
  CHECK (rendering_status IN ('PENDING', 'RENDERING', 'COMPLETED', 'FAILED'));