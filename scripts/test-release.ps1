param()
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$oldDocker = $env:SANDBOX_TEST_DOCKER
$oldGui = $env:SANDBOX_TEST_GUI
Push-Location $project
try {
    New-Item -ItemType Directory -Path (Join-Path $project 'target') -Force | Out-Null
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    $docker = if (Test-Path -LiteralPath $candidate) { $candidate } else { 'docker' }
    & $docker image inspect agent-platform-python-gui:1.7 --format '{{.Id}}'
    if ($LASTEXITCODE -ne 0) { throw 'Full release verification requires Docker and prepare-python-preview.cmd.' }
    $env:SANDBOX_TEST_DOCKER = $docker
    $env:SANDBOX_TEST_GUI = 'agent-platform-python-gui:1.7'
    & ./scripts/test-postgres.ps1 *> target/v1.7-full-final.log
    Get-Content -LiteralPath target/v1.7-full-final.log -Tail 32
    Write-Host 'Full Java/PostgreSQL/Docker verification complete. Browser tests run separately against an isolated demo.'
} finally {
    $env:SANDBOX_TEST_DOCKER = $oldDocker
    $env:SANDBOX_TEST_GUI = $oldGui
    Pop-Location
}
