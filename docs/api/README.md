# Partner integration contract

`openapi.yaml` is generated from the running local application. `/v1/*` requires `Authorization: Bearer <partner API key or session>`. `POST /auth/login` returns an eight-hour admin session. Browser invitation routes require the opaque token in the path and grant access to only the bound migration.

1. Register/upsert a tenant, then its properties with IANA timezones.
2. Synchronize each property's SERVICE and EMPLOYEE catalog entries.
3. Create an expiring invitation. Save its one-time `upload_url` securely.
4. The invite holder uploads, processes, reviews, corrects and explicitly approves.
5. Poll `/v1/events` or use a signed local webhook. `migration.ready` contains only IDs, a snapshot digest and an export reference.
6. Fetch `/v1/migrations/{id}/export?entity=clients&cursor=0&limit=100`, following `next_cursor` until null; then fetch appointments.
7. Import clients before appointments using each record's stable idempotency key. Return per-record delivery results; only confirmed results count as imported.

`packages/api-client-example/client.mjs` provides a small local-only polling/export client. `webhook.mjs` demonstrates signature and replay checks. No inbound internet tunnel is required.

Tenant/property POST operations upsert by scoped external ID. Admin sessions can DELETE an empty tenant or property; a 409 prevents deletion while migration history references it. Explicit migration deletion is available separately and removes its locally stored originals, records, snapshots and mock destination rows. Original downloads remain scoped to the invitation and return 410 after retention expiry. See the generated OpenAPI document for exact routes.

Canonical export records include `stable_record_id`, `entity`, `data`, `provenance`, `approved`, and `idempotency_key`. The envelope carries the immutable partner, tenant and optional property scope. Clients include source identity, names and optional contact fields; appointments reference the client's stable ID, start with UTC offset, timezone, duration, status and mapped destination IDs. Empty values remain explicit for human review and cannot silently become destination defaults.

Receipt example (all values illustrative):

```json
{"results":[{"record_id":"00000000-0000-0000-0000-000000000001","outcome":"IMPORTED","destination_id":"test-client-42","error_code":null}]}
```

Outcomes: `IMPORTED`, `ALREADY_EXISTS`, `UPDATED`, `FAILED`. Success requires a destination ID. The sender is an authenticated trusted partner; Transfervio records its acknowledgment rather than inventing destination success.

Webhook signing input: `timestamp + "." + eventId + "." + exactBody`, HMAC-SHA256 using the one-time subscription secret. Header `X-Transfervio-Signature` is `sha256=<hex>`. Header timestamps are Unix seconds. Persist processed event IDs with the consumer's effect; return success on an already completed event.
