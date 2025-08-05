-- Add new columns to presentations table
ALTER TABLE presentations 
  ADD COLUMN IF NOT EXISTS upload_timestamp TIMESTAMP,
  ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'UPLOADED',
  ADD COLUMN IF NOT EXISTS error_message TEXT;

-- Add new columns to slides table  
ALTER TABLE slides
  ADD COLUMN IF NOT EXISTS content_text TEXT,
  ADD COLUMN IF NOT EXISTS speaker_notes TEXT,
  ADD COLUMN IF NOT EXISTS layout_type VARCHAR(100);

-- Update existing presentations to have status if it's null
UPDATE presentations SET status = 'UPLOADED' WHERE status IS NULL;

-- Make status column not null after setting default values
ALTER TABLE presentations ALTER COLUMN status SET NOT NULL;