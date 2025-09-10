--
-- V25: Create AssetMetadata records for intro videos and avatar videos
-- This migration ensures all intro videos and avatar videos have corresponding AssetMetadata records
-- Required for uniform asset storage approach
--

-- Create AssetMetadata records for intro videos that have published URLs but no asset metadata
INSERT INTO asset_metadata (
    id,
    presentation_id,
    slide_id,
    asset_type,
    bucket_name,
    object_key,
    file_name,
    file_size,
    content_type,
    upload_status,
    created_at,
    updated_at
)
SELECT 
    gen_random_uuid() as id,
    iv.presentation_id,
    NULL as slide_id,
    'PRESENTATION_INTRO_VIDEO'::text as asset_type,
    'ppt2video-poc' as bucket_name,
    -- Extract object key from published URL - assumes R2 URL format
    CASE 
        WHEN iv.published_url IS NOT NULL AND iv.published_url LIKE '%.r2.cloudflarestorage.com/%' THEN
            -- Extract path after domain and remove query parameters
            SUBSTRING(
                SUBSTRING(iv.published_url FROM 'cloudflarestorage\.com/(.*)'),
                '^([^?]*)'
            )
        ELSE
            'intro-videos/' || iv.id::text || '.mp4'
    END as object_key,
    'intro_video_' || iv.id::text || '.mp4' as file_name,
    NULL as file_size,
    'video/mp4' as content_type,
    'COMPLETED'::text as upload_status,
    iv.created_at,
    iv.updated_at
FROM intro_videos iv
WHERE iv.published_url IS NOT NULL 
  AND iv.r2_asset_id IS NULL;

-- Update intro_videos to reference the newly created AssetMetadata records
UPDATE intro_videos 
SET r2_asset_id = am.id
FROM asset_metadata am
WHERE intro_videos.published_url IS NOT NULL 
  AND intro_videos.r2_asset_id IS NULL
  AND am.asset_type = 'PRESENTATION_INTRO_VIDEO'
  AND am.presentation_id = intro_videos.presentation_id
  AND am.object_key LIKE '%' || intro_videos.id::text || '%';

-- Create AssetMetadata records for avatar videos that have published URLs but no asset metadata
INSERT INTO asset_metadata (
    id,
    presentation_id,
    slide_id,
    asset_type,
    bucket_name,
    object_key,
    file_name,
    file_size,
    content_type,
    upload_status,
    created_at,
    updated_at
)
SELECT 
    gen_random_uuid() as id,
    av.presentation_id,
    av.slide_id,
    'SLIDE_AVATAR_VIDEO'::text as asset_type,
    'ppt2video-poc' as bucket_name,
    -- Extract object key from published URL - assumes R2 URL format
    CASE 
        WHEN av.published_url IS NOT NULL AND av.published_url LIKE '%.r2.cloudflarestorage.com/%' THEN
            -- Extract path after domain and remove query parameters
            SUBSTRING(
                SUBSTRING(av.published_url FROM 'cloudflarestorage\.com/(.*)'),
                '^([^?]*)'
            )
        ELSE
            'avatar-videos/' || av.id::text || '.mp4'
    END as object_key,
    'avatar_video_' || av.id::text || '.mp4' as file_name,
    av.duration_seconds as file_size, -- Store duration as file_size for now
    'video/mp4' as content_type,
    'COMPLETED'::text as upload_status,
    av.created_at,
    av.updated_at
FROM avatar_videos av
WHERE av.published_url IS NOT NULL 
  AND av.r2_asset_id IS NULL;

-- Update avatar_videos to reference the newly created AssetMetadata records
UPDATE avatar_videos 
SET r2_asset_id = am.id
FROM asset_metadata am
WHERE avatar_videos.published_url IS NOT NULL 
  AND avatar_videos.r2_asset_id IS NULL
  AND am.asset_type = 'SLIDE_AVATAR_VIDEO'
  AND am.presentation_id = avatar_videos.presentation_id
  AND am.slide_id = avatar_videos.slide_id
  AND am.object_key LIKE '%' || avatar_videos.id::text || '%';

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_asset_metadata_intro_videos 
ON asset_metadata(presentation_id) 
WHERE asset_type = 'PRESENTATION_INTRO_VIDEO';

CREATE INDEX IF NOT EXISTS idx_asset_metadata_avatar_videos 
ON asset_metadata(presentation_id, slide_id) 
WHERE asset_type = 'SLIDE_AVATAR_VIDEO';

-- Log the migration results
DO $$
DECLARE
    intro_count INTEGER;
    avatar_count INTEGER;
BEGIN
    -- Count newly linked intro videos
    SELECT COUNT(*) INTO intro_count
    FROM intro_videos iv
    JOIN asset_metadata am ON iv.r2_asset_id = am.id
    WHERE am.asset_type = 'PRESENTATION_INTRO_VIDEO';
    
    -- Count newly linked avatar videos  
    SELECT COUNT(*) INTO avatar_count
    FROM avatar_videos av
    JOIN asset_metadata am ON av.r2_asset_id = am.id
    WHERE am.asset_type = 'SLIDE_AVATAR_VIDEO';
    
    RAISE NOTICE 'Migration V25 completed: % intro videos and % avatar videos now have AssetMetadata records', 
        intro_count, avatar_count;
END $$;