$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$files = @(git -C $root ls-files --cached --others --exclude-standard)
foreach ($file in $files) {
    if ($file -match '(^|/)(\.env\.local|\.local-data|\.runner|node_modules|target)(/|$)') { throw "Private/generated path exposed to Git: $file" }
    if ($file -match '\.(pem|key|dump)$') { throw "Private file exposed to Git: $file" }
    if ($file -match '\.(png|jpg|xlsx)$') { continue }
    $content = Get-Content -Raw -LiteralPath (Join-Path $root $file)
    if ($content -match '(AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|sk-proj-[A-Za-z0-9_-]{30,})') { throw "Possible secret in $file" }
}
foreach ($workflow in (Get-ChildItem "$root/.github/workflows" -File)) {
    $content = Get-Content $workflow.FullName -Raw
    if ($content -notmatch 'runs-on: \[self-hosted, Windows, X64, transfervio-local\]' -or $content -match '(ubuntu|windows|macos)-latest|pull_request_target:|pull_request:') { throw "Unsafe runner configuration: $($workflow.Name)" }
}
Write-Host 'Source hygiene and self-hosted-only workflow checks passed.'
