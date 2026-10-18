# Transfervio — Local-first MVP implementation plan for Codex

**Version:** 2.0 — local-first (supersedes any cloud-first sequencing in the previous Transfervio MVP plan)  
**Product:** Transfervio  
**GitHub:** `HospitalitySolutions/Transfervio` (`https://github.com/HospitalitySolutions/Transfervio`)  
**Development root on the user's Windows computer:** `C:\DEVELOPMENT\Projects\transfervio`  
**Initial runtime:** Local development only. No AWS resources, cloud deployment, or production migrations in this phase.  
**Initial destination pilot:** Calendra, via an independently testable local adapter and a **non-production** Calendra environment/test tenant.  
**Approval constraint:** This is a Codex implementation plan, not authorization to merge to `main`, provision cloud infrastructure, or change Calendra production.

## 0. Non-negotiable principles

1. **Local first:** Code, local relational database, object storage, job state, logs, synthetic fixtures, local credentials, and runtime infrastructure reside on the developer's own computer. No Transfervio application data is persisted to AWS during local testing.
2. **Same application, two runtime configurations:** Develop one codebase, one domain model, and one tested application artifact with `local` and `cloud` configuration profiles. Cloud *configuration/design* can be created now; cloud *deployment/provisioning* must wait for explicit approval. Never fork business logic for cloud.
3. **Strict partner → tenant → property boundaries:** The link is bound to a destination partner and tenant, with optional property/location. Tenant/property IDs are not trusted from the recipient's browser.
4. **Privacy and deliberate external transfers:** Local storage does not imply offline processing if OpenAI API is enabled. By default, use a mock AI provider with synthetic data. Actual API vision inference transfers the submitted image/data to the AI provider; enable this explicitly, document it, and obtain an appropriate privacy/legal basis before real customer data. Writing to remote Calendra staging likewise transfers data off the computer.
5. **Calendra remains independent:** No direct Calendra database access, no copying its internal DB schema or login session tokens, and no modifications to Calendra production. Calendra adapter must use authorized application APIs.
6. **No live writes by default:** `DESTINATION_MODE=mock`, `IMPORT_WRITE_ENABLED=false`, and a preview/dry-run come first. Calendra writes require an explicit flag, staging-only allowlist, and a dedicated synthetic tenant/property.
7. **Only self-hosted GitHub Actions runners:** No GitHub-hosted runner of any kind. Missing local runner is a blocker, not a reason to fall back. Manual local tests are allowed and required.
8. **Secure public repository:** Repository currently appears public and uninitialized, so `.gitignore` and secret-scanning protections must be established before any commit. No `.env.local`, uploaded files, database dumps, logs, or real personal data in Git.
9. **Start with clients and appointments:** Recognize services, employees, spaces and locations only to the extent necessary to map appointments. Do not include financial history, subscriptions, entitlements, payment records, or full hotel PMS migrations in MVP.
10. **Import status means what it says:** `READY` only means data has been prepared; `IMPORTED` means Calendra/destination confirmed creation or successful idempotent update for the relevant records.

## 1. Project foundation and local folder structure

Codex must first inspect the existing local directory; preserve pre-existing work. If empty, initialize from GitHub once the repository initialization state is checked. The GitHub repository appeared empty at planning time; do not assume `main` actually has a commit yet. Initialize a minimal default branch if required, then work on a feature branch such as `feature/local-first-mvp`.

Expected layout:

