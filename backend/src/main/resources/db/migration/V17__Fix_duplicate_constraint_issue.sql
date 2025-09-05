-- Fix for duplicate constraint issue in CI environments
-- This migration handles the case where fk_slides_presentation might already exist

-- Use a DO block to conditionally handle the constraint
DO $$
BEGIN
    -- Check if the constraint exists
    IF NOT EXISTS (
        SELECT 1 
        FROM pg_constraint 
        WHERE conname = 'fk_slides_presentation' 
        AND conrelid = 'slides'::regclass
    ) THEN
        -- Only add the constraint if it doesn't exist
        ALTER TABLE slides 
        ADD CONSTRAINT fk_slides_presentation 
        FOREIGN KEY (presentation_id) 
        REFERENCES presentations(id) 
        ON DELETE CASCADE;
    END IF;
END $$;