-- Ensure Factor2BackupKey inserts work (id must be auto-generated).
-- Some dev schemas had d_factor2_backupkey.id without DEFAULT/IDENTITY, causing NOT NULL violations.

-- opendolphin schema
CREATE SEQUENCE IF NOT EXISTS opendolphin.d_factor2_backupkey_id_seq;

ALTER TABLE opendolphin.d_factor2_backupkey
    ALTER COLUMN id SET DEFAULT nextval('opendolphin.d_factor2_backupkey_id_seq'::regclass);

-- Align sequence to existing rows (if any). Next nextval() should return max(id)+1 (or 1 when empty).
SELECT setval(
    'opendolphin.d_factor2_backupkey_id_seq'::regclass,
    COALESCE((SELECT MAX(id) FROM opendolphin.d_factor2_backupkey), 0) + 1,
    false
);

ALTER SEQUENCE opendolphin.d_factor2_backupkey_id_seq
    OWNED BY opendolphin.d_factor2_backupkey.id;

-- public schema (kept for legacy / mixed-schema environments)
CREATE SEQUENCE IF NOT EXISTS public.d_factor2_backupkey_id_seq;

ALTER TABLE public.d_factor2_backupkey
    ALTER COLUMN id SET DEFAULT nextval('public.d_factor2_backupkey_id_seq'::regclass);

SELECT setval(
    'public.d_factor2_backupkey_id_seq'::regclass,
    COALESCE((SELECT MAX(id) FROM public.d_factor2_backupkey), 0) + 1,
    false
);

ALTER SEQUENCE public.d_factor2_backupkey_id_seq
    OWNED BY public.d_factor2_backupkey.id;
