param([string]$MigrationId, [string]$PartnerEmail = 'admin@calendra.test', [switch]$DryRun)
. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
if (!$MigrationId) {
    $demoPath = Join-Path $LocalData 'demo.json'
    if (!(Test-Path $demoPath)) { throw 'Pass -MigrationId or run the synthetic migration tests first.' }
    $MigrationId = (Get-Content $demoPath -Raw | ConvertFrom-Json).migration_id
}
$base = "http://localhost:$($env:API_PORT)"
$session = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body (@{email=$PartnerEmail;password=$env:BOOTSTRAP_ADMIN_PASSWORD}|ConvertTo-Json)
try {
    $preview = Invoke-RestMethod "$base/v1/migrations/$MigrationId/dry-run" -Headers @{Authorization="Bearer $($session.token)"}
    $preview | ConvertTo-Json -Depth 30 | Set-Content "$LocalData\mock-dry-run.json"
    Write-Host "Local mock preview: $($preview.proposed_changes.Count) records. Report: .local-data/mock-dry-run.json."
} finally { Invoke-RestMethod "$base/v1/logout" -Method Post -Headers @{Authorization="Bearer $($session.token)"} | Out-Null }
