-- Enforce ORCA patient business key uniqueness in local patient table.
-- Precheck duplicates so operators can clean data deterministically before the unique index is created.
DO $$
DECLARE
    duplicate_sample TEXT;
BEGIN
    SELECT string_agg(format('%s:%s(x%s)', facilityid, patientid, duplicate_count), ', ')
      INTO duplicate_sample
      FROM (
            SELECT facilityid, patientid, COUNT(*) AS duplicate_count
              FROM opendolphin.d_patient
             WHERE facilityid IS NOT NULL
               AND patientid IS NOT NULL
             GROUP BY facilityid, patientid
            HAVING COUNT(*) > 1
             ORDER BY COUNT(*) DESC, facilityid, patientid
             LIMIT 10
           ) dup;

    IF duplicate_sample IS NOT NULL THEN
        RAISE EXCEPTION
            'Cannot enforce unique key on opendolphin.d_patient(facilityid, patientid). Duplicate pairs: %',
            duplicate_sample
        USING HINT = 'Clean duplicated pairs first. See server-modernized/tools/flyway/README.md.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS d_patient_facility_patientid_uidx
    ON opendolphin.d_patient (facilityid, patientid);
