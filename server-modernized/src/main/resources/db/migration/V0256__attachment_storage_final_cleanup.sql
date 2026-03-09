ALTER TABLE IF EXISTS d_attachment
    ALTER COLUMN uri SET NOT NULL,
    ALTER COLUMN digest SET NOT NULL;

ALTER TABLE IF EXISTS d_attachment
    DROP COLUMN IF EXISTS bytes;

CREATE INDEX IF NOT EXISTS d_attachment_uri_idx
    ON d_attachment (uri);

CREATE INDEX IF NOT EXISTS d_attachment_digest_idx
    ON d_attachment (digest);
