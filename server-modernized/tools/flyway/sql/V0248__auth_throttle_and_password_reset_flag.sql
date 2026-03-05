-- Authentication brute-force guard state and password-reset policy flag.
-- Keep in sync with server-modernized/tools/flyway/sql.
SET search_path TO opendolphin, public;

CREATE TABLE IF NOT EXISTS opendolphin.d_auth_account_failure (
    facility_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    fail_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lock_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (facility_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_auth_account_failure_lock_until
    ON opendolphin.d_auth_account_failure (lock_until);

CREATE INDEX IF NOT EXISTS idx_auth_account_failure_updated_at
    ON opendolphin.d_auth_account_failure (updated_at);

CREATE TABLE IF NOT EXISTS opendolphin.d_auth_ip_failure (
    client_ip VARCHAR(64) PRIMARY KEY,
    fail_count INTEGER NOT NULL DEFAULT 0,
    window_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    throttle_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_auth_ip_failure_throttle_until
    ON opendolphin.d_auth_ip_failure (throttle_until);

CREATE INDEX IF NOT EXISTS idx_auth_ip_failure_updated_at
    ON opendolphin.d_auth_ip_failure (updated_at);

ALTER TABLE opendolphin.d_user_access_profile
    ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
