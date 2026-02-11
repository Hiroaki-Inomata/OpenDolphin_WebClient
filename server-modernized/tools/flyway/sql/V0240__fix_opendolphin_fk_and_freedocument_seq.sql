-- Repair mixed-schema FK references and freedocument sequence/table gaps.
-- Some dev DBs contain opendolphin.* tables whose FKs still point to public.*,
-- and d_patient_freedocument exists only in public without a usable sequence/default.

CREATE SEQUENCE IF NOT EXISTS opendolphin.d_patient_freedocument_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE IF NOT EXISTS opendolphin.d_patient_freedocument (
    id BIGINT NOT NULL DEFAULT nextval('opendolphin.d_patient_freedocument_seq'),
    comment TEXT,
    confirmed TIMESTAMP NOT NULL,
    facilitypatid VARCHAR(255) NOT NULL,
    CONSTRAINT d_patient_freedocument_pkey PRIMARY KEY (id)
);

ALTER TABLE IF EXISTS opendolphin.d_patient_freedocument
    ALTER COLUMN id SET DEFAULT nextval('opendolphin.d_patient_freedocument_seq');

ALTER SEQUENCE IF EXISTS opendolphin.d_patient_freedocument_seq
    OWNED BY opendolphin.d_patient_freedocument.id;

DO $$
BEGIN
    IF to_regclass('public.d_patient_freedocument') IS NOT NULL THEN
        INSERT INTO opendolphin.d_patient_freedocument (id, comment, confirmed, facilitypatid)
        SELECT id, comment, confirmed, facilitypatid
        FROM public.d_patient_freedocument
        ON CONFLICT (id) DO NOTHING;
    END IF;
END$$;

DO $$
DECLARE
    max_id BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) INTO max_id FROM opendolphin.d_patient_freedocument;
    IF max_id = 0 THEN
        PERFORM setval('opendolphin.d_patient_freedocument_seq', 1, false);
    ELSE
        PERFORM setval('opendolphin.d_patient_freedocument_seq', max_id, true);
    END IF;
END$$;

-- Compatibility fallback for environments still touching public.d_patient_freedocument.
CREATE SEQUENCE IF NOT EXISTS public.d_patient_freedocument_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE IF EXISTS public.d_patient_freedocument
    ALTER COLUMN id SET DEFAULT nextval('public.d_patient_freedocument_seq');

ALTER SEQUENCE IF EXISTS public.d_patient_freedocument_seq
    OWNED BY public.d_patient_freedocument.id;

DO $$
DECLARE
    max_id BIGINT;
BEGIN
    IF to_regclass('public.d_patient_freedocument') IS NULL THEN
        RETURN;
    END IF;

    SELECT COALESCE(MAX(id), 0) INTO max_id FROM public.d_patient_freedocument;
    IF max_id = 0 THEN
        PERFORM setval('public.d_patient_freedocument_seq', 1, false);
    ELSE
        PERFORM setval('public.d_patient_freedocument_seq', max_id, true);
    END IF;
END$$;

DO $$
DECLARE
    rec RECORD;
    new_def TEXT;
BEGIN
    FOR rec IN
        SELECT c.conname,
               src.relname AS src_table,
               ref.relname AS ref_table,
               pg_get_constraintdef(c.oid) AS condef
        FROM pg_constraint c
        JOIN pg_class src ON src.oid = c.conrelid
        JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
        JOIN pg_class ref ON ref.oid = c.confrelid
        JOIN pg_namespace ref_ns ON ref_ns.oid = ref.relnamespace
        WHERE c.contype = 'f'
          AND src_ns.nspname = 'opendolphin'
          AND ref_ns.nspname = 'public'
        ORDER BY src.relname, c.conname
    LOOP
        IF to_regclass(format('opendolphin.%I', rec.ref_table)) IS NULL THEN
            RAISE NOTICE 'skip constraint %, referenced table opendolphin.% is missing', rec.conname, rec.ref_table;
            CONTINUE;
        END IF;

        new_def := replace(rec.condef, 'REFERENCES public.', 'REFERENCES opendolphin.');
        IF new_def = rec.condef THEN
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE opendolphin.%I DROP CONSTRAINT IF EXISTS %I', rec.src_table, rec.conname);

        IF position('NOT VALID' in upper(new_def)) > 0 THEN
            EXECUTE format('ALTER TABLE opendolphin.%I ADD CONSTRAINT %I %s', rec.src_table, rec.conname, new_def);
        ELSE
            EXECUTE format('ALTER TABLE opendolphin.%I ADD CONSTRAINT %I %s NOT VALID', rec.src_table, rec.conname, new_def);
        END IF;
    END LOOP;
END$$;
