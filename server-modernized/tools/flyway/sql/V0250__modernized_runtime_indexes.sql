-- Runtime indexes for modernized d_* tables.
-- Legacy V0002 targeted old *_model tables, so add the actual production table indexes here.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS d_document_karte_started_status_idx
    ON d_document (karte_id, started DESC, status);

CREATE INDEX IF NOT EXISTS d_patient_visit_facility_date_status_idx
    ON d_patient_visit (facilityId, pvtDate, status);

CREATE INDEX IF NOT EXISTS d_patient_kana_name_trgm_idx
    ON d_patient USING gin (kanaName gin_trgm_ops);

CREATE INDEX IF NOT EXISTS d_patient_full_name_trgm_idx
    ON d_patient USING gin (fullName gin_trgm_ops);
