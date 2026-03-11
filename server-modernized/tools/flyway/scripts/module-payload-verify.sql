\set ON_ERROR_STOP on

-- Summary counts for post-migration validation
SELECT
    (SELECT COUNT(*) FROM opendolphin.d_module) AS total_modules,
    (SELECT COUNT(*) FROM opendolphin.d_module WHERE entity IN ('medOrder', 'progressCourse')) AS target_modules,
    (SELECT COUNT(*)
       FROM opendolphin.d_module m
      WHERE m.entity IN ('medOrder', 'progressCourse')
        AND m.bean_json ? 'schemaVersion'
        AND m.bean_json ? 'moduleType'
        AND m.bean_json ? 'payloadJson') AS envelope_modules,
    (SELECT COUNT(*) FROM opendolphin.d_module_payload) AS payload_rows,
    (SELECT COUNT(*)
       FROM opendolphin.d_module m
      WHERE m.entity IN ('medOrder', 'progressCourse')
        AND m.bean_json ? 'schemaVersion'
        AND m.bean_json ? 'moduleType'
        AND m.bean_json ? 'payloadJson'
        AND NOT EXISTS (
            SELECT 1 FROM opendolphin.d_module_payload p WHERE p.module_id = m.id
        )) AS missing_payload_rows;

-- Sample discrepancies (if any)
SELECT
    m.id AS missing_module_id,
    m.entity,
    m.doc_id,
    m.started,
    m.recorded
FROM opendolphin.d_module m
WHERE m.entity IN ('medOrder', 'progressCourse')
  AND m.bean_json ? 'schemaVersion'
  AND m.bean_json ? 'moduleType'
  AND m.bean_json ? 'payloadJson'
  AND NOT EXISTS (
      SELECT 1 FROM opendolphin.d_module_payload p WHERE p.module_id = m.id
  )
ORDER BY m.id
LIMIT 50;
