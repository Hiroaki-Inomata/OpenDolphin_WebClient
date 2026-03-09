ALTER TABLE IF EXISTS d_patient
    ALTER COLUMN birthday TYPE DATE
    USING CASE
        WHEN birthday IS NULL OR btrim(birthday::text, '"') = '' THEN NULL
        ELSE birthday::date
    END;

ALTER TABLE IF EXISTS d_patient_visit
    ALTER COLUMN pvtDate TYPE TIMESTAMP WITHOUT TIME ZONE
    USING CASE
        WHEN pvtDate IS NULL OR btrim(pvtDate::text, '"') = '' THEN NULL
        ELSE replace(left(pvtDate::text, 19), 'T', ' ')::timestamp
    END;

ALTER TABLE IF EXISTS d_patient_visit
    ALTER COLUMN pvtDate SET NOT NULL;

DROP INDEX IF EXISTS d_patient_visit_facility_date_idx;

CREATE INDEX IF NOT EXISTS d_patient_visit_facility_date_idx
    ON d_patient_visit (facilityId, pvtDate);

CREATE INDEX IF NOT EXISTS d_patient_visit_facility_date_status_idx
    ON d_patient_visit (facilityId, pvtDate, status);
