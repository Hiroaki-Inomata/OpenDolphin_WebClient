create table if not exists orca_prescription_orders (
    id bigserial primary key,
    facility_id varchar(64) not null,
    patient_id varchar(64) not null,
    encounter_id varchar(128),
    encounter_date date,
    perform_date date,
    payload_json jsonb not null,
    created_at timestamptz not null default now(),
    created_by varchar(128)
);

create index if not exists idx_orca_prescription_orders_patient_latest
    on orca_prescription_orders (facility_id, patient_id, created_at desc, id desc);

create index if not exists idx_orca_prescription_orders_encounter_date_latest
    on orca_prescription_orders (facility_id, patient_id, encounter_date, created_at desc, id desc);

create index if not exists idx_orca_prescription_orders_encounter_id_latest
    on orca_prescription_orders (facility_id, patient_id, encounter_id, created_at desc, id desc);