```text
C:\DEVELOPMENT\Projects\transfervio\
  apps\
    api\                        # Java 21, Spring Boot 3, REST API, migrations/worker
    web\                        # React, TypeScript, Vite, partner portal + upload site
    calendra-bridge\             # Example local destination consumer (may be inside API module)
  packages\
    api-client-example\          # Minimal partner integration SDK/HTTP example
  infra\
    local\
      compose.yaml               # Postgres, MinIO, Mailpit (if required), test webhook receiver
      init\                      # Bootstrap local buckets and minimal synthetic partner
    cloud\
      README.md                  # Planned AWS architecture, not deployed
      env.example                # Configuration schema only; no real secrets
  scripts\
    start-local.ps1
    stop-local.ps1
    reset-local.ps1               # Confirmation required; destructive for local data
    run-local-tests.ps1
    run-calendra-smoke.ps1
    backup-local.ps1
  docs\
    architecture.md
    local-setup.md
    local-vs-cloud.md
    calendra-integration.md
    api\openapi.yaml
    security-privacy.md
  fixtures\synthetic\           # Synthetic CSV/XLSX/JSON/screenshot test fixtures
  .local-data\                   # All developer-owned local uploaded data and backups (gitignored)
    uploads\
    backups\
    logs\
  .env.local.example             # Committed sample only
  .env.cloud.example             # Committed sample only (no cloud provisioning)
  .env.local                     # Gitignored, local machine values
  .github\workflows\            # self-hosted only
  README.md
```

**Windows considerations:** Prefer Docker Desktop with WSL2 and Linux containers. PostgreSQL should use a **local Docker-managed volume** to avoid poor performance/permissions on NTFS bind mounts. That volume is still on the local machine, just not literally inside the project directory. MinIO/uploads/logs may use a gitignored project-local bind mount if safe and performant. Store `pg_dump` backups in `.local-data/backups`. Document explicitly how to inspect, back up, reset and remove all local data. If the user requires every DB byte under the project path, evaluate a WSL2 filesystem-backed bind mount and document the trade-offs rather than silently choosing NTFS for Postgres.

## 2. Technology and runtime architecture

- **Backend:** Java 21, Spring Boot 3, Maven, Flyway, Spring Security, Hibernate/JPA or JDBC where appropriate, springdoc OpenAPI.
- **Frontend:** React + TypeScript + Vite, responsive white-label upload portal and partner dashboard.
- **Database:** PostgreSQL in local Docker Compose. Separate Transfervio database, never Calendra's database.
- **Object storage:** S3-compatible **MinIO running locally**. All original CSV/XLSX/JSON/PNG/JPG files and screenshot derivatives stored here; never in a public directory.
- **Work queue:** Start with a durable Postgres-backed job/outbox table and bounded background workers; avoid Redis/SQS until genuinely necessary. Worker operations are restart-safe and idempotent.
- **Mail:** Optional local Mailpit for developer email/OTP testing; no live SES needed.
- **AI:** `mock` default; optionally `openai` using a personal test key and **synthetic** images. AI interface must support future provider alternatives without schema changes.
- **Destination integration:** `mock` destination and `calendra` adapter modes. Both consume the same canonical export contract.
- **Observability:** Local structured logs redacting personal fields, actuator health checks, per-job tracing/cost metrics; no production telemetry vendor required now.

### Two runtime configurations, one product

| Component | Local now | Cloud later (configuration scaffold only) |
|---|---|---|
| Runtime profile | `SPRING_PROFILES_ACTIVE=local` | `SPRING_PROFILES_ACTIVE=cloud` |
| Frontend | Vite dev server on localhost | Built static frontend behind CDN/reverse proxy |
| Backend | Host JVM or local container | Container on ECS/EC2 behind ALB |
| Database | Local PostgreSQL Docker volume | Isolated RDS PostgreSQL |
| File storage | Local MinIO | Private S3 bucket |
| Async work | Postgres job/outbox | Same job abstraction, optional SQS later |
| Secrets | Gitignored `.env.local` | AWS Secrets Manager / runtime environment |
| Email | Mailpit / disabled | Approved mail provider, e.g. SES |
| AI | Mock; optional manually enabled API | Explicitly configured external provider |
| Destination | Mock or test-only Calendra | Explicit approved partner endpoints |
| Deployment | Local PowerShell + Docker Compose | Infrastructure as code in `infra/cloud`; do not apply |

