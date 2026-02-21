-- Align d_users_seq with seeded d_users.id values.
-- Seed scripts may insert IDs explicitly, leaving the sequence behind and breaking @GeneratedValue inserts.
DO $$
DECLARE
    max_id BIGINT;
    seq_last BIGINT;
BEGIN
    IF to_regclass('opendolphin.d_users_seq') IS NULL OR to_regclass('opendolphin.d_users') IS NULL THEN
        RAISE NOTICE 'sequence/table opendolphin.d_users_seq/opendolphin.d_users not found, skipping';
    ELSE
        SELECT COALESCE(MAX(id), 0) INTO max_id FROM opendolphin.d_users;
        SELECT last_value INTO seq_last FROM opendolphin.d_users_seq;
        PERFORM setval('opendolphin.d_users_seq', GREATEST(max_id, seq_last), true);
    END IF;
END $$;

-- Align d_roles_seq with seeded d_roles.id values for the same reason.
DO $$
DECLARE
    max_id BIGINT;
    seq_last BIGINT;
BEGIN
    IF to_regclass('opendolphin.d_roles_seq') IS NULL OR to_regclass('opendolphin.d_roles') IS NULL THEN
        RAISE NOTICE 'sequence/table opendolphin.d_roles_seq/opendolphin.d_roles not found, skipping';
    ELSE
        SELECT COALESCE(MAX(id), 0) INTO max_id FROM opendolphin.d_roles;
        SELECT last_value INTO seq_last FROM opendolphin.d_roles_seq;
        PERFORM setval('opendolphin.d_roles_seq', GREATEST(max_id, seq_last), true);
    END IF;
END $$;
