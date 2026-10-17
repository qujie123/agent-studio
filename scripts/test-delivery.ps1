param([switch]$WithoutDocker)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$oldDocker = $env:SANDBOX_TEST_DOCKER
$oldGui = $env:SANDBOX_TEST_GUI
Push-Location $project
try {
    New-Item -ItemType Directory -Path (Join-Path $project 'target') -Force | Out-Null
    if (-not $WithoutDocker) {
        $candidate = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
        $docker = if (Test-Path -LiteralPath $candidate) { $candidate } else { 'docker' }
        & $docker image inspect agent-platform-python-gui:1.7 --format '{{.Id}}'
        if ($LASTEXITCODE -ne 0) { throw 'Start Docker and run prepare-python-preview.cmd before the full delivery tests.' }
        $env:SANDBOX_TEST_DOCKER = $docker
        $env:SANDBOX_TEST_GUI = 'agent-platform-python-gui:1.7'
        $ErrorActionPreference = 'Continue' # PowerShell 5.1 redirects native stderr warnings as error records.
        & ./mvnw.cmd -B -ntp '-Dtest=ProjectDeliveryTests,ArtifactIntegrationTests,LocalPersistenceTests,ProjectPythonSandboxTests' test *> target/v1.7-delivery-tests.log
    } else {
        $ErrorActionPreference = 'Continue'
        & ./mvnw.cmd -B -ntp '-Dtest=ProjectDeliveryTests,ArtifactIntegrationTests,LocalPersistenceTests' test *> target/v1.7-delivery-tests.log
    }
    $testExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Get-Content -LiteralPath target/v1.7-delivery-tests.log -Tail 24
    if ($testExit -ne 0) { throw 'Delivery verification failed.' }
} finally {
    $env:SANDBOX_TEST_DOCKER = $oldDocker
    $env:SANDBOX_TEST_GUI = $oldGui
    Pop-Location
}