**Implementation note:** Prefer the same Docker image/JAR in both environments with runtime-injected configuration, not divergent compiled application logic. `local` and `cloud` launch scripts/manifests can differ. The backend must fail fast if `cloud` is selected without mandatory security/storage configuration; no silent fallback to local data or vice versa.

### Proposed local ports (all configurable; check conflicts before choosing)

- Web: `http://localhost:5174`
- API: `http://localhost:8091`
- PostgreSQL: host `5439`, container `5432`
- MinIO S3 API: host `9004`; MinIO console `9005`
- Mailpit UI (optional): `http://localhost:8027`
- OpenAPI: `http://localhost:8091/swagger-ui/index.html`

Do not assume these ports are free; Codex must check running Calendra services and existing local Docker port bindings first.

### Intended launch commands (Codex creates these scripts)

```powershell
cd C:\DEVELOPMENT\Projects\transfervio
Copy-Item .env.local.example .env.local
.\scripts\start-local.ps1 -Destination mock
# Web: http://localhost:5174
# API: http://localhost:8091
```

Then, when the Calendra adapter is ready and credentials for a test tenant are explicitly configured:

```powershell
.\scripts\start-local.ps1 -Destination calendra -DryRun
.\scripts\run-calendra-smoke.ps1 -DryRun
# After inspecting preview + destination settings and enabling safe writes:
.\scripts\run-calendra-smoke.ps1 -ConfirmWrite
```

The second launch mode must **not** automatically enable destination writes. The scripts should verify test-only destination URL, tenant and property allowlist, valid partner service credentials, no collision with production, and explicit confirmation before writing.

Also provide `.\scripts\stop-local.ps1`, `.\scripts\run-local-tests.ps1`, and a destructive reset command guarded by a prominent confirmation prompt.

## 3. Partner → tenant → property and invitation model

Definitions:

- **Partner**: SaaS provider that integrates once with Transfervio, e.g. Calendra, Lime Booking, PMS vendor.
- **Partner tenant**: The software provider's customer/prospect, e.g. a business. May have `external_tenant_id` or an onboarding placeholder until provisioned.
- **Property/location**: Optional subdivision of a tenant, e.g. salon branch, office, clinic location or hotel. Must belong to its tenant and partner.
- **Invitation**: One expiring, revocable, opaque upload link bound on the server to `partner_id`, `tenant_id`, optional `property_id`, permitted entity types and intended migration session.
- **Migration**: Upload(s), extraction, mappings, review, approved snapshot, export, delivery and record-level acknowledgment.

Illustrative API call:

```http
POST /v1/invitations
Authorization: Bearer <partner-api-key>
Content-Type: application/json
```

```json
{
  "tenant_id": "tenant_123",
  "property_id": "location_456",
  "allowed_entities": ["CLIENT", "APPOINTMENT"],
  "expires_in_hours": 168
}
```

Example local response:

```json
{
  "invitation_id": "inv_123",
  "upload_url": "http://localhost:5174/i/<opaque-random-token>",
  "expires_at": "2026-11-01T12:00:00Z"
}
```

For MVP, a localhost invitation is opened **on the same development computer**. It cannot be sent to a remote prospect over the internet. Remote links, TLS, partner-branded hostnames and delivery to actual customers come in a later explicitly approved phase. Do not expose local port to the internet through a tunnel by default.

No one may change tenant/property via URL query parameters, hidden forms or browser-supplied IDs. Unknown/inactive/revoked/expired tokens cannot upload or view records. Store invite token hashes (not raw token), bind session state to the invite, rate-limit the public endpoints, and prohibit cross-tenant access in every query.

### Minimum partner dashboard

1. Partner sign-in/admin.
2. Partner branding: logo, brand name, primary color, support text/link; initial Transfervio logo in non-white-label admin pages.
3. Partner API key creation/revocation (hashed at rest; show raw key once).
4. Tenant registration/listing; property creation under tenant; scoped ID catalog import.
5. Generate, copy, expire and revoke tenant-/property-specific invitation links.
6. Migration list and detail: uploaded files, processing phase, issues, import result.

