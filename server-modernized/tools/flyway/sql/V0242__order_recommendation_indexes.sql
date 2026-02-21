-- Indexes for order recommendation aggregation query paths.
-- Improves patient/facility scoped scans on d_module + d_karte joins.
CREATE INDEX IF NOT EXISTS d_karte_patient_id_idx
    ON d_karte (patient_id);

CREATE INDEX IF NOT EXISTS d_module_karte_entity_started_idx
    ON d_module (karte_id, entity, started DESC);

CREATE INDEX IF NOT EXISTS d_module_entity_started_idx
    ON d_module (entity, started DESC);
