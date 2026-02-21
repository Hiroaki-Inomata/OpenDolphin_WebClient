-- ORCA user link mapping for Administration ORCA user integration.
-- Keep in sync with server-modernized/src/main/resources/db/migration.
--
-- Baseline dumps may keep d_users in public schema. Prefer opendolphin when present,
-- otherwise fall back to public via search_path.
SET search_path TO opendolphin, public;

CREATE TABLE IF NOT EXISTS opendolphin.d_orca_user_link (
    ehr_user_pk BIGINT PRIMARY KEY,
    orca_user_id VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(255),
    CONSTRAINT fk_orca_user_link_user
        FOREIGN KEY (ehr_user_pk) REFERENCES d_users(id)
        ON DELETE CASCADE
);

ALTER TABLE opendolphin.d_orca_user_link
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE opendolphin.d_orca_user_link
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE opendolphin.d_orca_user_link
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_orca_user_link_orca_user_id
    ON opendolphin.d_orca_user_link (orca_user_id);
