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
