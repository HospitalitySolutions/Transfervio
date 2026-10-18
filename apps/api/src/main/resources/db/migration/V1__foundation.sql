CREATE TABLE runtime_checkpoint (
  id integer PRIMARY KEY CHECK (id = 1),
  first_started_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO runtime_checkpoint(id) VALUES (1);
