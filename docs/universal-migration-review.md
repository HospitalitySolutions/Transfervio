# Universal Migration API MVP review

Implementation date: 19 October 2026. Scope: [the supplied universal migration specification](universal-migration-spec.md), extending the existing local MVP. Work is on `feature/universal-migration-api`, based on main `4a8e26d`. The earlier main push was explicitly authorized; this new implementation has not been merged, deployed or pushed to main.

## Review decisions and implementation

- Preserve the working Spring Boot/React application, partner → tenant → property isolation, invitation capabilities, durable workers, immutable approvals, stable identities, pagination, receipts and signed local events.
- Remove the Calendra runtime stub. Only generic canonical exports and the local test mock remain in the core. Destination vendors own connectors, transformations, catalog resolution, writes and side effects. Source service/staff labels may remain unresolved; explicit foreign catalog references are still rejected.
- Add an explicit CLIENT/APPOINTMENT field registry, authenticated `/v1/export-schema`, per-request projections, key field policies and immutable customer sharing policies. Unknown fields return 400; unauthorized fields return 403. Selected missing fields are null; omitted fields are absent. Technical IDs and opaque client relationships remain stable. Export provenance excludes filenames, source IDs, table names and original text.
- Preserve deterministic structured parsing and add a source mapping editor for CSV, XLSX and nested JSON. Conflicting aliases require human selection. Known and unfamiliar JSON tables are retained together. Optional AI uses at most 12 bounded representative rows and never runs per row; suggestions require confirmation before deterministic application.
- Support previously unseen authorized PNG/JPEG input using strict structured output, source regions and uncertainty. External calls require runtime opt-in plus checksum-specific file consent. Fixture-only mode is retained. Safe diagnostics distinguish authentication, model, quota, rate-limit, timeout and schema failures; transient jobs retry, accepted outputs are cached and token usage stays local.
- Publish OpenAPI 3.1 for all 36 paths, 58 schemas, authentication, review, exports, errors, receipts and signed events. Provide a [vendor guide](api/README.md), [canonical schema/versioning guide](api/canonical-schema.md) and a functioning generic consumer with a local synthetic journal and idempotent retries.

The frontend retains the existing brand configuration and adds responsive source mapping, per-file AI permission, actionable extraction errors and customer field-sharing choices. The default launcher explicitly forces mock AI without erasing optional local credentials.

## Validation evidence

All automated fixtures are synthetic. Generated test records, private logs, screenshots, consumer journals and backups remain in ignored `.local-data`; PostgreSQL and MinIO remain local.

| Area | Evidence |
|---|---|
| Backend | 38 Java tests passed, zero failures/errors/skips; includes real local PostgreSQL/MinIO, consent scope/checksums/revocation/expiry, unseen JPEG transport, safe AI errors, mixed JSON tables, field policies and parser/validator coverage |
| Isolation | 32 HTTP assertions passed, including cross-partner and property scope, key/admin restrictions and revoked capabilities |
| Migration | 53 HTTP assertions passed; 5 clients/3 appointments, 500 clients/200 appointments, stable IDs, pagination, linked records, partial failure and duplicate-free retry |
| Universal API | 86 HTTP assertions passed: selected/full exports, null versus omission, unknown/forbidden fields, restricted-key bypass prevention, immutable sharing, CSV/XLSX/mixed JSON and ambiguous-column mapping, malformed receipts, generic consumer and retry |
| Boundaries | 27 HTTP assertions passed for upload size/type/quota, duplicate uploads, source access, scope, approval and delete cascade |
| Events | Signed webhook, intentional 503/retry, timestamp window, persisted replay detection and rejected remote targets passed |
| Contracts | OpenAPI 3.1 validation passed; all 36 live paths covered, field registry matched, 21 actual response shapes and the export example validated |
| Frontend | React production build passed; 2 headless browser flows passed (23.4 seconds), including review/correction/approval/mock receipts and mapping/customer-selected fields; mobile mapping screenshot inspected at 390 px |
| Persistence | Full stop/start preserved approved exports, delivery receipts and byte-identical original source; local PostgreSQL backup created |
| Git hygiene | Source and exact configured credential scans passed; private data, credentials, runner state and generated build output remain ignored |
| Runner | `transfervio-local-windows` observed online and idle with exactly the self-hosted Windows/X64/transfervio-local labels; workflow remains manual-only, no hosted fallback or deployment |

The complete acceptance run is recorded in `.local-data/logs/universal-acceptance-final.log`. After the final mixed-table parser fix, the backend, migration, universal HTTP and live contract checks were rerun. Latest machine-readable reports are `.local-data/universal-results.json`, `contract-results.json` and `live-ai-results.json`. Remote GitHub Actions execution was not performed for this unpushed feature branch; the workflow's validation commands ran locally.

## Explicitly authorized live AI acceptance

The user separately authorized synthetic live OpenAI testing. Two accepted provider responses used the locally configured `gpt-6-luna` model: one newly generated unseen screenshot and one unfamiliar CSV column-mapping suggestion. The test verified missing/revoked consent rejection, source review, canonical extraction, approval, selective export and cache reuse (26 HTTP assertions). It exported two fictional clients.

Usage recorded locally: image 932 input / 299 output tokens; mapping 1,297 input / 76 output tokens; total 2,229 input / 375 output tokens. Cost remains unknown because operator billing rates were not configured. No real customer data was used. This establishes that the live path worked for those samples, not a general OCR accuracy claim. No further live inference was needed; the app was restored to mock mode.

## Remaining boundaries

The local application is available at `http://localhost:5174`, with API `http://localhost:8091`. Local/cloud profiles remain available; the cloud profile is an unprovisioned scaffold. No AWS resources or deployment were created. The optional local container bundle was validated during the original foundation milestone; this extension's acceptance ran against the host development stack.

Saved export profiles are designed as a secondary milestone; per-request selection is implemented. No services/employees/locations or other future-entity migration logic was added. Real Calendra import remains a separately authorized, Calendra-owned connector task, requiring a scoped non-production API and synthetic test tenant/property. No Calendra code or data was changed and no production API was called. See [the integration boundary](calendra-integration.md).
