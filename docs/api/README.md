# Vendor integration guide

The live OpenAPI 3.1 contract is at `http://localhost:8091/v3/api-docs` (JSON), `/v3/api-docs.yaml` (YAML), and `/swagger-ui/index.html` (interactive). The checked-in [contract](openapi.yaml) describes every implemented route. `/v1/*` uses `Authorization: Bearer <partner API key or administrator session>`. Obtain keys in the local partner dashboard; keep them on the vendor server. Invitation URLs are separate, narrowly scoped capabilities and must not be logged or embedded in analytics.

## End-to-end workflow

1. Upsert `/v1/tenants` by your partner-scoped `external_tenant_id`.
2. Upsert `/v1/tenants/{id}/properties` with an IANA timezone. Optionally synchronize SERVICE/EMPLOYEE catalogs; these are references, not destination adapters.
3. POST `/v1/invitations` with `tenant_id`, optional `property_id`, `allowed_entities`, `expires_in_hours`, and optional `export_fields`. Appointments require a property. Save the returned `upload_url` securely; the token is shown once. A field-restricted key cannot create a public invitation because that would mint access to full source review.
4. The customer uploads, confirms unfamiliar source mappings, reviews/corrects records and approves. They may narrow the invitation's sharing fields. AI use requires separate explicit consent.
5. Poll `/v1/events?cursor=0` or receive a signed local `migration.ready` event. Events contain IDs, an export reference and snapshot digest. Polling returns at most 100 events; advance the offset by the returned count and persist event IDs for deduplication.
6. Discover `/v1/export-schema?entity=clients`, then retrieve clients and appointments from the immutable approved snapshot. Follow `next_cursor` until null for each entity.
7. Import clients before linked appointments. Map fields, source service/staff labels and property IDs into your own data model. Your connector owns business validation, duplicate policy, side effects and database transactions.
8. Return verified per-record receipts to `/v1/migrations/{id}/delivery-results`. Export alone never changes a migration to IMPORTED.

Example invitation (replace IDs with owned resources):

```json
{"tenant_id":"00000000-0000-0000-0000-000000000001","property_id":"00000000-0000-0000-0000-000000000002","allowed_entities":["CLIENT","APPOINTMENT"],"expires_in_hours":24,"export_fields":{"clients":["first_name","last_name","email"],"appointments":["start","duration_minutes","status","source_service"]}}
```

## Field selection and stable identities

```http
GET /v1/export-schema?entity=clients
GET /v1/migrations/{id}/export?entity=clients&fields=first_name,last_name,email&cursor=0&limit=100
GET /v1/migrations/{id}/export?entity=appointments&fields=start,duration_minutes,status,service_id,employee_id
```

Omitting `fields` returns the complete authorized canonical business data present in the snapshot. A supplied nonempty list returns exactly those business keys. Selected absent/empty values become JSON null; unselected keys are omitted. Unknown or wrong-entity fields return 400; known fields outside the intersection of key permissions and approved customer sharing return 403. Discovery reports credential-authorized fields but does not grant access to any customer migration. An omitted entity in an `export_fields` policy means all its registry fields; `[]` means no business fields for that entity.

Every record retains `stable_record_id`, `entity`, `approved`, `idempotency_key`, `relationships` and sanitized `provenance`. Appointments always retain `relationships.client_record_id`, an opaque stable reference to a client in the same approved snapshot, even when the business field `client_record_id` is not selected. Source identifiers, filenames, table names and raw text are not technical metadata. Full and selected pages retain identical stable identities and never mutate the snapshot. Cursor is a nonnegative integer offset; limit is 1–200. A cursor outside the snapshot returns 400.

The API-key status response contains migration state, jobs and receipts. Detailed source review, mock preview and mock delivery require an administrator session or the appropriate invitation capability. A key's field restriction applies to data exports and cannot be bypassed through those review routes.

See [canonical schema](canonical-schema.md) for field semantics, relationships and compatibility.

## Reliable receipts and retries

```json
{"results":[{"record_id":"00000000-0000-0000-0000-000000000003","outcome":"IMPORTED","destination_id":"synthetic-client-42","error_code":null}]}
```

Send 1–1000 results per request. Outcomes are `IMPORTED`, `ALREADY_EXISTS`, `UPDATED` or `FAILED`. Success requires a destination ID; acknowledge clients before dependent appointments. Transfervio trusts the authenticated vendor's verified assertion; it cannot inspect the vendor database. Partial acknowledgments keep the migration PARTIAL. All included approved records must have successful receipts before IMPORTED.

Persist each idempotency key and destination ID atomically with the imported effect. After a crash, reuse that identity and acknowledge `ALREADY_EXISTS`. Repeated success is safe; a later failure cannot downgrade success. A conflicting destination ID returns 409. Unknown record IDs, missing outcomes and malformed receipt batches return 400. Treat 400/403 as configuration or permission errors, 409 as a state conflict, and retry network/temporary failures with bounded backoff. Do not automatically invent destination defaults for null values.

## Working reference consumer

`packages/api-client-example/client.mjs` implements discovery, event polling, paginated selected exports and acknowledgments. `consumer.mjs` is vendor-owned example logic with a local synthetic JSON journal; it calls no vendor API. It imports clients first, preserves relationships and writes its journal before acknowledging. A second run reuses destination identities and creates zero new rows.

Set `TRANSFERVIO_API_KEY` and `MIGRATION_ID` in your local process environment, using a disposable key and a synthetic approved migration. Do not paste secrets into source control or command-line arguments.

```powershell
node packages/api-client-example/consumer.mjs
# Default: preview counts, no destination writes or acknowledgments.
node packages/api-client-example/consumer.mjs --execute-synthetic-local
# Explicit test: .local-data/vendor-example.json, then receipts to the local API.
```

Library usage supports `consume(client, migration, localJournal, {execute: true, fields: {clients: ['first_name', 'email'], appointments: ['start', 'status']}})`. The consumer still resolves client references through technical relationships. In a real connector replace the journal with your own transactional persistence and idempotency constraints.

## Signed local webhooks

Create a subscription with an administrator session. Targets must equal the configured loopback receiver, `http://127.0.0.1:<WEBHOOK_RECEIVER_PORT>/events`; remote URLs and redirects are rejected. `webhook.mjs` provides a local receiver with signature and replay checks.

Verify HMAC-SHA256 over `timestamp + "." + eventId + "." + exactBody`, using the subscription secret. Headers are `X-Transfervio-Timestamp`, `X-Transfervio-Event`, and `X-Transfervio-Signature: sha256=<hex>`. Enforce a five-minute timestamp window. Persist processed event IDs with the effect, and return 2xx for already completed events. The outbox retries failures up to five attempts with backoff; poll events to recover missed delivery.

## Contract maintenance

`node scripts/build-api-contract.mjs` refreshes the response/security documentation from the live route inventory and field registry. Rebuild/restart the API, then save `/v3/api-docs.yaml` to `docs/api/openapi.yaml`. `packages/contract-tests/validate.mjs` validates the committed OpenAPI without local credentials; `test.mjs` additionally verifies live route coverage, discovery consistency and actual response shapes. The complete local suite runs both application and contract acceptance checks.
