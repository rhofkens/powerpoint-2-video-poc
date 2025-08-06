-- Increase field sizes for AI-generated content that may exceed original limits

-- Update deck_analysis table
ALTER TABLE deck_analysis
    ALTER COLUMN tone TYPE VARCHAR(500),
    ALTER COLUMN model_used TYPE VARCHAR(255);

-- Update slide_analysis table  
ALTER TABLE slide_analysis
    ALTER COLUMN model_used TYPE VARCHAR(255);

-- Update slide_narratives table
ALTER TABLE slide_narratives
    ALTER COLUMN model_used TYPE VARCHAR(255);