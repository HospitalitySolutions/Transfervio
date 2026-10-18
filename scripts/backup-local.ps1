. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path $LocalData "backups\$stamp"
New-Item -ItemType Directory -Path $backup | Out-Null
docker compose --env-file "$ProjectRoot\.env.local" -f "$ProjectRoot\infra\local\compose.yaml" exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/transfervio.dump'
Assert-Exit 'Database dump'
docker compose --env-file "$ProjectRoot\.env.local" -f "$ProjectRoot\infra\local\compose.yaml" cp postgres:/tmp/transfervio.dump "$backup\database.dump"
Assert-Exit 'Copy database dump'
Write-Host "Database backup saved to $backup. For a consistent full backup, stop uploads and copy .local-data/objects after stopping MinIO."