### White-label upload portal

- Partner logo and colors shown on the upload page; no visible need for prospect to know Transfervio.
- Accept multiple CSV, XLSX, JSON, PNG, JPG files, regardless of source column naming. Reject unsupported files gracefully.
- Explain: *'Upload your export or screenshots; no special template required'*, but never promise all conceivable formats.
- Progress: Upload → Extract → Validate → Review → Approve → Partner delivery/Import result.
- Tabs for Clients and Appointments; show per-record provenance, duplicates, missing data and mappings.
- Final explicit approval required; upload alone does not import anything.

## 4. Canonical records and data safety

**Clients**: `stable_record_id`, source identifiers/provenance, first/last/display name, email, phone, optional notes/language, intended tenant/property, issues, approved status.

**Appointments**: `stable_record_id`, source identifiers/provenance, linked client `stable_record_id`, date/time/timezone, duration or end, status, source service name, source employee name, candidate destination service/employee IDs, intended property, issues, approved status.

Prioritize semantic identifiers over name-only matching. A booking can reference a client record from another uploaded file. The engine must preserve relationships and export clients before dependent appointments. Never fabricate missing year, timezone, client identity, service, employee or duration. Ambiguous time zones, overlapping calendar blocks and incomplete screenshots must be held for review.

Use idempotency keys by partner + tenant/property + migration + source record identity. Distinguish 'new', 'already imported', 'updated' and 'failed' in delivery receipts. Preserve anonymized/provenance references for debugging. Test duplicate uploads and repeated delivery, including partner server failure and restart.

**Data storage:** File bytes in local MinIO; records and mappings in local PostgreSQL; metadata and audit logs locally. No raw personal data in logs. Set local test retention (for example, delete files after 7 days) and add manual delete/cascade cleanup. Backups should contain only synthetic or permissioned test data.

## 5. Parsing and AI workflow

1. Upload files to private local object storage; validate MIME/magic bytes, maximum size/count, file checksum. Never execute spreadsheet macros/formulas.
2. Parse CSV, XLSX and JSON deterministically. Use AI only for semantic schema/column identification when useful, rather than spending tokens per row.
3. For screenshots, route through an interchangeable image extractor interface. `mock` uses deterministic fixture outputs; optional real vision processing uses strict JSON schema responses, source spans/coordinates where feasible, and token usage monitoring.
4. Normalize client fields and booking date/time/status with locale handling (Slovenian and English sample inputs), date ambiguity checks and configured property timezone.
5. Reconcile clients to appointments across files/screenshots. Flag ambiguities and duplicate screenshots (including overlapping calendar pages).
6. Resolve destination service/employee/property references using a catalog provided by the partner/Calendra test tenant. Do not infer nonexistent destination IDs.
7. Run rule-based validation, produce a review UI, allow correction, revalidate, and freeze an immutable approved export snapshot.
8. Emit `migration.ready` as a local signed event/webhook or make it available to a polling consumer. Export datasets via paginated partner API. Await per-record acknowledgment before calling a migration imported.

**Local AI configurations:**

- `AI_PROVIDER=mock` — default, no external calls; tests and synthetic fixture extraction.
- `AI_PROVIDER=openai` — explicitly opt in for model-based analysis; sends selected inputs to OpenAI API; log tokens/cost only, redact source data. Use only consented/anonymized/synthetic records until GDPR/security review.
- Optional `AI_PROVIDER=local` can be added later for fully offline model inference if hardware and accuracy justify it; not a Phase 0 prerequisite.

## 6. Calendra testing plan — local Transfervio to Calendra

### Source code findings to verify, not an integration guarantee

As of repository inspection, Calendra contains:

- `backend/src/main/java/com/example/app/client/ClientController.java` mapped to `/api/clients`.
- `backend/src/main/java/com/example/app/session/SessionBookingController.java` mapped to `/api/bookings`.

