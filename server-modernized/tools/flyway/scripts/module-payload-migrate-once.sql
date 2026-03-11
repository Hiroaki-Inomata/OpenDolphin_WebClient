\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS opendolphin.d_module_payload_migration_run (
    run_id TEXT PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status TEXT NOT NULL,
    before_total_modules BIGINT NOT NULL,
    before_envelope_modules BIGINT NOT NULL,
    before_payload_rows BIGINT NOT NULL,
    migrated_rows BIGINT NOT NULL DEFAULT 0,
    after_payload_rows BIGINT,
    after_missing_rows BIGINT,
    notes TEXT
);

INSERT INTO opendolphin.d_module_payload_migration_run (
    run_id,
    started_at,
    status,
    before_total_modules,
    before_envelope_modules,
    before_payload_rows,
    notes
)
SELECT
    :'run_id',
    CURRENT_TIMESTAMP,
    'running',
    (SELECT COUNT(*) FROM opendolphin.d_module),
    (SELECT COUNT(*)
       FROM opendolphin.d_module m
      WHERE m.entity IN ('medOrder', 'progressCourse')
        AND m.bean_json ? 'schemaVersion'
        AND m.bean_json ? 'moduleType'
        AND m.bean_json ? 'payloadJson'),
    (SELECT COUNT(*) FROM opendolphin.d_module_payload),
    'P6-09 one-shot migration start'
ON CONFLICT (run_id) DO UPDATE SET
    started_at = EXCLUDED.started_at,
    finished_at = NULL,
    status = 'running',
    before_total_modules = EXCLUDED.before_total_modules,
    before_envelope_modules = EXCLUDED.before_envelope_modules,
    before_payload_rows = EXCLUDED.before_payload_rows,
    migrated_rows = 0,
    after_payload_rows = NULL,
    after_missing_rows = NULL,
    notes = EXCLUDED.notes;

WITH source_rows AS (
    SELECT
        m.id AS module_id,
        COALESCE(NULLIF(m.bean_json->>'schemaVersion', '')::INTEGER, 1) AS schema_version,
        NULLIF(m.bean_json->>'moduleType', '') AS module_type,
        (m.bean_json->>'payloadJson')::JSONB AS payload_json,
        NULLIF(m.bean_json->>'payloadHash', '') AS payload_hash
    FROM opendolphin.d_module m
    WHERE m.entity IN ('medOrder', 'progressCourse')
      AND m.bean_json ? 'schemaVersion'
      AND m.bean_json ? 'moduleType'
      AND m.bean_json ? 'payloadJson'
      AND NULLIF(m.bean_json->>'payloadJson', '') IS NOT NULL
),
upserted AS (
    INSERT INTO opendolphin.d_module_payload (
        module_id,
        schema_version,
        module_type,
        payload_json,
        payload_hash,
        created_at,
        updated_at
    )
    SELECT
        s.module_id,
        s.schema_version,
        s.module_type,
        s.payload_json,
        s.payload_hash,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    FROM source_rows s
    ON CONFLICT (module_id) DO UPDATE SET
        schema_version = EXCLUDED.schema_version,
        module_type = EXCLUDED.module_type,
        payload_json = EXCLUDED.payload_json,
        payload_hash = EXCLUDED.payload_hash,
        updated_at = CURRENT_TIMESTAMP
    RETURNING module_id
),
stats AS (
    SELECT
        (SELECT COUNT(*) FROM upserted) AS migrated_rows,
        (SELECT COUNT(*) FROM opendolphin.d_module_payload) AS after_payload_rows,
        (SELECT COUNT(*)
           FROM opendolphin.d_module m
          WHERE m.entity IN ('medOrder', 'progressCourse')
            AND m.bean_json ? 'schemaVersion'
            AND m.bean_json ? 'moduleType'
            AND m.bean_json ? 'payloadJson'
            AND NOT EXISTS (
                SELECT 1 FROM opendolphin.d_module_payload p WHERE p.module_id = m.id
            )) AS after_missing_rows
)
UPDATE opendolphin.d_module_payload_migration_run run
SET
    finished_at = CURRENT_TIMESTAMP,
    status = 'completed',
    migrated_rows = stats.migrated_rows,
    after_payload_rows = stats.after_payload_rows,
    after_missing_rows = stats.after_missing_rows,
    notes = 'P6-09 one-shot migration completed'
FROM stats
WHERE run.run_id = :'run_id';

SELECT *
FROM opendolphin.d_module_payload_migration_run
WHERE run_id = :'run_id';
