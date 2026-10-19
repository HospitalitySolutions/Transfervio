param([string]$MigrationId, [switch]$DryRun, [switch]$ConfirmWrite)
. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
if ($ConfirmWrite) { throw 'Actual Calendra writes are not implemented or authorized. The inspected booking API has notification, entitlement and billing side effects. Use -DryRun with the local contract stub.' }
if (!$MigrationId) {
    $demoPath=Join-Path $LocalData 'demo.json'
    if (!(Test-Path $demoPath)) { throw 'Pass -MigrationId or run the synthetic migration tests first.' }
    $MigrationId=(Get-Content $demoPath -Raw | ConvertFrom-Json).migration_id
}
$base="http://localhost:$($env:API_PORT)"
$session=Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body (@{email='admin@calendra.test';password=$env:BOOTSTRAP_ADMIN_PASSWORD}|ConvertTo-Json)
try {
    $headers=@{Authorization="Bearer $($session.token)"}
    $preview=Invoke-RestMethod "$base/v1/migrations/$MigrationId/dry-run" -Headers $headers
    $preview|ConvertTo-Json -Depth 30|Set-Content "$LocalData\calendra-dry-run.json"
    Write-Host "Local Calendra contract dry-run: $($preview.proposed_changes.Count) records. No Calendra API contacted."
    Write-Host 'Preview: .local-data/calendra-dry-run.json. Remote writes remain disabled.'
} finally { Invoke-RestMethod "$base/v1/logout" -Method Post -Headers @{Authorization="Bearer $($session.token)"} | Out-Null }
