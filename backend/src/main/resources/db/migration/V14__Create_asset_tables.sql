-- Create asset_metadata table
CREATE TABLE asset_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES presentations(id) ON DELETE CASCADE,
    slide_id UUID REFERENCES slides(id) ON DELETE CASCADE,
    asset_type VARCHAR(50) NOT NULL CHECK (asset_type IN (
        'SLIDE_IMAGE', 
        'SLIDE_AUDIO', 
        'SLIDE_AVATAR_VIDEO', 
        'PRESENTATION_INTRO_VIDEO',
        'PRESENTATION_FULL_VIDEO'
    )),
    bucket_name VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    content_type VARCHAR(100),
    checksum VARCHAR(255),
    metadata JSONB,
    upload_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (upload_status IN (
        'PENDING', 
        'UPLOADING', 
        'COMPLETED', 
        'FAILED'
    )),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(bucket_name, object_key)
);

-- Create indexes for asset_metadata
CREATE INDEX idx_asset_metadata_presentation_id ON asset_metadata(presentation_id);
CREATE INDEX idx_asset_metadata_slide_id ON asset_metadata(slide_id);
CREATE INDEX idx_asset_metadata_asset_type ON asset_metadata(asset_type);
CREATE INDEX idx_asset_metadata_upload_status ON asset_metadata(upload_status);

-- Create presigned_urls table
CREATE TABLE presigned_urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_metadata_id UUID NOT NULL REFERENCES asset_metadata(id) ON DELETE CASCADE,
    url_type VARCHAR(50) NOT NULL CHECK (url_type IN ('UPLOAD', 'DOWNLOAD')),
    presigned_url TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    access_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    UNIQUE(asset_metadata_id, url_type, expires_at)
);

-- Create indexes for presigned_urls
CREATE INDEX idx_presigned_urls_asset_metadata_id ON presigned_urls(asset_metadata_id);
CREATE INDEX idx_presigned_urls_expires_at ON presigned_urls(expires_at);
CREATE INDEX idx_presigned_urls_is_active ON presigned_urls(is_active);

-- Add trigger to update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_asset_metadata_updated_at
    BEFORE UPDATE ON asset_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();