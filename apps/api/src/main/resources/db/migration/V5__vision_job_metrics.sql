ALTER TABLE ai_usage ADD COLUMN migration_id uuid REFERENCES migrations(id) ON DELETE CASCADE;
CREATE TABLE ai_extractions (
 migration_id uuid NOT NULL REFERENCES migrations(id) ON DELETE CASCADE,
 image_sha256 text NOT NULL, model text NOT NULL, content jsonb NOT NULL,
 PRIMARY KEY(migration_id,image_sha256,model)
);
