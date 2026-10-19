# Same application, two profiles

| Concern | Local | Cloud scaffold |
|---|---|---|
| Database | Loopback PostgreSQL, Docker volume | Explicit isolated JDBC URL, verified TLS |
| Objects | Loopback MinIO | Explicit private S3-compatible endpoint and bucket |
| Secrets | Ignored `.env.local` | Runtime injection / future secret manager |
| HTTP | Loopback only | HTTPS reverse proxy required |
| Work queue | PostgreSQL | Same durable PostgreSQL abstraction |
| AI | Mock by default; explicit launch and file consent for OpenAI | Mock until explicitly enabled; same file consent checks |
| Destination | Mock / dry-run for testing; generic vendor pull API | Same vendor pull contract; no backend vendor adapters |

The cloud profile has no fallback credentials, database, storage endpoint, region, bucket, or public URL. It must fail startup if these are missing or if TLS requirements are not met. It never creates a cloud bucket. No infrastructure was provisioned, cloud data stored, or cloud profile deployed/tested against real services.

Local containers use Docker service names internally; host JVM and browser use loopback host ports. Configuration cannot silently select AWS in local mode. Dependency downloads during installation are different from application data transfers.
