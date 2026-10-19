# Development notes

The runtime requires Java 21, Node 22.12+ or 24, Maven 3.9+, Docker Desktop Linux containers and PowerShell 7. Run commands from the repository root. `.env.local` is private; use the checked-in example for a fresh workspace and let `start-local.ps1` generate secrets.

Java sources use Google Java Format 1.30.0; React/TypeScript/CSS use Prettier 3.6.2. Formatting tools execute locally. Maven dependency resolution uses `infra/maven/settings.xml` to restrict mirrors to Maven Central. Do not use broad dependency prefetch plugins that discover repositories from unrelated optional dependencies.

`scripts/run-local-tests.ps1` enables the real local database/MinIO tests and runs the API, web build, HTTP acceptance and Chrome browser flow. The manually dispatched runner workflow builds and runs the unit suite; database tests are explicitly skipped there unless `RUN_LOCAL_INTEGRATION=true` and local configuration are supplied. No CI run has been claimed as completed.

Browser acceptance can also target the isolated container bundle by setting `BROWSER_BASE_URL=http://localhost:5175`, after seeding the synthetic customer, location and catalog in that bundle's separate database. Normal acceptance seeds the host stack automatically before the browser step. Test output and screenshots are private under `.local-data`.

Keep runtime services on loopback, use mock extraction and delivery by default, and preserve the strict invitation scope and explicit approval transition when extending the product. The optional OpenAI adapter and Calendra contract stub are intentionally gated; see their security/integration documents before enabling additional external behavior.
