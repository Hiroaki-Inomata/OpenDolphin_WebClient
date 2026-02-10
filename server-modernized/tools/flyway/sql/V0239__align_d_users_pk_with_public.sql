-- Align opendolphin.d_users primary keys with public.d_users when the same userid exists in both schemas.
--
-- Some seeded/dev schemas contain duplicate user rows across schemas with different PK values (e.g. admin user).
-- This breaks JPA joins because RoleModel.c_user does not match UserModel.id.
--
-- Strategy:
-- 1) Detect userid rows present in both schemas where opendolphin.id != public.id
-- 2) Temporarily drop FK constraints that reference opendolphin.d_users(id)
-- 3) For each mismatch, rewrite FK columns + known user-pk columns to the public-side id
-- 4) Update opendolphin.d_users.id to the public-side id (when safe)
-- 5) Restore FK constraints
-- 6) Align opendolphin.d_users_seq to the new max(id)

CREATE TEMP TABLE tmp_align_d_users_fk (
    conname TEXT NOT NULL,
    table_fqn TEXT NOT NULL,
    colname TEXT,
    condef TEXT NOT NULL,
    convalidated BOOLEAN NOT NULL,
    col_count INT NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    rec RECORD;
    fk RECORD;
    mismatches INT := 0;
    max_id BIGINT;
    seq_last BIGINT;
BEGIN
    IF to_regclass('opendolphin.d_users') IS NULL OR to_regclass('public.d_users') IS NULL THEN
        RAISE NOTICE 'd_users tables missing, skipping pk alignment';
        RETURN;
    END IF;

    SELECT COUNT(*) INTO mismatches
    FROM opendolphin.d_users o
    JOIN public.d_users p ON p.userid = o.userid
    WHERE o.id <> p.id;

    IF mismatches = 0 THEN
        RAISE NOTICE 'No mismatched d_users ids detected, skipping pk alignment';
        RETURN;
    END IF;

    -- Capture all FK constraints referencing opendolphin.d_users so we can drop and restore them.
    INSERT INTO tmp_align_d_users_fk (conname, table_fqn, colname, condef, convalidated, col_count)
    SELECT
        c.conname,
        format('%I.%I', n.nspname, cl.relname) AS table_fqn,
        CASE WHEN array_length(c.conkey, 1) = 1 THEN a.attname ELSE NULL END AS colname,
        replace(pg_get_constraintdef(c.oid), 'REFERENCES d_users', 'REFERENCES opendolphin.d_users') AS condef,
        c.convalidated,
        COALESCE(array_length(c.conkey, 1), 0) AS col_count
    FROM pg_constraint c
    JOIN pg_class cl ON cl.oid = c.conrelid
    JOIN pg_namespace n ON n.oid = cl.relnamespace
    LEFT JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
    WHERE c.contype = 'f'
      AND c.confrelid = 'opendolphin.d_users'::regclass;

    FOR fk IN
        SELECT * FROM tmp_align_d_users_fk ORDER BY table_fqn, conname
    LOOP
        EXECUTE 'ALTER TABLE ' || fk.table_fqn || ' DROP CONSTRAINT ' || quote_ident(fk.conname);
    END LOOP;

    FOR rec IN
        SELECT o.userid AS userid, o.id AS op_id, p.id AS pub_id
        FROM opendolphin.d_users o
        JOIN public.d_users p ON p.userid = o.userid
        WHERE o.id <> p.id
        ORDER BY o.userid
    LOOP
        -- Safety: avoid clobbering an unrelated user row.
        IF EXISTS (SELECT 1 FROM opendolphin.d_users u2 WHERE u2.id = rec.pub_id) THEN
            RAISE NOTICE 'Skip aligning userId=%: target id=% already exists in opendolphin.d_users', rec.userid, rec.pub_id;
            CONTINUE;
        END IF;

        -- Rewrite FK columns that reference opendolphin.d_users(id).
        FOR fk IN
            SELECT * FROM tmp_align_d_users_fk ORDER BY table_fqn, conname
        LOOP
            IF fk.col_count <> 1 OR fk.colname IS NULL THEN
                RAISE NOTICE 'Skip updating FK target table % constraint % (multi-column FK)', fk.table_fqn, fk.conname;
                CONTINUE;
            END IF;
            EXECUTE 'UPDATE ' || fk.table_fqn
                || ' SET ' || quote_ident(fk.colname) || ' = $1'
                || ' WHERE ' || quote_ident(fk.colname) || ' = $2'
                USING rec.pub_id, rec.op_id;
        END LOOP;

        -- Update opendolphin-side tables that store user PK (if present) but do not necessarily have FKs.
        IF to_regclass('opendolphin.d_factor2_credential') IS NOT NULL THEN
            UPDATE opendolphin.d_factor2_credential
            SET user_pk = rec.pub_id
            WHERE user_pk = rec.op_id;
        END IF;

        IF to_regclass('opendolphin.d_factor2_challenge') IS NOT NULL THEN
            UPDATE opendolphin.d_factor2_challenge
            SET user_pk = rec.pub_id
            WHERE user_pk = rec.op_id;
        END IF;

        IF to_regclass('opendolphin.d_factor2_device') IS NOT NULL THEN
            UPDATE opendolphin.d_factor2_device
            SET userpk = rec.pub_id
            WHERE userpk = rec.op_id;
        END IF;

        IF to_regclass('opendolphin.d_factor2_code') IS NOT NULL THEN
            UPDATE opendolphin.d_factor2_code
            SET userpk = rec.pub_id
            WHERE userpk = rec.op_id;
        END IF;

        IF to_regclass('opendolphin.d_factor2_backupkey') IS NOT NULL THEN
            UPDATE opendolphin.d_factor2_backupkey
            SET user_pk = rec.pub_id
            WHERE user_pk = rec.op_id;
            UPDATE opendolphin.d_factor2_backupkey
            SET userpk = rec.pub_id
            WHERE userpk = rec.op_id;
        END IF;

        -- In case role rows were copied with the old PK, align them too.
        IF to_regclass('opendolphin.d_roles') IS NOT NULL THEN
            UPDATE opendolphin.d_roles
            SET c_user = rec.pub_id
            WHERE user_id = rec.userid AND c_user = rec.op_id;
        END IF;

        UPDATE opendolphin.d_users
        SET id = rec.pub_id
        WHERE id = rec.op_id AND userid = rec.userid;

        RAISE NOTICE 'Aligned opendolphin.d_users id % -> % for %', rec.op_id, rec.pub_id, rec.userid;
    END LOOP;

    -- Restore FK constraints.
    FOR fk IN
        SELECT * FROM tmp_align_d_users_fk ORDER BY table_fqn, conname
    LOOP
        EXECUTE 'ALTER TABLE ' || fk.table_fqn
            || ' ADD CONSTRAINT ' || quote_ident(fk.conname) || ' ' || fk.condef
            || CASE WHEN fk.convalidated THEN '' ELSE ' NOT VALID' END;
    END LOOP;

    -- Align sequence for future inserts (IDs may have grown to match public.d_users).
    IF to_regclass('opendolphin.d_users_seq') IS NULL THEN
        RAISE NOTICE 'sequence opendolphin.d_users_seq not found, skipping sequence alignment';
    ELSE
        SELECT COALESCE(MAX(id), 0) INTO max_id FROM opendolphin.d_users;
        SELECT last_value INTO seq_last FROM opendolphin.d_users_seq;
        PERFORM setval('opendolphin.d_users_seq', GREATEST(max_id, seq_last), true);
    END IF;
END $$;
