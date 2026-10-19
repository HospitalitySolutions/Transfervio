# Security and privacy boundaries

## Local defaults

All uploads, normalized records, approval snapshots, destination receipts, job state, credentials, logs and backups stay on the development computer. PostgreSQL is a separate Docker volume; MinIO uses private project-local objects. Runtime endpoints bind to loopback. In the optional container bundle, the API and data services use only an internal Docker network with no outbound route. Nginx additionally joins an ingress bridge for Docker Desktop to publish its loopback web port; that bridge permits outbound traffic, but Nginx only serves static assets and proxies the internal API. No application service uses a remote destination by default. This topology follows [Docker's frontend/internal-network pattern](https://docs.docker.com/engine/network/).

Installation downloads public software dependencies. An initial container dependency-prefetch attempt contacted a legacy public S3-hosted Maven repository; it sent no application data and provisioned no resources. That prefetch step was removed and Java dependency resolution is now explicitly restricted to Maven Central. Do not confuse dependency download traffic with application storage or processing.

## Credentials and authorization

- Local bootstrap creates two synthetic partner administrator accounts. There is no public signup or password-reset email flow in this MVP.
- Passwords use BCrypt. Random 256-bit API/session/invitation tokens are stored as SHA-256 hashes. Sessions expire after eight hours; API keys after 90 days. Keys and invitation links are returned once.
- Partner routes require bearer authentication. Admin-only operations reject API keys. Session credentials stay in browser session storage, never URLs or frontend bundles.
- Invitation scope resolves exclusively on the server. Database foreign keys enforce partner → tenant → property associations. Query strings cannot select a different destination. Expired and revoked invitations reject preview, upload, original download, edit and approval.
- Public/auth routes are rate limited per socket address with bounded time-window cleanup. No forwarded client IP is trusted. Public browser origins must match the configured local web origin.

## Files and review

Only CSV, XLSX, JSON, PNG and JPG are accepted; magic bytes, UTF-8 text, file size, image dimensions, spreadsheet ZIP expansion, row/column/cell limits and checksums are checked. Twenty unique files per migration and 10 MB per file. Spreadsheet formulas are never evaluated and macros are rejected. Originals are downloaded as attachments through the scoped invitation, never public bucket URLs.

Dates without a clear year/time or with slash ambiguity remain unresolved. Property timezone can supply known context; DST gaps and repeated hours require a valid explicit offset. Client linking uses source IDs/email/phone, not guessed names. Catalog matches must resolve to the invited property. Duplicate or conflicting records and employee overlaps require review. A source-review acknowledgment cannot bypass allowed entity types or ordinary validation.

Approval checks the review revision, validates again, and stores an immutable snapshot. Database triggers reject snapshot updates. Uploading or becoming READY never imports data. Successful receipts require destination IDs; dependent appointments require acknowledged clients. Confirmed success cannot be downgraded on retries.

## Optional external AI

Default `AI_PROVIDER=mock`, `EXTERNAL_AI_ENABLED=false`. Mock extraction recognizes only the committed synthetic image checksums; unknown screenshots become an explicit review issue.

Normal `start-local.ps1` overrides AI settings in the child process to mock without erasing optional credentials in `.env.local`. External processing requires `AI_PROVIDER=openai`, `EXTERNAL_AI_ENABLED=true`, an explicit `OPENAI_MODEL`, a local `OPENAI_API_KEY`, and one launch flag: `-EnableSyntheticAi` restricts processing to checksums in the committed synthetic manifest; `-EnableExternalAi` permits previously unseen files. Neither flag grants file consent. The invitation holder must explicitly authorize each upload checksum before image extraction or a requested column-mapping suggestion. Uploading, listing mappings, and granting consent alone make no AI call. Expired/revoked invitations and revoked file permission prevent subsequent processing, including cache retrieval. General external mode must only be used with appropriately authorized data.

Authorized image bytes or bounded structured samples leave the computer for processing and may incur charges. Mapping inference sends at most 12 representative rows, 120 characters per value, within a 16,000-character sample budget; approved mappings then apply locally to every row. Strict structured outputs are validated locally; suggestions need confirmation, and extracted image records need human source review. `store:false` is requested; it is not a guarantee about every provider retention policy. Token counts remain local; cost estimates are unknown unless operator-supplied per-million-token rates are set. Outputs are cached per migration/checksum/model/purpose. Authentication, model, quota, rate-limit, timeout and schema errors have safe diagnostics that exclude source contents and provider response bodies.

On 19 October 2026, explicit user authorization covered two live synthetic OpenAI requests: a newly generated screenshot and an unfamiliar CSV mapping. Both passed review/export tests; a repeated extraction reused the cache. This validates that path for those samples, not general OCR accuracy. The app was restored to mock mode. No real customer data was used.

API keys can restrict export fields. Each export intersects the registry, credential policy and the immutable customer-approved sharing policy. Restricted keys cannot mint public invitations, and API keys cannot retrieve raw source review through migration status or mock delivery. Selected missing fields are null; unselected business fields are absent. Technical metadata includes opaque IDs, relationships and safe provenance only; filenames, source IDs, table names and source text are excluded. Administrators and the scoped invitation holder retain the review access needed to manage a migration.

## Events, retention and logs

Webhook targets are restricted to exactly `http://127.0.0.1:<WEBHOOK_RECEIVER_PORT>/events`; no user-chosen remote URLs, DNS names, redirects, fragments or query strings. HMAC binds timestamp, event ID and exact body. The reference receiver verifies signatures within five minutes, persists processed IDs and handles retries without repeating effects. Outbox bodies contain IDs/export references, not client PII. Delivery attempts and status remain local.

Source file retention defaults to seven days. Scheduled cleanup marks expired files, deletes bytes, and also sweeps old orphan objects from interrupted uploads. Canonical records and snapshots persist until an authenticated partner administrator deletes the migration through `DELETE /v1/migrations/{id}`. That deletion cascades metadata, snapshots, receipts, events and cached AI output; local mock records and object bytes are deleted too. Backups are deliberately outside this cascade and must be managed separately.

API access logging is disabled; application errors return generic safe messages. Never enable body/SQL parameter logging with customer data. The production-like Nginx config does not log request paths. Development logs and test artifacts are ignored by Git. Browser failure artifacts may contain entered local credentials: treat the entire `.local-data` directory as private.

## Cloud and operational limits

Cloud is a configuration scaffold only. Required values and TLS are checked before any datasource is created. Cloud provisioning, TLS/auth operations, real-data legal/privacy review, vulnerability management, availability and disaster recovery are not certified by local tests. MinIO's community distribution is archived; review its maintenance and AGPL obligations before any future deployment. The Transfervio logo is a code-based placeholder because no source logo was present.
