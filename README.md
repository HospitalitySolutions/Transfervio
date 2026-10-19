# Transfervio

Local-first migration of clients and appointments, with partner → tenant → property isolation and explicit review before delivery.

## Run on Windows

Prerequisites: Docker Desktop (Linux containers), JDK 21, Maven 3.9+, Node 22.12+ or Node 24, PowerShell 7, Git.

```powershell
Copy-Item .env.local.example .env.local
.\scripts\start-local.ps1 -Destination mock
```

The startup script generates local secrets in ignored `.env.local`. The first build downloads dependencies and builds MinIO from pinned official source; allow several minutes. Subsequent startups reuse cached images. Open [the local workspace](http://localhost:5174), [API health](http://localhost:8091/actuator/health), or [OpenAPI](http://localhost:8091/swagger-ui/index.html).

```powershell
.\scripts\run-local-tests.ps1
.\scripts\backup-local.ps1
.\scripts\stop-local.ps1
.\scripts\start-local.ps1 -SkipBuild
```

Everything binds to loopback. No tunnel, AWS provisioning, deployment, production Calendra access, or paid AI calls. Cloud configuration is a scaffold, not a deployment. External AI and remote writes fail closed.

Sign in as `admin@calendra.test` or `admin@lime.test` using `BOOTSTRAP_ADMIN_PASSWORD` from `.env.local`. These are synthetic local accounts. Add a customer, create its location and catalog, generate an invitation, then open the one-time upload link on this computer. Files under `fixtures/synthetic` are safe for demonstrations. Upload `clients-sl.csv` and `screenshot-09.png` for five clients and three appointments; add `screenshot-17.png` to exercise an ambiguous date and missing duration.

The recipient reviews and approves; the partner opens the migration, previews delivery, then delivers to the local mock. `READY` means approved, while `IMPORTED` requires confirmed record receipts. `run-calendra-smoke.ps1 -DryRun` writes a local proposed-changes report. Real Calendra import is blocked pending the API/credential work documented in [Calendra integration](docs/calendra-integration.md).

Validated features and remaining external gates are recorded in [milestone evidence](docs/milestones.md). See [security and privacy](docs/security-privacy.md) for retention, synthetic-only optional AI, and known limitations. The code-based branding is a placeholder; no original logo file was supplied.

See [local setup](docs/local-setup.md), [architecture](docs/architecture.md), [runtime profiles](docs/local-vs-cloud.md), and the authoritative [implementation plan](TRANSFERVIO_MVP_LOCAL_FIRST_CODEX_PLAN.md).

## CI

Only a dedicated local self-hosted Windows runner is permitted. The workflow is manually dispatched by maintainers; it never runs on untrusted pull requests. No GitHub-hosted fallback. Tests also run directly on the developer's computer. No workflow deploys anything.
