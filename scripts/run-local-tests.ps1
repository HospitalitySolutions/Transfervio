. "$PSScriptRoot\common.ps1"
Import-LocalEnvironment
$env:AI_PROVIDER='mock'; $env:EXTERNAL_AI_ENABLED='false'; $env:AI_SYNTHETIC_ONLY='true'
$runtime = Invoke-RestMethod "http://localhost:$($env:API_PORT)/api/runtime"
if ($runtime.ai_provider -ne 'mock') { throw 'Restart with start-local.ps1 in default mock mode before acceptance tests.' }
$env:RUN_LOCAL_INTEGRATION='true'
& "$PSScriptRoot\check-source.ps1"
& mvn.cmd -B -s "$ProjectRoot\infra\maven\settings.xml" -f "$ProjectRoot\apps\api\pom.xml" verify
Assert-Exit 'API tests'
Push-Location "$ProjectRoot\apps\web"
try { & npm.cmd run build; Assert-Exit 'Web build' } finally { Pop-Location }
$health = Invoke-RestMethod "http://localhost:$($env:API_PORT)/actuator/health"
if ($health.status -ne 'UP') { throw 'Health check failed' }
$runtime = Invoke-RestMethod "http://localhost:$($env:API_PORT)/api/runtime"
if ($runtime.runtime -ne 'local' -or $runtime.ai_provider -ne 'mock' -or $runtime.import_write_enabled) { throw 'Unsafe runtime' }
& node "$PSScriptRoot\test-isolation.mjs"
Assert-Exit 'Partner isolation tests'
& node "$PSScriptRoot\test-migration.mjs"
Assert-Exit 'Migration acceptance tests'
& "$PSScriptRoot\generate-xlsx-fixture.ps1" -Unfamiliar
& node "$PSScriptRoot\test-universal.mjs"
Assert-Exit 'Universal export, mapping and vendor consumer tests'
& node "$PSScriptRoot\test-boundaries.mjs"
Assert-Exit 'Quota, scope, source and cleanup tests'
& node "$PSScriptRoot\test-events.mjs"
Assert-Exit 'Signed webhook tests'
Push-Location "$ProjectRoot\packages\contract-tests"
try { & npm.cmd ci --ignore-scripts --no-audit --no-fund; Assert-Exit 'Contract dependencies'; & node test.mjs; Assert-Exit 'OpenAPI and response contracts' } finally { Pop-Location }
Push-Location "$ProjectRoot\apps\web"
try { & npx.cmd playwright test; Assert-Exit 'Browser acceptance tests' } finally { Pop-Location }
Write-Host 'All local tests passed. Results and screenshots: .local-data.'
