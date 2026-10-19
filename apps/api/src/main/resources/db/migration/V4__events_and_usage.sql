CREATE TABLE ai_usage (
 id bigserial PRIMARY KEY, image_sha256 text NOT NULL, model text NOT NULL,
 input_tokens bigint NOT NULL, output_tokens bigint NOT NULL, estimated_cost_usd numeric(12,6),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE webhook_subscriptions (
 id uuid PRIMARY KEY, partner_id uuid NOT NULL REFERENCES partners(id), target_url text NOT NULL,
 active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE webhook_deliveries (
 event_id uuid NOT NULL REFERENCES outbox_events(id) ON DELETE CASCADE,
 subscription_id uuid NOT NULL REFERENCES webhook_subscriptions(id) ON DELETE CASCADE,
 status text NOT NULL DEFAULT 'PENDING', attempts integer NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz, last_http_status integer,
 PRIMARY KEY(event_id,subscription_id)
);
