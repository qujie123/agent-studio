$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'build.ps1')
$taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixture = Join-Path $taskTempRoot ('agent-build-test-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $fixture,(Join-Path $fixture 'src'),(Join-Path $fixture '.mvn'),(Join-Path $fixture 'target') | Out-Null
    foreach ($name in @('pom.xml','mvnw','.mvn/wrapper.properties','src/input.txt','src/application.yml','src/application-demo.yml','src/application-local.yml','target/agent-platform-0.0.1-SNAPSHOT.jar')) { Set-Content -LiteralPath (Join-Path $fixture $name) -Value 'fixture' }
    Set-Content -LiteralPath (Join-Path $fixture 'mvnw.cmd') -Value "@echo off`necho fixture-build-output`nexit /b 0"
    $jar = Get-ProjectJar -ProjectPath $fixture
    if ($jar -isnot [string] -or $jar -ne (Join-Path $fixture 'target/agent-platform-0.0.1-SNAPSHOT.jar')) { throw 'Build output contaminated the returned JAR path' }
    $stamp = Join-Path $fixture 'target/build-inputs.json'
    $before = Get-Content -LiteralPath $stamp -Raw
    Get-ProjectJar -ProjectPath $fixture | Out-Null
    if ($before -ne (Get-Content -LiteralPath $stamp -Raw)) { throw 'Unchanged inputs unexpectedly rebuilt' }
    $helper = (Join-Path $PSScriptRoot 'build.ps1').Replace("'", "''")
    $fixtureArg = $fixture.Replace("'", "''")
    & powershell -NoProfile -Command ". '$helper'; Get-ProjectJar -ProjectPath '$fixtureArg' | Out-Null"
    if ($LASTEXITCODE -ne 0 -or $before -ne (Get-Content -LiteralPath $stamp -Raw)) { throw 'Windows PowerShell and PowerShell 7 must reuse the same unchanged build' }
    $source = Join-Path $fixture 'src/input.txt'; $timestamp = (Get-Item -LiteralPath $source).LastWriteTimeUtc
    Set-Content -LiteralPath $source -Value 'changed'; (Get-Item -LiteralPath $source).LastWriteTimeUtc = $timestamp
    Get-ProjectJar -ProjectPath $fixture | Out-Null
    if ($before -eq (Get-Content -LiteralPath $stamp -Raw)) { throw 'Content change with unchanged timestamp was not detected' }
    $before = Get-Content -LiteralPath $stamp -Raw
    Set-Content -LiteralPath (Join-Path $fixture 'mvnw.cmd') -Value "@echo off`nexit /b 7"
    $failed = $false; try { Get-ProjectJar -ProjectPath $fixture | Out-Null } catch { $failed = $true }
    if (-not $failed -or $before -ne (Get-Content -LiteralPath $stamp -Raw)) { throw 'Failed builds must not be stamped as current' }
    Write-Host 'Build regressions passed: content freshness, stable reuse across PowerShell versions, clean JAR return and failed-build protection.'
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    if ($resolvedFixture.StartsWith($taskTempRoot,[StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedFixture -Leaf) -match '^agent-build-test-[a-f0-9]{32}$') {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
