param(
    [ValidateSet('Start','Stop','Credentials')][string]$Action = 'Start',
    [ValidateRange(1024,65535)][int]$Port = 8081,
    [switch]$NoBrowser,
    [switch]$ShowCredentials,
    [switch]$UseExistingRuntime
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$data = Join-Path $project 'data'
$secretsFile = Join-Path $data 'local-secrets.xml'
$pidFile = Join-Path $data 'local-process.json'
$runtimeJar = Join-Path $data 'agent-platform-local.jar'
. (Join-Path $PSScriptRoot 'build.ps1')
function Get-LocalProcess {
    if (-not (Test-Path -LiteralPath $pidFile)) { return $null }
    $saved = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
    if (-not $process -or $process.StartTime.ToUniversalTime().Ticks -ne $saved.StartTicks) { return $null }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
    if (-not $info.CommandLine.Contains($runtimeJar)) { throw 'Process identity mismatch; no process was stopped.' }
    return $process
}
if ($Action -eq 'Stop') {
    $process = Get-LocalProcess
    if ($process) { Stop-Process -Id $process.Id; $process.WaitForExit(15000) | Out-Null; Write-Host 'Local workspace stopped. Data is preserved.' }
    else { Write-Host 'Local workspace is not running.' }
    return
}
if ($Action -eq 'Credentials' -and -not (Test-Path -LiteralPath $secretsFile)) { throw 'Start the local workspace first.' }
if (-not (Test-Path -LiteralPath $data)) {
    New-Item -ItemType Directory -Path $data | Out-Null
    $acl = Get-Acl -LiteralPath $data
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18', 'S-1-5-32-544')) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule([Security.Principal.SecurityIdentifier]::new($sid), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $data -AclObject $acl
}
if (-not (Test-Path -LiteralPath $secretsFile)) {
    if (Test-Path -LiteralPath (Join-Path $data 'agent-local.mv.db')) { throw 'Database exists but its secrets are missing. Restore the original secrets; do not generate new keys.' }
    $secrets = @{}
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        foreach ($name in @('JWT_SECRET','SETTINGS_ENCRYPTION_KEY','LOCAL_DB_PASSWORD','LOCAL_ADMIN_PASSWORD')) {
            $bytes = New-Object byte[] 48
            $rng.GetBytes($bytes)
            $value = [Convert]::ToBase64String($bytes)
            $secrets[$name] = New-Object Management.Automation.PSCredential($name, (ConvertTo-SecureString $value -AsPlainText -Force))
        }
        # Windows DPAPI: readable only by this Windows account on this computer.
        $secrets | Export-Clixml -LiteralPath $secretsFile
    } finally { $rng.Dispose() }
}
$secrets = Import-Clixml -LiteralPath $secretsFile
if ($Action -eq 'Credentials' -or $ShowCredentials) {
    Write-Host 'Local account: local_admin'
    Write-Host ('Initial password: ' + $secrets.LOCAL_ADMIN_PASSWORD.GetNetworkCredential().Password)
    Write-Host 'If you changed the password in the web app, use that new password instead.'
    Write-Host 'Keep this password private. Do not share screenshots of this console.'
    if ($Action -eq 'Credentials') { return }
}
$process = Get-LocalProcess
if ($process) {
    $saved = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $url = "http://127.0.0.1:$($saved.Port)"
    Write-Host "Already running: $url (PID $($process.Id)). Stop and start to load a new build."
    if (-not $NoBrowser) { Start-Process $url -WindowStyle Hidden }
    return
}
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is in use. No existing process was stopped." }
$java = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/java.exe")) { "$env:JAVA_HOME/bin/java.exe" } else { (Get-Command java -ErrorAction Stop).Source }
$oldValues = @{}
Push-Location $project
try {
    if ($UseExistingRuntime) {
        $stampFile = Join-Path $project 'target/build-inputs.json'
        if (-not (Test-Path -LiteralPath $runtimeJar) -or -not (Test-Path -LiteralPath $stampFile)) { throw 'No previously verified runtime is available. Use a normal start after completing verification.' }
        $stamp = Get-Content -LiteralPath $stampFile -Raw | ConvertFrom-Json
        if ((Get-FileHash -LiteralPath $runtimeJar -Algorithm SHA256).Hash -ne $stamp.JarHash) { throw 'Existing runtime does not match the verified build fingerprint; nothing started.' }
        Write-Host 'Starting the existing verified runtime. Pending source changes are not deployed.'
    } else {
        $builtJar = Get-ProjectJar -ProjectPath $project
        Copy-Item -LiteralPath $builtJar -Destination $runtimeJar -Force
    }
    foreach ($name in $secrets.Keys) {
        $oldValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $secrets[$name].GetNetworkCredential().Password, 'Process')
    }
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $runtimeJar + '"'), '--spring.profiles.active=local', "--server.port=$Port") -WorkingDirectory $project -WindowStyle Hidden -RedirectStandardOutput (Join-Path $data 'local-stdout.log') -RedirectStandardError (Join-Path $data 'local-stderr.log') -PassThru
    @{Id=$process.Id;StartTicks=$process.StartTime.ToUniversalTime().Ticks;Port=$Port} | ConvertTo-Json | Set-Content -LiteralPath $pidFile -Encoding UTF8
    $url = "http://127.0.0.1:$Port"
    $ready = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $process.Refresh()
        if ($process.HasExited) { throw "Startup failed. Check data/local-stdout.log and data/local-stderr.log." }
        try { $response = Invoke-WebRequest -Uri "$url/" -UseBasicParsing -TimeoutSec 1; $ready = $response.StatusCode -eq 200 } catch { }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Still starting. Check data/local-stdout.log, then open $url. Do not start another instance." }
    Write-Host "Local workspace ready: $url (PID $($process.Id)). Data survives restarts."
    Write-Host 'Credentials: powershell -ExecutionPolicy Bypass -File scripts/local.ps1 -Action Credentials'
    if (-not $NoBrowser) { Start-Process $url -WindowStyle Hidden }
} finally {
    foreach ($name in $oldValues.Keys) { [Environment]::SetEnvironmentVariable($name, $oldValues[$name], 'Process') }
    Pop-Location
}
