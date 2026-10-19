# Milestone evidence

## M0 — validated 18 October 2026

- Folder contained only the supplied plan; GitHub repository had no refs. Initialized `feature/local-first-mvp` without overwriting the plan.
- Java 21.0.10, Maven 3.9.14, Node 24.14.0, Docker Desktop Linux engine available. All proposed ports free; unrelated Calendra PostgreSQL on 5432 preserved.
- Registered authorized dedicated runner `transfervio-local-windows`; observed online with labels `self-hosted`, `Windows`, `X64`, `transfervio-local`. Workflow is manual-only. GitHub workflow itself has not run.
- Spring Boot 3.5.16 / React / Vite build passed. Six runtime-safety unit tests passed.
- `/actuator/health` UP with PostgreSQL and private MinIO checks. Browser application served HTTP 200.
- Full stop/start retained the original database checkpoint. PostgreSQL backup created locally.
- Source hygiene check passed; `.env.local`, `.runner`, `.local-data` ignored. External AI and destination writes disabled. Cloud scaffold only.
- MinIO upstream Docker image unavailable: built official pinned source release `RELEASE.2025-10-15T17-29-55Z` locally.

## M1 — partner and invitation isolation validated

M1–M6 implementation and validation completed during 18–19 October 2026. External acceptance exceptions are called out below.

- Two synthetic partners have independent accounts, hashed API keys, branding, tenants, locations and catalogs. Tenant/property upsert, read and guarded admin delete are implemented.
- Thirty-two HTTP assertions cover identical external tenant IDs across partners, cross-partner reads/deletes, property substitution, expiry bounds, token revocation, API-key revocation/admin restrictions and deletion with history. Database tests separately prove expired invitations and composite foreign keys.
- Public invitation scope is server-bound; forged query parameters and cross-invitation source downloads cannot change it. Branding supports safe embedded PNG/JPEG logos and configured color/support text.

## M2 — structured imports and local originals validated

- CSV (Slovenian headings), XLSX (English headings) and JSON converge to canonical clients and appointments, deduplicate across files, retain provenance and resolve client/service/staff relationships.
- Fifty-three HTTP assertions pass across five-client/three-appointment acceptance and a separate 500-client/200-appointment migration, which retains stable IDs after reprocessing and exports with pagination and correct client links.
- Twenty-eight HTTP boundary assertions cover empty/malformed/spoofed/oversized files, the 20-file quota, duplicate upload at quota, scope/origin rejection, immutable phases and deletion. XLSX formulas are never evaluated; parsing limits and macros are enforced.
- Original downloads match uploaded bytes. Seven-day retention removes MinIO bytes while retaining provenance metadata. Explicit migration deletion removes local originals, records, snapshots and mock destination rows.

## M3 — mock image extraction and validation validated

- Twenty labeled synthetic PNG fixtures cover directories, appointment lists and cropped calendar cards. All 524 expected canonical fields match the fixture contract. This is deterministic fixture lookup, **not measured real OCR accuracy**.
- Ambiguous dates, missing duration, DST gaps/folds, unknown destination IDs, overlapping appointments and forbidden entity types block approval until corrected or excluded. Source acknowledgment cannot bypass invitation entity restrictions.
- The optional strict-schema OpenAI adapter, usage/cost records and restart cache exist. It is disabled by default and additionally limited to recognized synthetic inputs. No key or paid call was used; live extraction behavior remains unverified.

## M4 — approval, delivery and events validated

- Revision-checked review produces a database-immutable snapshot and per-record idempotency keys. Approval sets READY; successful destination receipts alone set IMPORTED.
- Partial mock delivery, retry and repeated delivery create zero additional destination records. Clients are delivered before dependent appointments, and confirmed receipts cannot be downgraded.
- Signed webhook test deliberately returns 503, then accepts a retry. HMAC/body/timestamp checks, persistent replay detection, local-target allowlisting and four rejected SSRF targets pass. Polling/export client and generated OpenAPI are provided.

## M5 — local Calendra contract gate validated; real API gate pending

- Read-only review of the existing Calendra repository established authentication, tenant permissions, DTOs and potentially significant booking side effects. No Calendra code or database was changed.
- Local contract dry-run reports five clients and three appointments; all mock imports and retries use synthetic data. Remote writes are rejected by runtime configuration and the smoke script.
- Actual non-production CRM/calendar verification is **not complete**. It requires an approved scoped machine API, synthetic tenant/property and proven suppression of notifications, entitlements and billing. See `calendra-integration.md`; no browser credential or production endpoint was used.

## M6 — local operational validation and documentation

- Complete host acceptance suite: **25 Java tests, zero failures/errors/skips**, HTTP suites above, signed events, React production build, and one full desktop/mobile browser flow. Browser flow covers invitation → CSV/screenshots → correction → approval → nine mock receipts, with no JavaScript errors or horizontal overflow at 390 px.
- Stop/start verification preserved the same approved exports, delivery receipts and byte-identical original file. A fresh PostgreSQL backup was saved locally. Destructive reset was deliberately not executed; restore remains a documented procedure rather than a tested recovery certification.
- Cloud profile fails before datasource initialization when mandatory cloud settings are absent. Cloud deployment, AWS provisioning and production operation have not been tested or performed.
- Local container bundle builds the same application and serves health UP through Nginx on loopback 5175; API/PostgreSQL/MinIO have no published host ports and stay on an internal-only network. Nginx has a separate ingress bridge. The complete browser flow also passed against this separately seeded bundle (14.7 seconds), confirming the built frontend, proxy, authorization, upload, worker, review and delivery path.
- Source hygiene checks exclude credentials, local data, runner state and generated build output. Only manual workflows using the verified self-hosted labels exist; no GitHub-hosted job or deployment exists. Dedicated runner registered with user authorization; remote Actions execution has not been performed.
- Initial build dependency-prefetch contacted a public legacy S3 Maven endpoint, with no application data or resource provisioning. It was removed and all Maven resolution restricted to Maven Central. Default application paths remain local; test policy assertions are not presented as packet-capture evidence.

## Review boundary

All implementation is on `feature/local-first-mvp`. M0 was committed separately as `1194618`. Work is local: no push, merge, AWS deployment, external AI processing or actual Calendra import. Local setup, profiles, architecture, privacy, API contract, synthetic fixtures and Calendra follow-up requirements are documented. The supplied original logo was absent, so the UI uses a code-based placeholder.
