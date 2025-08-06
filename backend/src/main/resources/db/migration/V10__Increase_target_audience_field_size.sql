-- Increase target_audience field size to accommodate longer AI-generated content

ALTER TABLE deck_analysis
    ALTER COLUMN target_audience TYPE VARCHAR(500);