These controllers are **not necessarily public partner APIs** or safely usable via machine-to-machine credentials. Codex must inspect their authentication, tenant isolation, request DTOs, booking creation side effects, notifications and available dry-run/import options before deciding how to integrate. Do not assume personal browser login tokens are an appropriate integration method.

### Preferred architecture — independent local Calendra bridge

1. Create a reference **Calendra partner** and dedicated **synthetic test tenant/property** inside Transfervio.
2. Partner creates tenant-/property-scoped invitation; upload synthetic client/booking screenshots and CSV/XLSX; approve normalized records.
3. A **local** `calendra-bridge` polls the approved migration export API or receives an in-process/local callback. **Do not require an inbound public webhook to localhost**. No internet tunnel necessary.
4. Bridge queries/syncs the allowed destination catalog (services, employees, locations) using an authorized Calendra test credential. It maps Transfervio stable IDs to Calendra IDs.
5. Initial import is always a **dry-run** with validation and a detailed proposed-changes report. Preview clients, appointments, unresolved references, notifications that would fire, conflicts and duplicates.
6. When explicitly authorized and allowed by the integration, create clients FIRST, collect their Calendra IDs, then create appointments with correct client/service/employee/location references.
7. Bridge returns **per-record** imported/already-exists/error acknowledgment; Transfervio stores it locally and displays the result.
8. Retry exactly the same migration; prove no duplicate clients/appointments are created. Verify results inside the Calendra test tenant's CRM/calendar UI.
9. Never create invoices, notifications, reminders, entitlements or automatic billing as an unintended side effect. Confirm relevant Calendra behavior in source code and tests; if a dedicated import mode/endpoint is needed, prepare a **separate, reviewable Calendra PR** behind a feature flag and with explicit user approval.

### Environment priorities

- **Priority A:** Local Transfervio → local mock Calendra server/contract stub. Fully offline; synthetic data; easy repeatability.
- **Priority B:** Local Transfervio → Calendra running locally, if available and supported by its existing configuration. Use its own separate local database/test tenant.
- **Priority C:** Local Transfervio → `staging.calendra.si` with a dedicated test tenant and an explicitly permitted integration identity. Only later after dry-run checks and deliberate approval; data crosses to a remote staging service. No production endpoint writes.

The Calendra destination URL must be **explicitly allowlisted** in local configuration, never arbitrary user-controlled or supplied by invitation link holders. Reject `app.calendra.si`/production hosts and unknown tenants by default. Authentication secrets must remain local and never be embedded into frontend JS or Git.

### Calendra-specific review checklist for Codex

- Auth model: service account/credential scopes and location permissions; no use of SUPER_ADMIN to bypass normal isolation.
- DTOs and fields for client creation (name, email, phone, preferred language) and booking creation (client references, service ID, staff ID, location, start/end, status, group flags).
- Client deduplication policy and safely searchable identity fields.
- Appointment duplication prevention, local-time and DST logic, service rules, breaks, overlaps and cancellations.
- Booking side effects: reminders, emails/SMS, billing drafts, entitlement consumption, waitlist, activity log and realtime events.
- How to disable or safely suppress unwanted notifications/side effects only for import/testing, without weakening production behavior.
- What is needed for idempotent batch import and itemized outcomes, ideally a dedicated staging-only integration contract.

**Do not alter the existing Calendra repository until the necessary changes are documented and separately authorized.** The initial Transfervio MVP can still be finished and tested end-to-end with a local mock destination.

## 7. Partner API, event contract and local endpoints

Illustrative contract; generate OpenAPI and version it. All `/v1/*` routes require authenticated partner server-side access; public invitation routes use limited invitation sessions.

