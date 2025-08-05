-- Create presentations table
CREATE TABLE IF NOT EXISTS presentations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255),
    original_filename VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    upload_timestamp TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    error_message TEXT,
    processing_status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED',
    narrative_style VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on status for filtering
CREATE INDEX IF NOT EXISTS idx_presentations_status ON presentations(status);

-- Create index on processing_status for filtering
CREATE INDEX IF NOT EXISTS idx_presentations_processing_status ON presentations(processing_status);

-- Create index on upload_timestamp for sorting
CREATE INDEX IF NOT EXISTS idx_presentations_upload_timestamp ON presentations(upload_timestamp);