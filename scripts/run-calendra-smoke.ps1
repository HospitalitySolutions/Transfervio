# Compatibility name retained for existing local instructions. No vendor API is called.
param([string]$MigrationId, [switch]$DryRun, [switch]$ConfirmWrite)
if ($ConfirmWrite) { throw 'Remote writes are not implemented. Calendra must own its separately authorized connector.' }
& "$PSScriptRoot\run-mock-smoke.ps1" -MigrationId $MigrationId -DryRun
