param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin', [int]$Port = 55439)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
# A dedicated local cluster, never the user's existing PostgreSQL service or business database.
$cluster = Join-Path $project ('target/pg-test-' + [guid]::NewGuid().ToString('N'))
$oldUrl = $env:PG_TEST_URL
$oldUser = $env:PG_TEST_USERNAME
$oldPassword = $env:PG_TEST_PASSWORD
$startAttempted = $false
Push-Location $project
try {
    New-Item -ItemType Directory -Path $cluster | Out-Null
    & "$PostgresBin/initdb.exe" -D $cluster -U postgres -A trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize isolated PostgreSQL cluster' }
    # Separate log handles prevent detached PostgreSQL from keeping a redirected pipeline open.
    $starter = Start-Process -FilePath "$PostgresBin/pg_ctl.exe" -ArgumentList @('-D', ('"' + $cluster + '"'), '-l', ('"' + $cluster + '/server.log"'), '-o', ('"-p ' + $Port + ' -h 127.0.0.1"'), '-w', 'start') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$cluster/start.log" -RedirectStandardError "$cluster/start-error.log"
    $startAttempted = $true
    $null = $starter.Handle # Cache the handle before exit for Windows PowerShell 5.1.
    if (-not $starter.WaitForExit(30000)) { $starter.Kill(); throw 'Timed out starting isolated PostgreSQL cluster' }
    if ($starter.ExitCode -ne 0) { throw 'Could not start isolated PostgreSQL cluster' }
    $env:PG_TEST_URL = "jdbc:postgresql://127.0.0.1:$Port/postgres"
    $env:PG_TEST_USERNAME = 'postgres'
    $env:PG_TEST_PASSWORD = ''
    $ErrorActionPreference = 'Continue'
    & ./mvnw.cmd -B -ntp verify
    $testExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($testExit -ne 0) { throw 'PostgreSQL verification failed' }
} finally {
    $stopped = -not $startAttempted
    if ($startAttempted) {
        & "$PostgresBin/pg_ctl.exe" -D $cluster status *> "$cluster/status.log"
        $databaseStatus = $LASTEXITCODE
        if ($databaseStatus -eq 0) {
            & "$PostgresBin/pg_ctl.exe" -D $cluster -m fast -w stop
            $stopped = $LASTEXITCODE -eq 0
        } elseif ($databaseStatus -eq 3) { $stopped = $true }
    }
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $project 'target'))
    $clusterPath = [IO.Path]::GetFullPath($cluster)
    $liveCluster = @(Get-CimInstance Win32_Process -Filter "name = 'postgres.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($clusterPath) })
    if ($stopped -and $liveCluster.Count -eq 0 -and $clusterPath.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path $clusterPath -Leaf) -match '^pg-test-[a-f0-9]{32}$') {
        Remove-Item -LiteralPath $clusterPath -Recurse -Force
    }
    $env:PG_TEST_URL = $oldUrl
    $env:PG_TEST_USERNAME = $oldUser
    $env:PG_TEST_PASSWORD = $oldPassword
    Pop-Location
}
