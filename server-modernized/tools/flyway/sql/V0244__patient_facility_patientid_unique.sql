-- Enforce ORCA patient business key uniqueness in local patient table.
-- Baseline dumps may keep d_patient in public; prefer opendolphin and fall back to public.
DO $$
DECLARE
    target_table TEXT;
    duplicate_sample TEXT;
BEGIN
    IF to_regclass('opendolphin.d_patient') IS NOT NULL THEN
        target_table := 'opendolphin.d_patient';
    ELSIF to_regclass('public.d_patient') IS NOT NULL THEN
        target_table := 'public.d_patient';
    ELSE
        RAISE EXCEPTION 'Cannot enforce unique key: d_patient table not found in opendolphin/public';
    END IF;

    EXECUTE format(
        'SELECT string_agg(format(''%%s:%%s(x%%s)'', facilityid, patientid, duplicate_count), '', '')
           FROM (
                 SELECT facilityid, patientid, COUNT(*) AS duplicate_count
                   FROM %s
                  WHERE facilityid IS NOT NULL
                    AND patientid IS NOT NULL
                  GROUP BY facilityid, patientid
                 HAVING COUNT(*) > 1
                  ORDER BY COUNT(*) DESC, facilityid, patientid
                  LIMIT 10
                ) dup',
        target_table
    )
    INTO duplicate_sample;

    IF duplicate_sample IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot enforce unique key on %(facilityid, patientid). Duplicate pairs: %',
            target_table,
            duplicate_sample
        USING HINT = 'Clean duplicated pairs first. See server-modernized/tools/flyway/README.md.';
    END IF;

    EXECUTE format(
        'CREATE UNIQUE INDEX IF NOT EXISTS d_patient_facility_patientid_uidx ON %s (facilityid, patientid)',
        target_table
    );
END $$;
