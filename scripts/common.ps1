$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$LocalData = Join-Path $ProjectRoot '.local-data'
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
function Import-LocalEnvironment {
    $path = Join-Path $ProjectRoot '.env.local'
    if (!(Test-Path -LiteralPath $path)) { Copy-Item -LiteralPath (Join-Path $ProjectRoot '.env.local.example') -Destination $path }
    $lines = @(Get-Content -LiteralPath $path)
    $secretNames = @('DB_PASSWORD','MINIO_ROOT_PASSWORD','BOOTSTRAP_ADMIN_PASSWORD','WEBHOOK_SIGNING_MASTER')
    foreach ($secretName in $secretNames) { if (!($lines | Where-Object { $_ -match "^$secretName=" })) { $lines += "$secretName=" } }
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            $name = $Matches[1]; $value = $Matches[2]
            if ($name -in $secretNames -and [string]::IsNullOrWhiteSpace($value)) { $value = New-LocalSecret; $lines[$i] = "$name=$value" }
            [Environment]::SetEnvironmentVariable($name,$value,'Process')
        }
    }
    [IO.File]::WriteAllLines($path,$lines,(New-Object Text.UTF8Encoding($false)))
    New-Item -ItemType Directory -Force $LocalData,(Join-Path $LocalData 'logs'),(Join-Path $LocalData 'backups') | Out-Null
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:TRANSFERVIO_RUNTIME = 'local'
    if (!$env:JAVA_HOME) {
        $java = Get-ChildItem 'C:\Program Files\Java','C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '21' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } | Select-Object -First 1
        if ($java) { $env:JAVA_HOME = $java.FullName }
    }
    if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'Install JDK 21 and set JAVA_HOME.' }
}
function Assert-Exit([string] $Step) { if ($LASTEXITCODE -ne 0) { throw "$Step failed (exit $LASTEXITCODE)." } }
function Wait-Http([string]$Url, [int]$Attempts = 90) {
    for ($i=0; $i -lt $Attempts; $i++) {
        try { $response = Invoke-WebRequest -Uri $Url -TimeoutSec 3; if ($response.StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $Url. Inspect .local-data/logs."
}
