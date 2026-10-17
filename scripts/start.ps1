param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'build.ps1')
foreach ($name in @('DB_PASSWORD', 'JWT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Set the $name environment variable first (see README.md)."
    }
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $builtJar = Get-ProjectJar -ProjectPath (Get-Location).Path
    & java -jar $builtJar --spring.profiles.active=production "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw 'Application stopped with an error' }
} finally { Pop-Location }
