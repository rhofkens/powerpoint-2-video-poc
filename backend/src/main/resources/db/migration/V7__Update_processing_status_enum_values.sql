-- Update the processing_status column to support new AI analysis status values
-- This migration ensures the VARCHAR column can hold the new status values

-- Since processing_status is already a VARCHAR(50), we just need to document the new values
-- The new values are: RENDERING_READY, INTENT_ANALYSIS_COMPLETE, and NARRATIVE_COMPLETE

-- Add a check constraint to ensure only valid values are used (optional but recommended)
-- First drop any existing constraint
ALTER TABLE presentations DROP CONSTRAINT IF EXISTS check_processing_status_values;

-- Add the new constraint with all valid values
ALTER TABLE presentations ADD CONSTRAINT check_processing_status_values 
CHECK (processing_status IN (
    'UPLOADED',
    'PARSING',
    'RENDERING_READY',
    'ANALYZING', 
    'INTENT_ANALYSIS_COMPLETE',
    'NARRATIVE_COMPLETE',
    'GENERATING_CONTENT',
    'COMPLETED',
    'FAILED'
));

-- Note: The order of values matches the Java enum definition:
-- UPLOADED -> PARSING -> RENDERING_READY -> ANALYZING -> INTENT_ANALYSIS_COMPLETE -> NARRATIVE_COMPLETE -> GENERATING_CONTENT -> COMPLETED -> FAILED