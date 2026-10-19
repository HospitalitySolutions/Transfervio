ALTER TABLE credentials ADD COLUMN export_fields jsonb NOT NULL DEFAULT '{}';
ALTER TABLE migrations ADD COLUMN export_fields jsonb NOT NULL DEFAULT '{}';
CREATE TABLE source_mappings (
 upload_id uuid NOT NULL REFERENCES uploads(id) ON DELETE CASCADE,
 table_name text NOT NULL, entity text NOT NULL CHECK(entity IN ('CLIENT','APPOINTMENT')),
 columns jsonb NOT NULL, field_map jsonb NOT NULL, approved boolean NOT NULL DEFAULT false,
 suggested_by text NOT NULL DEFAULT 'deterministic', updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(upload_id,table_name)
);
CREATE TABLE ai_authorizations (
 upload_id uuid PRIMARY KEY REFERENCES uploads(id) ON DELETE CASCADE,
 sha256 text NOT NULL, synthetic boolean NOT NULL, consented_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE ai_extractions ADD COLUMN purpose text NOT NULL DEFAULT 'image';
ALTER TABLE ai_extractions DROP CONSTRAINT ai_extractions_pkey;
ALTER TABLE ai_extractions ADD PRIMARY KEY(migration_id,image_sha256,model,purpose);
ALTER TABLE ai_usage ADD COLUMN purpose text NOT NULL DEFAULT 'image';
ALTER TABLE jobs ADD COLUMN error_message text;
