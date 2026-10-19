# Transfervio

Universal migration of clients and appointments, with local storage, partner → tenant → property isolation and explicit customer review. Vendors retrieve approved canonical records and import them in their own systems.

## Run on Windows

Prerequisites: Docker Desktop (Linux containers), JDK 21, Maven 3.9+, Node 22.12+ or Node 24, PowerShell 7, Git.

```powershell
if (!(Test-Path .env.local)) { Copy-Item .env.local.example .env.local }
.\scripts\start-local.ps1 -Destination mock
```

The startup script generates local secrets in ignored `.env.local`. The first build downloads dependencies and builds MinIO from pinned official source; allow several minutes. Subsequent startups reuse cached images. Open [the local workspace](http://localhost:5174), [API health](http://localhost:8091/actuator/health), or [OpenAPI](http://localhost:8091/swagger-ui/index.html).

```powershell
.\scripts\run-local-tests.ps1
.\scripts\backup-local.ps1
.\scripts\stop-local.ps1
.\scripts\start-local.ps1 -SkipBuild
```

Everything binds to loopback. Cloud configuration is a scaffold; no AWS resources or deployment are required. Normal startup forces mock AI even if optional OpenAI credentials exist in `.env.local`. External AI requires an explicit launch flag and consent for each uploaded file; see [AI controls](docs/security-privacy.md#optional-external-ai). Remote destination writes are not part of the backend.

Sign in as `admin@calendra.test` or `admin@lime.test` using `BOOTSTRAP_ADMIN_PASSWORD` from `.env.local`. These are synthetic local accounts. Add a customer, create its location and catalog, generate an invitation, then open the one-time upload link on this computer. Files under `fixtures/synthetic` are safe for demonstrations. Upload `clients-sl.csv` and `screenshot-09.png` for five clients and three appointments; add `screenshot-17.png` to exercise an ambiguous date and missing duration.

The recipient confirms unfamiliar column mappings, reviews records and chooses the fields to share before approval. Vendors discover fields at `/v1/export-schema`, retrieve full or selected fields from `/v1/migrations/{id}/export`, and return per-record import receipts. `READY` means approved; exporting alone never sets `IMPORTED`. The [vendor guide](docs/api/README.md) includes a working generic consumer, and the dashboard retains local mock delivery for testing. Calendra must own its connector; see the [integration boundary](docs/calendra-integration.md).

Current validation is recorded in [universal migration review](docs/universal-migration-review.md), with earlier M0–M6 evidence in [milestones](docs/milestones.md). See [security and privacy](docs/security-privacy.md) for consent, retention and operational limits.

See [local setup](docs/local-setup.md), [architecture](docs/architecture.md), [runtime profiles](docs/local-vs-cloud.md), the original [local foundation plan](TRANSFERVIO_MVP_LOCAL_FIRST_CODEX_PLAN.md), and the current [universal migration specification](docs/universal-migration-spec.md).

## CI

Only a dedicated local self-hosted Windows runner is permitted. The workflow is manually dispatched by maintainers; it never runs on untrusted pull requests. No GitHub-hosted fallback. Tests also run directly on the developer's computer. No workflow deploys anything.
