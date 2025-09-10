-- Add Shotstack URL field to asset_metadata table
-- This stores the Shotstack-hosted URL when assets are uploaded to Shotstack for preview mode

ALTER TABLE asset_metadata 
ADD COLUMN IF NOT EXISTS shotstack_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS shotstack_asset_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS shotstack_uploaded_at TIMESTAMP;

-- Add index for quick lookup by Shotstack asset ID
CREATE INDEX IF NOT EXISTS idx_asset_metadata_shotstack_asset_id 
ON asset_metadata(shotstack_asset_id) 
WHERE shotstack_asset_id IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN asset_metadata.shotstack_url IS 'URL of the asset hosted on Shotstack servers (used in shotstack-upload mode)';
COMMENT ON COLUMN asset_metadata.shotstack_asset_id IS 'Shotstack source/asset ID returned from the Ingest API';
COMMENT ON COLUMN asset_metadata.shotstack_uploaded_at IS 'Timestamp when the asset was uploaded to Shotstack';