| Method | Route | Use |
|---|---|---|
| POST | `/v1/tenants` | Register/upsert partner's tenant |
| POST | `/v1/tenants/{id}/properties` | Register tenant property/location |
| PUT | `/v1/tenants/{id}/catalog` | Sync destination employee/service/location catalog |
| POST | `/v1/invitations` | Generate tenant-/property-specific link |
| GET | `/v1/invitations` | List invitations, statuses |
| POST | `/v1/invitations/{id}/revoke` | Revoke a link |
| GET | `/v1/migrations/{id}` | Status/metrics |
| GET | `/v1/migrations/{id}/export?entity=clients&cursor=...` | Paginated approved records |
| POST | `/v1/migrations/{id}/delivery-results` | Idempotent per-record partner result |
| POST | `/v1/webhook-subscriptions` | Local/production signed webhook target |
| POST | `/public/invitations/{token}/uploads` | Upload scoped files |
| POST | `/public/invitations/{token}/process` | Start extraction |
| GET | `/public/invitations/{token}/preview` | View extracted results/issues |
| PATCH | `/public/invitations/{token}/mappings` | Correct mapping decisions |
| POST | `/public/invitations/{token}/approve` | Freeze and approve export |

Do not expose raw invite token in log/analytics. Signed webhook body should contain migration identifiers and export references, not client PII. Implement webhook HMAC, timestamp/replay protection, retries and delivery audit. Provide a local consumer that **polls**, because a cloud Calendra service cannot directly call back into a laptop at `localhost`.

## 8. Local vs cloud configuration examples

Codex must create `.env.local.example` with illustrative non-secret values, e.g.:

```dotenv
TRANSFERVIO_RUNTIME=local
SPRING_PROFILES_ACTIVE=local
WEB_PORT=5174
API_PORT=8091
POSTGRES_PORT=5439
MINIO_PORT=9004
MINIO_CONSOLE_PORT=9005
DB_HOST=localhost
DB_NAME=transfervio
STORAGE_PROVIDER=minio
STORAGE_BUCKET=transfervio-local
AI_PROVIDER=mock
DESTINATION_MODE=mock
IMPORT_WRITE_ENABLED=false
CALENDRA_API_BASE_URL=
CALENDRA_ALLOWED_TENANT_ID=
CALENDRA_ALLOWED_PROPERTY_ID=
CALENDRA_SERVICE_TOKEN=
UPLOAD_RETENTION_DAYS=7
```

Actual DB/password/MinIO/admin/application secrets are loaded from ignored local secret sources. Never put a real Calendra token or OpenAI key in a checked-in example. Backend-to-Docker hostnames and ports differ from browser/host URLs; document them separately instead of assuming `localhost` works inside each container.

Create `application-local.yml` and `application-cloud.yml` with the same keys/interface. `infra/cloud/README.md` should describe intended AWS architecture and required secrets but must explicitly say **not provisioned or tested** until a later milestone. No Terraform apply or AWS CLI provisioning now.

## 9. Implementation milestones and acceptance gates

### M0 — Repo and local bootstrapping

- Inspect `C:\DEVELOPMENT\Projects\transfervio` and the GitHub repository before initializing.
- Protect public repo with `.gitignore`; prepare feature branch and non-secret sample configs.
- Create local Docker Compose Postgres and MinIO; Flyway migrations; Spring Boot health endpoint; Vite app; start/stop/test scripts; local backup/reset instructions.
- Add `local` and placeholder `cloud` profiles; cloud must fail fast if incomplete.
- Configure GitHub workflow `runs-on: [self-hosted, ...]` only, with exact verified runner labels.

**Acceptance:** One command starts the stack; health endpoints respond; data persists through restart; no AWS resources are contacted; the repo contains no secrets or user data.

### M1 — Partner + tenant/property invitations

- Partner auth and hashed API keys, tenant/property CRUD and branding.
- Create/revoke time-limited tenant/property links; branded upload landing page.
- Enforce partner/tenant/property scope server-side and across DB queries.

**Acceptance:** Two mock partners with identical external tenant IDs remain isolated; one property invitation cannot target another property; expired/revoked links stop working.

### M2 — Local file storage + structured import

- Multi-file CSV/XLSX/JSON uploads into local MinIO; parse and canonicalize clients/appointments, source provenance and duplicate rules.
- Show editable review/validation summary; build up service/employee/location catalog mapping.

