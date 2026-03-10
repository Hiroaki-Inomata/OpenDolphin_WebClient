ALTER TABLE IF EXISTS d_module
    ADD COLUMN IF NOT EXISTS bean_json jsonb;

ALTER TABLE IF EXISTS d_module
    ALTER COLUMN bean_json TYPE jsonb
    USING CASE
        WHEN bean_json IS NULL OR btrim(bean_json::text, '"') = '' THEN '{}'::jsonb
        ELSE bean_json::jsonb
    END;

UPDATE d_module
SET bean_json = '{}'::jsonb
WHERE bean_json IS NULL;

ALTER TABLE IF EXISTS d_module
    ALTER COLUMN bean_json SET NOT NULL;
