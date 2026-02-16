-- Align FK targets to opendolphin schema (avoid public.* mismatch)
-- NOTE: Baseline dumps may still have d_karte / d_users in public schema.
-- Prefer opendolphin when present, otherwise fall back to public via search_path.
SET search_path TO opendolphin, public;

ALTER TABLE IF EXISTS opendolphin.d_document DROP CONSTRAINT IF EXISTS fk6s9ifrm58t6jr9qamv7ey83lm;
ALTER TABLE IF EXISTS opendolphin.d_document DROP CONSTRAINT IF EXISTS fk_d_document_karte;
ALTER TABLE IF EXISTS opendolphin.d_document DROP CONSTRAINT IF EXISTS fk_d_document_creator;
ALTER TABLE IF EXISTS opendolphin.d_document DROP CONSTRAINT IF EXISTS fkf9jkp9t07q15ubahu0lgt7kpk;

ALTER TABLE IF EXISTS opendolphin.d_document
    ADD CONSTRAINT fk_d_document_karte FOREIGN KEY (karte_id) REFERENCES d_karte(id) NOT VALID;
ALTER TABLE IF EXISTS opendolphin.d_document
    ADD CONSTRAINT fk_d_document_creator FOREIGN KEY (creator_id) REFERENCES d_users(id) NOT VALID;

ALTER TABLE IF EXISTS opendolphin.d_module DROP CONSTRAINT IF EXISTS fk8snks9qh1q0itl4l2mpmnp06y;
ALTER TABLE IF EXISTS opendolphin.d_module DROP CONSTRAINT IF EXISTS fk_d_module_karte;
ALTER TABLE IF EXISTS opendolphin.d_module DROP CONSTRAINT IF EXISTS fk_d_module_creator;
ALTER TABLE IF EXISTS opendolphin.d_module DROP CONSTRAINT IF EXISTS fke7g6rg8pl0jaw2h0df9jymei5;

ALTER TABLE IF EXISTS opendolphin.d_module
    ADD CONSTRAINT fk_d_module_karte FOREIGN KEY (karte_id) REFERENCES d_karte(id) NOT VALID;
ALTER TABLE IF EXISTS opendolphin.d_module
    ADD CONSTRAINT fk_d_module_creator FOREIGN KEY (creator_id) REFERENCES d_users(id) NOT VALID;
