param()
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$candidate = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
$docker = if (Test-Path -LiteralPath $candidate) { $candidate } else { 'docker' }
Push-Location $project
try {
    & $docker version --format '{{.Server.Version}}'
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop in Linux container mode first. This script does not install or reset Docker.' }
    & $docker build -t agent-platform-python-gui:1.7 -f docker/python-preview/Dockerfile docker/python-preview
    if ($LASTEXITCODE -ne 0) { throw 'Python preview image build failed. Keep the error output and retry after checking Docker/network.' }
    Write-Host 'Python desktop preview ready: Tkinter, Pygame, Matplotlib, Pillow; runtime is offline and has no host directory mounts.'
} finally { Pop-Location }
