-- V2: Rename overall_verdict to verdict_sentence to match new dynamic analysis shape.
-- The `analysis` JSONB column stores the full structured response, so no data is migrated.

ALTER TABLE search_results
    RENAME COLUMN overall_verdict TO verdict_sentence;

-- Add product_category column for faster filtering/display later
ALTER TABLE search_results
    ADD COLUMN IF NOT EXISTS product_category VARCHAR(100);
