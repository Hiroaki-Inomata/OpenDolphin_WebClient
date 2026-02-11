-- Retarget opendolphin.* foreign keys that still reference public.*.
-- Build FK definitions from pg_constraint metadata to avoid pg_get_constraintdef
-- output differences caused by search_path.

DO $$
DECLARE
    rec RECORD;
    src_cols TEXT;
    ref_cols TEXT;
    fk_sql TEXT;
BEGIN
    FOR rec IN
        SELECT c.conname,
               src.relname AS src_table,
               ref.relname AS ref_table,
               c.conrelid,
               c.confrelid,
               c.conkey,
               c.confkey,
               c.confmatchtype,
               c.confupdtype,
               c.confdeltype,
               c.condeferrable,
               c.condeferred
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

        SELECT string_agg(format('%I', a.attname), ', ' ORDER BY cols.ord)
          INTO src_cols
        FROM unnest(rec.conkey) WITH ORDINALITY AS cols(attnum, ord)
        JOIN pg_attribute a ON a.attrelid = rec.conrelid AND a.attnum = cols.attnum;

        SELECT string_agg(format('%I', a.attname), ', ' ORDER BY cols.ord)
          INTO ref_cols
        FROM unnest(rec.confkey) WITH ORDINALITY AS cols(attnum, ord)
        JOIN pg_attribute a ON a.attrelid = rec.confrelid AND a.attnum = cols.attnum;

        fk_sql := format(
            'FOREIGN KEY (%s) REFERENCES opendolphin.%I(%s)',
            src_cols,
            rec.ref_table,
            ref_cols
        );

        CASE rec.confmatchtype
            WHEN 'f' THEN fk_sql := fk_sql || ' MATCH FULL';
            WHEN 'p' THEN fk_sql := fk_sql || ' MATCH PARTIAL';
            ELSE NULL;
        END CASE;

        fk_sql := fk_sql || CASE rec.confupdtype
            WHEN 'r' THEN ' ON UPDATE RESTRICT'
            WHEN 'c' THEN ' ON UPDATE CASCADE'
            WHEN 'n' THEN ' ON UPDATE SET NULL'
            WHEN 'd' THEN ' ON UPDATE SET DEFAULT'
            ELSE ' ON UPDATE NO ACTION'
        END;

        fk_sql := fk_sql || CASE rec.confdeltype
            WHEN 'r' THEN ' ON DELETE RESTRICT'
            WHEN 'c' THEN ' ON DELETE CASCADE'
            WHEN 'n' THEN ' ON DELETE SET NULL'
            WHEN 'd' THEN ' ON DELETE SET DEFAULT'
            ELSE ' ON DELETE NO ACTION'
        END;

        IF rec.condeferrable THEN
            fk_sql := fk_sql || ' DEFERRABLE';
            IF rec.condeferred THEN
                fk_sql := fk_sql || ' INITIALLY DEFERRED';
            ELSE
                fk_sql := fk_sql || ' INITIALLY IMMEDIATE';
            END IF;
        ELSE
            fk_sql := fk_sql || ' NOT DEFERRABLE';
        END IF;

        EXECUTE format('ALTER TABLE opendolphin.%I DROP CONSTRAINT IF EXISTS %I', rec.src_table, rec.conname);
        EXECUTE format('ALTER TABLE opendolphin.%I ADD CONSTRAINT %I %s NOT VALID', rec.src_table, rec.conname, fk_sql);
    END LOOP;
END$$;
