-- Add columns to track whether transitions were included in speech generation
ALTER TABLE slide_speeches 
ADD COLUMN IF NOT EXISTS transition_included BOOLEAN DEFAULT false,
ADD COLUMN IF NOT EXISTS transition_skipped_reason VARCHAR(255);

-- Add comment on new columns
COMMENT ON COLUMN slide_speeches.transition_included IS 'Whether the transition phrase was included in the generated speech';
COMMENT ON COLUMN slide_speeches.transition_skipped_reason IS 'Reason why the transition was skipped (e.g., redundant with next slide)';