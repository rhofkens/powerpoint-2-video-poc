-- Migration script to detect and migrate existing enhanced narratives
-- This script attempts to identify narratives that have already been emotionally enhanced
-- and moves them to the enhanced_narrative_text field while keeping a copy as the original

-- First, let's create a temporary analysis of which narratives appear to be enhanced
-- Enhanced narratives typically contain:
-- 1. Ellipses (...) for dramatic pauses
-- 2. CAPITALIZED words for emphasis
-- 3. Multiple exclamation marks
-- 4. Em dashes (—) for interruptions

-- Step 1: Identify potentially enhanced narratives and copy them to enhanced field
-- We'll be conservative and only mark narratives as enhanced if they have strong indicators
UPDATE slide_narratives
SET 
    enhanced_narrative_text = narrative_text,
    enhancement_timestamp = updated_at,
    enhancement_model_used = 'elevenlabs-legacy-migration',
    enhancement_metadata = jsonb_build_object(
        'migration_note', 'Detected as enhanced during V18 migration',
        'original_lost', true,
        'detection_method', 'pattern_matching',
        'migration_date', CURRENT_TIMESTAMP
    )
WHERE 
    is_active = true
    AND enhanced_narrative_text IS NULL
    AND (
        -- Check for multiple enhancement indicators
        (
            -- Has ellipses for pauses
            narrative_text LIKE '%...%'
            AND (
                -- Has CAPITALIZED words (at least 2 consecutive capitals not at start)
                narrative_text ~ '\s[A-Z]{2,}'
                OR 
                -- Has exclamation marks
                narrative_text LIKE '%!%'
            )
        )
        OR
        -- Has em dashes and capitals
        (
            narrative_text LIKE '%—%'
            AND narrative_text ~ '\s[A-Z]{2,}'
        )
    );

-- Step 2: Log migration results
DO $$
DECLARE
    total_narratives INTEGER;
    migrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_narratives 
    FROM slide_narratives 
    WHERE is_active = true;
    
    SELECT COUNT(*) INTO migrated_count 
    FROM slide_narratives 
    WHERE is_active = true 
    AND enhanced_narrative_text IS NOT NULL
    AND enhancement_model_used = 'elevenlabs-legacy-migration';
    
    RAISE NOTICE 'Migration complete: % out of % active narratives identified as enhanced and migrated', 
                 migrated_count, total_narratives;
END $$;

-- Step 3: For narratives that were detected as enhanced but we lost the original,
-- we'll need to manually regenerate the original narrative later if needed.
-- This is tracked in the enhancement_metadata field with 'original_lost': true

-- Note: After this migration, the system will:
-- 1. Always save new narratives to narrative_text (original)
-- 2. Save enhanced versions to enhanced_narrative_text
-- 3. Use enhanced_narrative_text for audio generation when available
-- 4. Use narrative_text for subtitles and translation