. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
& mvn.cmd -B -f "$ProjectRoot\apps\api\pom.xml" verify
Assert-Exit 'API tests'
Push-Location "$ProjectRoot\apps\web"
try { & npm.cmd run build; Assert-Exit 'Web build' } finally { Pop-Location }
$health = Invoke-RestMethod "http://localhost:$($env:API_PORT)/actuator/health"
if ($health.status -ne 'UP') { throw 'Health check failed' }
$runtime = Invoke-RestMethod "http://localhost:$($env:API_PORT)/api/runtime"
if ($runtime.runtime -ne 'local' -or $runtime.ai_provider -ne 'mock' -or $runtime.import_write_enabled) { throw 'Unsafe runtime' }
Write-Host 'Local foundation checks passed.'
