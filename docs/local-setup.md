# Local setup and ownership

The project lives at `C:\DEVELOPMENT\Projects\transfervio`. Calendra has its own unrelated database and application; do not reuse them.

| Service | Host URL/port | Container address |
|---|---|---|
| React/Vite | http://localhost:5174 | Host process |
| Spring Boot | http://localhost:8091 | Host process |
| PostgreSQL | localhost:5439 | postgres:5432 |
| MinIO S3 | http://localhost:9004 | minio:9000 |
| MinIO console | http://localhost:9005 | minio:9001 |

Ports are configurable in `.env.local`. `start-local.ps1` checks web/API conflicts; Docker rejects occupied infrastructure ports. Set `JAVA_HOME` to JDK 21. The script can discover installations under Program Files. Java's Oracle `javapath` shim may hang in restricted contexts; the launcher uses the actual JDK executable.

## Storage

- PostgreSQL uses the Docker-managed `transfervio_postgres-data` volume on this computer, inside Docker Desktop's Linux disk. It is deliberately not an NTFS database bind mount.
- Private MinIO objects live in `.local-data/objects`. Buckets have no public access policy.
- Application logs, backups, process IDs, and test reports live under ignored `.local-data`.
- `.env.local` contains randomly generated credentials. Never share or commit it. No secrets are in frontend code.
- The dedicated Actions runner and its credentials are under ignored `.runner`. It is a background process, not a Windows service. Stop it when not needed; inspect its status via GitHub repository Settings → Actions → Runners. Only trusted manual workflows may run on this machine.

## Inspect, stop, back up, reset

```powershell
docker compose --env-file .env.local -f infra/local/compose.yaml ps
docker volume inspect transfervio_postgres-data
.\scripts\backup-local.ps1
.\scripts\stop-local.ps1
```

Stopping preserves all data. `backup-local.ps1` creates a PostgreSQL custom-format dump in `.local-data/backups/<timestamp>`. For a full consistent backup, stop API uploads first, create the database dump, stop infrastructure, and copy `.local-data/objects` beside the dump. Store credentials separately. Restore with `pg_restore` into an empty Transfervio database and restore the corresponding object folder while MinIO is stopped. Never restore into Calendra.

`reset-local.ps1` requires typing `DELETE TRANSFERVIO`. It removes only this Compose project's database volume and `.local-data/objects`; it preserves backups and credentials. It does not touch Calendra or other Docker volumes. Manual pruning of unrelated Docker data is not part of this project.

MinIO community binary distribution changed; the local image builds `RELEASE.2025-10-15T17-29-55Z` from the official source using Go. See the [upstream source and AGPL license](https://github.com/minio/minio). This development dependency requires a maintenance/security review before any cloud use.

## Production-like local container bundle

```powershell
docker compose --env-file .env.local -f infra/local/compose.bundle.yaml up -d --build
# http://localhost:5175 — separate database, bucket and object folder
docker compose --env-file .env.local -f infra/local/compose.bundle.yaml stop
```

The bundle uses the same JAR/domain logic with local-container hostnames and a built React frontend behind Nginx. API/data services join only a Docker internal network; Nginx also joins an ingress bridge so Docker Desktop can publish the loopback port. Its database volume is `transfervio-bundle_bundle-postgres`; objects are `.local-data/bundle/objects`. It exposes only the loopback web port, not the database or object store. `stop-local.ps1` controls the host-development stack only; stop the bundle with its own Compose file. Never use `down --volumes` unless deliberately deleting the bundle database. Its backup/restore procedure is the same as above, with the bundle Compose file and object path.

After the acceptance suite, verify persistence with `node scripts/test-persistence.mjs --capture`, stop/start the host stack, then `node scripts/test-persistence.mjs`. It compares approved exports, receipts and an original file's SHA-256 without printing invitation capabilities or record contents.

The full test script requires Chrome installed for headless Playwright (or configure the browser channel in `apps/web/playwright.config.ts`). Browser tests create synthetic invitations and imports and save screenshots locally. They block external browser requests. The Java database tests use rollback-scoped synthetic rows, and their temporary MinIO object is removed after the test.

## Optional authorized AI

Ordinary startup forces mock mode without overwriting configured optional credentials. To use OpenAI, configure `AI_PROVIDER=openai`, `EXTERNAL_AI_ENABLED=true`, `OPENAI_MODEL` and `OPENAI_API_KEY` in ignored `.env.local`, stop the app, then explicitly start with one mode:

```powershell
.\scripts\start-local.ps1 -EnableSyntheticAi  # verified fixture checksums only
# OR, for separately authorized previously unseen sources:
.\scripts\start-local.ps1 -EnableExternalAi
```

Both modes require file-by-file consent in the upload portal. Structured mapping suggestions also require an explicit button action and human confirmation. An ordinary upload makes no external request. Restart without either flag to return to mock. The optional container bundle remains offline/mock and is not the live AI test runtime.

The normal `run-local-tests.ps1` runs Java/local database, frontend build, isolation, migration, universal mapping/export/consumer, boundary, event, OpenAPI and browser suites. It refuses to run against an AI-enabled app. `generate-live-ai-fixture.ps1` and `test-live-ai.mjs --authorized-synthetic-test` are separate, explicitly authorized acceptance helpers; they may incur provider charges and are never called by CI or the standard suite. Live test results and usage totals stay in ignored `.local-data`.