**Acceptance:** Synthetic fixtures with differing columns produce the same canonical structure without an import template; no data leaves the local machine.

### M3 — Screenshot extraction + AI provider switch

- PNG/JPG uploading, fixture-backed deterministic mock extraction, configurable real vision provider, schema validation, screenshot overlap/duplicate detection and mapping review.
- Track AI usage/cost only when externally enabled; test with synthetic screenshots first.

**Acceptance:** Multiple screenshot layouts produce editable canonical records and provenance, with missing fields flagged. Mock mode is fully local/offline.

### M4 — Approval + partner delivery

- Immutable approved snapshot, signed event/webhook + paginated export and import acknowledgments.
- Reference/mock destination consumer; retry and partial failure support.

**Acceptance:** Local mock destination receives clients before appointments; exactly-once effect on repeated delivery; migration marked imported only on acknowledgments.

### M5 — Calendra bridge, dry-run first

- Inspect actual Calendra auth, create endpoints, mandatory fields, and side effects.
- Build local bridge in dry-run/pull mode against a contract stub or local Calendra test setup.
- With approval, test against dedicated non-production Calendra tenant; ensure allowed tenant/property, idempotency, no notifications, no finance side effects.

**Acceptance:** Synthetic 5 clients + 3 appointments reviewed, mapped and shown correctly in a Calendra *test* tenant; re-running creates zero duplicates and yields reliable delivery receipts. No Calendra production writes.

### M6 — Local hardening, cloud readiness documentation (no cloud deployment)

- Tests for tenant isolation, malformed files, arbitrary URLs/SSRF, token expiration, file quotas, logs redaction, timezone/DST, duplicate images and retry behavior.
- Build reproducible local production-like container bundle; cloud environment variables and inactive manifests; architecture diagram/runbook; backups/cleanup; license/README.
- Evaluate accuracy and processing cost from labeled synthetic/anonymized samples.

**Acceptance:** An independent developer can clone and run locally from the instructions without AWS, then simulate full partner onboarding and demo Calendra migration. Cloud is *planned but not activated*.

## 10. Local tests that Codex must automate

1. Partner A and B may both have `external_tenant_id=123`, but all data/API keys/invitations stay isolated.
2. Tenant T with properties P1 and P2 receives separate links; uploading via P1 can never import into P2.
3. Generated local link works in browser, expires, can be revoked; forged URL parameters and invitation ID swapping fail.
4. Import same client as CSV then screenshot and recognize/flag duplicates; provenance remains inspectable.
5. At least 500 synthetic clients + 200 synthetic appointments parse and export with stable links and pagination.
6. At least 20 synthetic screenshot fixtures (client list, appointment list and calendar UI) with labeled ground truth; report exact-field accuracy and ambiguous/missing field behavior. Do not hardcode a guessed accuracy claim.
7. Daylight saving / timezone and ambiguous date tests (e.g. `03/04/2026`) require correct mapping or manual clarification.
8. Re-running the same approved migration does not duplicate clients/appointments in the mock or Calendra test destination.
9. Delivery result statuses reflect actual ack success, partial failure and retries.
10. Default mode makes **zero AWS calls, zero paid AI calls, zero Calendra writes**; explicitly enabled test mode requires authorization.
11. All CI workflows reference verified self-hosted runner labels and no GitHub-hosted `runs-on` entry.
12. Tests assert no notification/payment/billing side effects in Calendra test mode; do not assume these are disabled without verifying their code paths.

## 11. What is needed from the user, and what is not yet needed

### Needed for M0–M4

