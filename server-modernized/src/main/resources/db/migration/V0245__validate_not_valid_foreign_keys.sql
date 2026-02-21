-- Validate deferred foreign keys (NOT VALID) after FK retarget/repair migrations.
-- Some environments contain orphan rows created before FK retargeting.
-- Remove orphan references first, then validate constraints.
DO $$
DECLARE
    rec RECORD;
    src_column TEXT;
    ref_column TEXT;
    removed_rows BIGINT;
BEGIN
    FOR rec IN
        SELECT con.conname AS constraint_name,
               src_ns.nspname AS src_schema,
               src.relname AS src_table,
               ref_ns.nspname AS ref_schema,
               ref.relname AS ref_table,
               con.conkey[1] AS src_attnum,
               con.confkey[1] AS ref_attnum
          FROM pg_constraint con
          JOIN pg_class src
            ON src.oid = con.conrelid
          JOIN pg_namespace src_ns
            ON src_ns.oid = src.relnamespace
          JOIN pg_class ref
            ON ref.oid = con.confrelid
          JOIN pg_namespace ref_ns
            ON ref_ns.oid = ref.relnamespace
         WHERE con.contype = 'f'
           AND src_ns.nspname = 'opendolphin'
           AND con.convalidated = false
           AND array_length(con.conkey, 1) = 1
           AND array_length(con.confkey, 1) = 1
         ORDER BY src.relname, con.conname
    LOOP
        SELECT att.attname
          INTO src_column
          FROM pg_attribute att
         WHERE att.attrelid = format('%I.%I', rec.src_schema, rec.src_table)::regclass
           AND att.attnum = rec.src_attnum;

        SELECT att.attname
          INTO ref_column
          FROM pg_attribute att
         WHERE att.attrelid = format('%I.%I', rec.ref_schema, rec.ref_table)::regclass
           AND att.attnum = rec.ref_attnum;

        EXECUTE format(
            'DELETE FROM %I.%I s ' ||
            'WHERE s.%I IS NOT NULL ' ||
            '  AND NOT EXISTS (SELECT 1 FROM %I.%I r WHERE r.%I = s.%I)',
            rec.src_schema,
            rec.src_table,
            src_column,
            rec.ref_schema,
            rec.ref_table,
            ref_column,
            src_column
        );

        GET DIAGNOSTICS removed_rows = ROW_COUNT;
        IF removed_rows > 0 THEN
            RAISE NOTICE 'Removed % orphan rows from %.% for %',
                removed_rows, rec.src_schema, rec.src_table, rec.constraint_name;
        END IF;
    END LOOP;

    FOR rec IN
        SELECT src_ns.nspname AS schema_name,
               src.relname AS table_name,
               con.conname AS constraint_name
          FROM pg_constraint con
          JOIN pg_class src
            ON src.oid = con.conrelid
          JOIN pg_namespace src_ns
            ON src_ns.oid = src.relnamespace
         WHERE con.contype = 'f'
           AND src_ns.nspname = 'opendolphin'
           AND con.convalidated = false
         ORDER BY src.relname, con.conname
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I VALIDATE CONSTRAINT %I',
            rec.schema_name,
            rec.table_name,
            rec.constraint_name
        );
    END LOOP;
END $$;
