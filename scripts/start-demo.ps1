param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'build.ps1')
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $builtJar = Get-ProjectJar -ProjectPath (Get-Location).Path
    Write-Host "Demo: http://127.0.0.1:$Port | demo_admin / DemoOnly_2026!"
    Write-Host 'Mock mode by default; configure a real provider in Model Settings. Demo data resets on restart. Use start-local.cmd for persistence.'
    & java -jar $builtJar --spring.profiles.active=demo "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw 'Application stopped with an error' }
} finally { Pop-Location }
