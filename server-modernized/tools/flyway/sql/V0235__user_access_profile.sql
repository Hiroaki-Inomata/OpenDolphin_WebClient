-- Staff access profile for OpenDolphin users (sex / staff role metadata).
-- This table is intentionally separated from d_users to avoid legacy schema drift.
--
-- Baseline dumps may keep d_users in public schema. Prefer opendolphin when present,
-- otherwise fall back to public via search_path.
SET search_path TO opendolphin, public;

CREATE TABLE IF NOT EXISTS opendolphin.d_user_access_profile (
    user_pk BIGINT PRIMARY KEY,
    sex VARCHAR(1),
    staff_role VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_access_profile_user
        FOREIGN KEY (user_pk) REFERENCES d_users(id)
        ON DELETE CASCADE
);

-- Legacy schema dump may already contain d_user_access_profile with fewer columns.
-- Ensure we can evolve the table without requiring a drop/recreate.
ALTER TABLE opendolphin.d_user_access_profile
    ADD COLUMN IF NOT EXISTS staff_role VARCHAR(32);

ALTER TABLE opendolphin.d_user_access_profile
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE opendolphin.d_user_access_profile
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_user_access_profile_staff_role
    ON opendolphin.d_user_access_profile (staff_role);
