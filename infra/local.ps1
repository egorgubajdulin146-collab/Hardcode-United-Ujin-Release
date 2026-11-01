param(
    [ValidateSet('help', 'start', 'stop', 'status', 'logs', 'restart', 'images')]
    [string]$Action = 'help'
)
$ErrorActionPreference = 'Stop'
if ($Action -eq 'help') {
    Write-Output 'Actions: start, stop, status, logs, restart, images.'
    exit 0
}
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Missing .env' }
    $composeArgs = @('compose', '-f', 'docker-compose.yml')
    switch ($Action) {
        'start'   { $composeArgs += @('up', '-d', '--wait') }
        'stop'    { $composeArgs += 'down' }
        'status'  { $composeArgs += 'ps' }
        'logs'    { $composeArgs += @('logs', '--tail', '100', '-f') }
        'restart' { $composeArgs += @('restart', 'frontend') }
        'images'  { $composeArgs += 'pull' }
    }
    & docker @composeArgs
    if ($LASTEXITCODE -ne 0) { throw "Docker exited with code $LASTEXITCODE" }
} finally { Pop-Location }
