-- Update the foreign key constraint on slides table to add ON DELETE CASCADE
-- This ensures that when a presentation is deleted, all related slides are automatically deleted

-- First, drop the existing constraint
ALTER TABLE slides 
DROP CONSTRAINT IF EXISTS fk8radxrca56i75b0etc16gvfwv;

-- Add the constraint back with ON DELETE CASCADE
ALTER TABLE slides 
ADD CONSTRAINT fk_slides_presentation 
FOREIGN KEY (presentation_id) 
REFERENCES presentations(id) 
ON DELETE CASCADE;