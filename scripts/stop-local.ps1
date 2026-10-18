. "$PSScriptRoot\common.ps1"
$pidFile = Join-Path $LocalData 'processes.json'
if (Test-Path $pidFile) {
    $state = Get-Content $pidFile -Raw | ConvertFrom-Json
    foreach ($processId in @($state.api,$state.web)) {
        if (!$processId) { continue }
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
        if ($proc -and $proc.CommandLine -like "*$ProjectRoot*") { Stop-Process -Id $processId -Force }
        elseif ($proc) { throw "Refusing to stop unrelated process $processId" }
    }
    Remove-Item -LiteralPath $pidFile
}
docker compose --env-file "$ProjectRoot\.env.local" -f "$ProjectRoot\infra\local\compose.yaml" stop
Assert-Exit 'Stop infrastructure'
Write-Host 'Stopped. Database and files are retained locally.'
