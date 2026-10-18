param([ValidateSet('mock','calendra')][string]$Destination = 'mock', [switch]$DryRun, [switch]$SkipBuild)
. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
$env:DESTINATION_MODE = $Destination
$env:IMPORT_WRITE_ENABLED = 'false'
if ($env:AI_PROVIDER -ne 'mock' -or $env:EXTERNAL_AI_ENABLED -ne 'false') { throw 'Only local mock AI is supported by this launch command.' }
$pidFile = Join-Path $LocalData 'processes.json'
if (Test-Path $pidFile) { throw 'A stack may already be running. Run stop-local.ps1 before restarting.' }
foreach ($port in @([int]$env:API_PORT,[int]$env:WEB_PORT)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Port $port is already in use." }
}
docker compose --env-file "$ProjectRoot\.env.local" -f "$ProjectRoot\infra\local\compose.yaml" up -d --wait
Assert-Exit 'Local infrastructure'
if (!$SkipBuild) {
    & mvn.cmd -B -f "$ProjectRoot\apps\api\pom.xml" package -DskipTests
    Assert-Exit 'API build'
    Push-Location "$ProjectRoot\apps\web"
    try {
        if (Test-Path package-lock.json) { & npm.cmd ci --no-audit --no-fund } else { & npm.cmd install --no-audit --no-fund }
        Assert-Exit 'Web dependency installation'
        & npm.cmd run build
        Assert-Exit 'Web build'
    } finally { Pop-Location }
}
$api = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-jar',"`"$ProjectRoot\apps\api\target\transfervio-api-0.1.0-SNAPSHOT.jar`"") -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput "$LocalData\logs\api.stdout.log" -RedirectStandardError "$LocalData\logs\api.stderr.log"
@{api=$api.Id;root=$ProjectRoot} | ConvertTo-Json | Set-Content $pidFile
Wait-Http "http://localhost:$($env:API_PORT)/actuator/health"
$node = (Get-Command node.exe).Source
$web = Start-Process -FilePath $node -ArgumentList @("`"$ProjectRoot\apps\web\node_modules\vite\bin\vite.js`"") -WorkingDirectory "$ProjectRoot\apps\web" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$LocalData\logs\web.stdout.log" -RedirectStandardError "$LocalData\logs\web.stderr.log"
@{api=$api.Id;web=$web.Id;root=$ProjectRoot} | ConvertTo-Json | Set-Content $pidFile
Wait-Http "http://localhost:$($env:WEB_PORT)"
Write-Host "Transfervio: http://localhost:$($env:WEB_PORT) | API: http://localhost:$($env:API_PORT)"
Write-Host 'Local storage only. Mock AI. Destination writes disabled.'
Write-Host 'Partner login: admin@calendra.test. Password: BOOTSTRAP_ADMIN_PASSWORD in .env.local (never commit it).'
