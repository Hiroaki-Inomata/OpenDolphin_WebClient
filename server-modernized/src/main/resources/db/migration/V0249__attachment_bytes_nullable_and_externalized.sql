-- d_attachment.bytes is retained for legacy rows, but new externalized attachments persist uri/digest and may store NULL.
ALTER TABLE IF EXISTS d_attachment
    ALTER COLUMN bytes DROP NOT NULL;

COMMENT ON COLUMN d_attachment.uri IS
    'External attachment locator. New externalized writes persist uri + digest and keep bytes NULL; legacy inline rows remain readable.';