- Access to **their own Windows computer** running Codex with read/write permission to `C:\DEVELOPMENT\Projects\transfervio`. This planning environment **cannot directly access that Windows folder**.
- Git and a local self-hosted GitHub Actions runner registered/authorized for `HospitalitySolutions/Transfervio` (or permission to register a new dedicated one).
- Java 21, Node.js LTS, Docker Desktop/WSL2, sufficient local disk space, and suitable Windows PowerShell.
- The attached Transfervio logo copied into the local project as a source branding asset; Codex should create a transparent header/icon export after approval. Use a synthetically generated placeholder partner logo for white-label testing.
- Synthetic sample data; permissioned and anonymized export examples later.

### Needed only when enabling real AI or Calendra test writes

- An OpenAI API key (or selected compatible vision service) for explicitly approved synthetic tests; not needed for M0–M2.
- A dedicated Calendra test tenant/property with known services and employees; a staging/local API base URL; approved scoped machine credential; ability to suppress notifications/side effects and inspect results.
- If existing Calendra APIs are insufficient, separate approval for a Calendra staging-only import API/feature flag and a separate Calendra PR.

### **Not needed now**

- AWS account provisioning or infrastructure changes, RDS, S3, ALB, SES, domain/DNS, TLS certificates, Stripe or billing system, public migration links for remote users, custom domains, cloud runners, live customer data.

## 12. Codex working contract and step-by-step prompt

**Paste this message to Codex together with the plan file:**

> Implement Transfervio locally first, under the exact Windows path `C:\DEVELOPMENT\Projects\transfervio`, using `HospitalitySolutions/Transfervio` for source control. Follow `TRANSFERVIO_MVP_LOCAL_FIRST_CODEX_PLAN.md` as the authoritative plan, superseding cloud-first priorities from earlier documents. Inspect the folder/repo before writing; don't overwrite existing files. Establish a new standalone Java 21 Spring Boot 3 + React/Vite/TypeScript application with PostgreSQL and MinIO in local Docker Compose, `.env.local` gitignored, startup/test/backup scripts, and strict partner→tenant→optional property isolation. Implement tenant/property-specific white-label migration invitation links, multi-file Excel/CSV/JSON/screenshot upload, mock-first AI extraction, canonical clients/appointments, validation, approval, partner export API, signed events and import acknowledgments. Support two runtime profiles: `local` usable now and `cloud` configuration-only, not deployed. Add a local Calendra bridge consumer that defaults to dry-run and later can import synthetic clients and bookings into a specifically allowlisted Calendra test tenant using authorized APIs; never touch production or use database writes. Inspect actual Calendra controller DTOs/auth and notification side effects before implementing writes. Keep all Transfervio test data stored locally; explicitly disclose when optional OpenAI inference or remote Calendra staging sends data out. Build and test in small phases, report acceptance results and blockers, and stop before merging, pushing deployment changes or changing Calendra without explicit approval. GitHub Actions must use only verified local self-hosted runners; if unavailable, stop rather than falling back to GitHub-hosted runners.

**First Codex checkpoint:** Report which local dependencies are installed, whether the Windows path is empty, which local ports are free, whether the GitHub repo is initialized, and whether the self-hosted runner can serve this repo. Then deliver M0 as a running local stack before implementing AI or cloud work.

---

## Local MVP demonstration script

1. Start Transfervio with `.\scripts\start-local.ps1 -Destination mock`.
2. Sign in as a synthetic partner called Calendra; register a demo tenant, then two properties.
3. Create an invitation scoped to Property 1; open its white-label upload page on `localhost`.
4. Upload one synthetic client CSV and two synthetic appointment screenshots. Review extracted clients and appointments, correct one ambiguous date and map the correct staff/service.
5. Approve the migration. Show generated canonical JSON and partner delivery acknowledgment via mock consumer.
6. Run Calendra bridge **dry-run** for the same approved migration. Display exactly which client and appointment records would be created and why any would be rejected.
7. When explicitly enabled, import into a dedicated *non-production* Calendra test tenant. Verify records in its CRM/calendar, run the same migration again, show no duplicates, and review status back in Transfervio.
8. Stop the application; restart it; show that migration history and locally stored files persist.
9. Demonstrate that `cloud` configuration exists but no cloud resources were deployed or contacted.
