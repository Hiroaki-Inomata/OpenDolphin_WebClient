ALTER TABLE IF EXISTS d_health_insurance
    ADD COLUMN IF NOT EXISTS bean_json jsonb;

ALTER TABLE IF EXISTS d_health_insurance
    ALTER COLUMN bean_json TYPE jsonb
    USING CASE
        WHEN bean_json IS NULL OR btrim(bean_json::text, '"') = '' THEN '{}'::jsonb
        ELSE bean_json::jsonb
    END;

UPDATE d_health_insurance
SET bean_json = '{}'::jsonb
WHERE bean_json IS NULL;

ALTER TABLE IF EXISTS d_health_insurance
    ALTER COLUMN bean_json SET NOT NULL;

ALTER TABLE IF EXISTS d_health_insurance
    DROP COLUMN IF EXISTS beanBytes;
