# Cloud design only — NOT PROVISIONED OR DEPLOYED

Future design: the same JAR/container on ECS/EC2 behind an HTTPS ALB; isolated RDS PostgreSQL; private S3 objects; Secrets Manager injection; static web assets behind a CDN. PostgreSQL queue remains valid initially. AWS credentials and SDK auto-discovery are not part of local startup.

See `../../.env.cloud.example` for the required configuration schema. Before activation: approved infrastructure plan, tenant/security review, MinIO/S3 compatibility checks, data retention policy, backup/restore drills, outbound destination allowlists, and a reviewed deployment workflow restricted to a local self-hosted runner. No Terraform or AWS CLI commands are supplied or run here.
