-- Add presentation title and author fields to deck_analysis table
-- These fields will store the actual presentation title (from first slide)
-- and author/company information extracted by AI analysis

ALTER TABLE deck_analysis 
ADD COLUMN IF NOT EXISTS presentation_title VARCHAR(500),
ADD COLUMN IF NOT EXISTS presentation_author VARCHAR(500);

-- Add comments for documentation
COMMENT ON COLUMN deck_analysis.presentation_title IS 'The actual presentation title extracted from the first slide';
COMMENT ON COLUMN deck_analysis.presentation_author IS 'The author or company name, formatted as "By [Name]" or similar';