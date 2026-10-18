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
