CREATE TABLE partners (
 id uuid PRIMARY KEY, name text NOT NULL, brand_name text NOT NULL,
 primary_color text NOT NULL DEFAULT '#16755b', support_text text NOT NULL DEFAULT 'We are here to help you move.',
 logo_data text NOT NULL DEFAULT '', active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE partner_users (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL REFERENCES partners(id), email text UNIQUE NOT NULL,
 password_hash text NOT NULL, active boolean NOT NULL DEFAULT true
);
CREATE TABLE credentials (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL REFERENCES partners(id), token_hash text UNIQUE NOT NULL,
 label text NOT NULL, kind text NOT NULL CHECK(kind IN ('SESSION','API_KEY')), revoked boolean NOT NULL DEFAULT false,
 expires_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE tenants (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL REFERENCES partners(id), external_tenant_id text NOT NULL,
 name text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(partner_id,external_tenant_id), UNIQUE(id,partner_id)
);
CREATE TABLE properties (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, partner_id uuid NOT NULL, external_property_id text NOT NULL,
 name text NOT NULL, timezone text NOT NULL,
 FOREIGN KEY(tenant_id,partner_id) REFERENCES tenants(id,partner_id) ON DELETE CASCADE,
 UNIQUE(tenant_id,external_property_id), UNIQUE(id,tenant_id,partner_id)
);
CREATE TABLE catalog_entries (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL, tenant_id uuid NOT NULL, property_id uuid NOT NULL,
 kind text NOT NULL CHECK(kind IN ('SERVICE','EMPLOYEE')), external_id text NOT NULL, name text NOT NULL,
 FOREIGN KEY(property_id,tenant_id,partner_id) REFERENCES properties(id,tenant_id,partner_id) ON DELETE CASCADE,
 UNIQUE(property_id,kind,external_id)
);
CREATE TABLE migrations (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL, tenant_id uuid NOT NULL, property_id uuid,
 status text NOT NULL DEFAULT 'UPLOADING' CHECK(status IN ('UPLOADING','PROCESSING','REVIEW','READY','PARTIAL','IMPORTED','FAILED')),
 revision integer NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), approved_at timestamptz,
 FOREIGN KEY(tenant_id,partner_id) REFERENCES tenants(id,partner_id),
 FOREIGN KEY(property_id,tenant_id,partner_id) REFERENCES properties(id,tenant_id,partner_id),
 UNIQUE(id,partner_id,tenant_id)
);
CREATE TABLE invitations (
 id uuid PRIMARY KEY, migration_id uuid NOT NULL UNIQUE REFERENCES migrations(id) ON DELETE CASCADE,
 token_hash text NOT NULL UNIQUE, allowed_entities text[] NOT NULL,
 expires_at timestamptz NOT NULL, revoked boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE audit_events (
 id bigserial PRIMARY KEY, partner_id uuid NOT NULL REFERENCES partners(id), migration_id uuid,
 action text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX migrations_partner_created ON migrations(partner_id,created_at DESC);
