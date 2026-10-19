CREATE TABLE uploads (
 id uuid PRIMARY KEY, migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 original_name text NOT NULL, content_type text NOT NULL, size_bytes bigint NOT NULL,
 sha256 text NOT NULL, object_key text NOT NULL UNIQUE, created_at timestamptz NOT NULL DEFAULT now(),
 deleted_at timestamptz, UNIQUE(migration_id,sha256)
);
CREATE TABLE records (
 id uuid PRIMARY KEY, migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 entity text NOT NULL CHECK(entity IN ('CLIENT','APPOINTMENT')), data jsonb NOT NULL,
 provenance jsonb NOT NULL, issues jsonb NOT NULL DEFAULT '[]', excluded boolean NOT NULL DEFAULT false,
 UNIQUE(id,migration_id)
);
CREATE INDEX records_migration_entity ON records(migration_id,entity,id);
CREATE TABLE jobs (
 id uuid PRIMARY KEY, migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 kind text NOT NULL CHECK(kind IN ('EXTRACT','DELIVER')), status text NOT NULL DEFAULT 'PENDING',
 attempts integer NOT NULL DEFAULT 0, available_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz,
 error_code text, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(migration_id,kind)
);
CREATE TABLE approved_snapshots (
 migration_id uuid PRIMARY KEY REFERENCES migrations(id) ON DELETE CASCADE,
 content jsonb NOT NULL, sha256 text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION reject_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Approved snapshots are immutable'; END $$;
CREATE TRIGGER snapshot_immutable BEFORE UPDATE ON approved_snapshots FOR EACH ROW EXECUTE FUNCTION reject_snapshot_mutation();
CREATE TABLE delivery_results (
 migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 record_id uuid NOT NULL, outcome text NOT NULL CHECK(outcome IN ('IMPORTED','ALREADY_EXISTS','UPDATED','FAILED')),
 destination_id text, error_code text, attempt integer NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(migration_id,record_id), FOREIGN KEY(record_id,migration_id) REFERENCES records(id,migration_id) ON DELETE CASCADE
);
CREATE TABLE mock_destination_records (
 id uuid PRIMARY KEY, idempotency_key text NOT NULL UNIQUE, partner_id uuid NOT NULL,
 tenant_id uuid NOT NULL, property_id uuid, entity text NOT NULL, data jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE outbox_events (
 id uuid PRIMARY KEY, migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 partner_id uuid NOT NULL, event_type text NOT NULL, payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
