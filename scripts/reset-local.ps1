. "$PSScriptRoot\common.ps1"
$answer = Read-Host 'DESTRUCTIVE: delete ALL Transfervio local database and uploaded objects? Type DELETE TRANSFERVIO'
if ($answer -cne 'DELETE TRANSFERVIO') { throw 'Reset cancelled.' }
& "$PSScriptRoot\stop-local.ps1"
docker compose --env-file "$ProjectRoot\.env.local" -f "$ProjectRoot\infra\local\compose.yaml" down --volumes
Assert-Exit 'Remove local database volume'
$objects = [IO.Path]::GetFullPath((Join-Path $LocalData 'objects'))
$expected = [IO.Path]::GetFullPath((Join-Path $ProjectRoot '.local-data\objects'))
if ($objects -ne $expected -or !$objects.StartsWith($ProjectRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe deletion path' }
if (Test-Path -LiteralPath $objects) { Remove-Item -LiteralPath $objects -Recurse -Force }
Write-Host 'Reset complete. Backups, logs and local credentials remain.'
