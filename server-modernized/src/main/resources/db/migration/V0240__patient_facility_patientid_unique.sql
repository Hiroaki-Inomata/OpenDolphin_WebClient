-- Ensure ORCA Patient_ID is unique per facility in the local patient table.
-- This prevents accidental duplicate patient records when importing/syncing from ORCA.
CREATE UNIQUE INDEX IF NOT EXISTS d_patient_facility_patientid_uidx
    ON opendolphin.d_patient (facilityid, patientid);

