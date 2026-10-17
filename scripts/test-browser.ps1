param([ValidateRange(1024,65535)][int]$Port = 18080, [switch]$WithoutDocker)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'build.ps1')
$process = $null
$oldUrl = $env:APP_URL
$oldGui = $env:ARTIFACT_TEST_GUI
$oldNodePath = $env:NODE_PATH
Push-Location $project
try {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw 'Test port is occupied; no existing service was stopped.' }
    $builtJar = Get-ProjectJar -ProjectPath $project
    $directory = Join-Path $project 'target/verification'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    # A separate runtime avoids Windows locking Maven's build artifact.
    $runtime = Join-Path $directory 'demo-runtime.jar'
    Copy-Item -LiteralPath $builtJar -Destination $runtime -Force
    $java = (Get-Command java -ErrorAction Stop).Source
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $runtime + '"'), '--spring.profiles.active=demo', '--app.sandbox.enabled=true', "--server.port=$Port") -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $project 'target/v1.7-demo.log') -RedirectStandardError (Join-Path $project 'target/v1.7-demo-error.log')
    $startTicks = $process.StartTime.ToUniversalTime().Ticks
    $env:APP_URL = "http://127.0.0.1:$Port"
    $ready = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) { throw 'Isolated demo failed to start; inspect target/v1.7-demo-error.log.' }
        try { $ready = (Invoke-WebRequest -Uri $env:APP_URL -UseBasicParsing -TimeoutSec 1).StatusCode -eq 200 } catch { }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw 'Isolated demo did not become ready.' }
    $env:ARTIFACT_TEST_GUI = if ($WithoutDocker) { 'false' } else { 'true' }
    $ErrorActionPreference = 'Continue'
    if ($WithoutDocker) { & npm.cmd run test:ui:without-docker *> target/browser-final.log }
    else { & npm.cmd run test:ui *> target/browser-final.log }
    $browserExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    Get-Content -LiteralPath target/browser-final.log -Tail 28
    if ($browserExit -ne 0) { throw 'Browser verification failed.' }
} finally {
    if ($process) {
        $process.Refresh()
        if (-not $process.HasExited) {
            $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
            if ($process.StartTime.ToUniversalTime().Ticks -ne $startTicks -or -not $info.CommandLine.Contains($runtime)) { throw 'Test process identity mismatch; nothing stopped.' }
            Stop-Process -Id $process.Id
            $process.WaitForExit(15000) | Out-Null
        }
    }
    $env:APP_URL = $oldUrl
    $env:ARTIFACT_TEST_GUI = $oldGui
    $env:NODE_PATH = $oldNodePath
    Pop-Location
}
