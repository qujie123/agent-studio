param([string]$Installer, [switch]$Browser)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if (-not $Installer) { $Installer = (Get-Content -LiteralPath (Join-Path $project 'target\windows-package.json') -Raw | ConvertFrom-Json).installer }
$registration = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{DE26B43D-6C16-4E49-8DD1-6B91D07331A4}_is1'
if (Get-ItemProperty -LiteralPath $registration -ErrorAction SilentlyContinue) { throw 'An installed Agent Studio already exists. Do not replace it with a test installation.' }
$testRoot = Join-Path $project ('target\installer-test-' + [Guid]::NewGuid().ToString('N'))
$installed = Join-Path $testRoot '安装目录 with spaces'
$workspace = Join-Path $testRoot '用户数据 with spaces'
$launcher = Join-Path $installed 'desktop.ps1'
$stateFile = Join-Path $workspace 'data\desktop-process.json'
$secretsFile = Join-Path $workspace 'data\local-secrets.xml'
$shell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$group = 'Agent Studio'
if (Test-Path -LiteralPath (Join-Path ([Environment]::GetFolderPath('Programs')) $group)) { throw 'Existing Agent Studio shortcuts must not be overwritten by an installer test.' }
$installedByTest = $false
$heldPort = $null
New-Item -ItemType Directory -Path $testRoot | Out-Null
function Assert-That([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Launch([string]$Action = 'Start', [int]$ExpectedExit = 0) {
    & $shell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $launcher -WorkspacePath $workspace -Headless -Action $Action
    Assert-That ($LASTEXITCODE -eq $ExpectedExit) "Launcher $Action returned $LASTEXITCODE; expected $ExpectedExit"
}
function Login {
    $savedSecrets = Import-Clixml -LiteralPath $secretsFile
    $body = @{username='local_admin';password=$savedSecrets.LOCAL_ADMIN_PASSWORD.GetNetworkCredential().Password} | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$script:url/api/users/login" -Method Post -ContentType 'application/json' -Body $body
    return @{Authorization=('Bearer ' + $login.token)}
}
Start-Transcript -LiteralPath (Join-Path $project 'target\windows-package-test.log') -Force | Out-Null
try {
    $setup = Start-Process -FilePath $Installer -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/SP-',('/DIR="' + $installed + '"'),('/GROUP="' + $group + '"'),'/TASKS=',('/LOG="' + (Join-Path $testRoot 'install.log') + '"')) -WindowStyle Hidden -Wait -PassThru
    Assert-That ($setup.ExitCode -eq 0) "Installer failed: $($setup.ExitCode)"
    $installedByTest = $true
    Assert-That (Test-Path -LiteralPath $launcher) 'Installed launcher missing'
    Assert-That (Test-Path -LiteralPath (Join-Path $installed 'runtime\bin\java.exe')) 'Bundled Java missing'
    foreach ($requiredNotice in @('LICENSE','docs\LIMITATIONS.md','docs\THIRD_PARTY_NOTICES.md','runtime\legal\java.base\LICENSE')) {
        Assert-That (Test-Path -LiteralPath (Join-Path $installed $requiredNotice)) "Required notice missing: $requiredNotice"
    }
    Assert-That (-not (Test-Path -LiteralPath (Join-Path $installed 'data'))) 'Installer unexpectedly contains user data'
    $links = @(Get-ChildItem -LiteralPath (Join-Path ([Environment]::GetFolderPath('Programs')) $group) -Filter '*.lnk')
    Assert-That ($links.Count -ge 6) 'Start menu shortcuts missing'
    $shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $links[0].DirectoryName 'Agent Studio.lnk'))
    Assert-That ($shortcut.Arguments.Contains($launcher)) 'Shortcut points to a different installation'
    $oldJava = $env:JAVA_HOME
    $oldPath = $env:PATH
    $oldKey = $env:LLM_API_KEY
    $oldBase = $env:LLM_BASE_URL
    try {
        $env:JAVA_HOME = $null
        $env:PATH = "$env:SystemRoot\System32;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
        $env:LLM_API_KEY = 'installer-test-host-key-not-for-model-use'
        $env:LLM_BASE_URL = 'https://example.invalid/v1'
        $heldPort = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 18881)
        $heldPort.Start()
        Launch
    } finally {
        $env:JAVA_HOME = $oldJava; $env:PATH = $oldPath; $env:LLM_API_KEY = $oldKey; $env:LLM_BASE_URL = $oldBase
        if ($heldPort) { $heldPort.Stop(); $heldPort = $null }
    }
    $first = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    Assert-That ($first.Port -ne 18881) 'Port conflict fallback failed'
    $script:url = "http://127.0.0.1:$($first.Port)"
    $headers = Login
    $status = Invoke-RestMethod "$url/api/system/status" -Headers $headers
    Assert-That (-not $status.ephemeral) 'Installer started the disposable demo profile'
    foreach ($endpoint in @('/api/admin/model-settings','/api/me/model-settings')) {
        $model = Invoke-RestMethod ($url + $endpoint) -Headers $headers
        Assert-That (-not $model.hasKey -and $model.baseUrl -eq '' -and $model.mode -ne 'API') 'Installed app inherited a model key or address'
    }
    $acl = Get-Acl -LiteralPath (Join-Path $workspace 'data')
    Assert-That $acl.AreAccessRulesProtected 'Data ACL inherits public permissions'
    $allowedSids = @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18', 'S-1-5-32-544')
    foreach ($rule in $acl.Access) {
        Assert-That ($rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -in $allowedSids) 'Unexpected account has a data ACL entry'
    }
    $connections = @(Get-NetTCPConnection -OwningProcess $first.Id -State Listen)
    Assert-That ($connections.Count -gt 0) 'No listening socket found'
    foreach ($connection in $connections) { Assert-That ($connection.LocalAddress -eq '127.0.0.1') 'Server is exposed beyond loopback' }
    $secretsHash = (Get-FileHash -LiteralPath $secretsFile -Algorithm SHA256).Hash
    $body = @{name='Installer persistence check';description='Disposable installer test';systemPrompt='test';modelName='demo-model';status='ACTIVE'} | ConvertTo-Json
    $agent = Invoke-RestMethod "$url/api/agents" -Method Post -Headers $headers -ContentType 'application/json' -Body $body
    Launch
    $second = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    Assert-That ($second.Id -eq $first.Id -and $second.StartTicks -eq $first.StartTicks) 'Repeated launch created another server'
    Launch 'CheckStopped' 1
    Launch 'Credentials'
    Write-Output 'PASS: real install, menu entries, bundled Java, port fallback, private loopback/ACL, independent secrets, no inherited Key, duplicate start and running guard.'
    Launch 'Stop'
    Launch 'CheckStopped'
    Launch
    $second = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $script:url = "http://127.0.0.1:$($second.Port)"
    $headers = Login
    $preserved = Invoke-RestMethod "$url/api/agents/$($agent.id)" -Headers $headers
    Assert-That ($preserved.name -eq $agent.name) 'Data was lost after restarting'
    Assert-That ((Get-FileHash -LiteralPath $secretsFile -Algorithm SHA256).Hash -eq $secretsHash) 'Keys changed after restart'
    # Keep only paths and non-sensitive status for an optional browser acceptance pass.
    @{root=$testRoot;installed=$installed;workspace=$workspace;url=$url;group=$group} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $project 'target\windows-package-test.json') -Encoding UTF8
    Write-Output 'PASS: new account login and persistent business data after stop/restart.'
    if ($Browser) {
        $oldUrl = $env:INSTALLER_TEST_URL; $oldPassword = $env:INSTALLER_TEST_PASSWORD
        try {
            $env:INSTALLER_TEST_URL = $url
            $env:INSTALLER_TEST_PASSWORD = (Import-Clixml -LiteralPath $secretsFile).LOCAL_ADMIN_PASSWORD.GetNetworkCredential().Password
            & node (Join-Path $PSScriptRoot 'browser-installed.cjs')
            Assert-That ($LASTEXITCODE -eq 0) 'Installed browser acceptance failed'
        } finally { $env:INSTALLER_TEST_URL = $oldUrl; $env:INSTALLER_TEST_PASSWORD = $oldPassword }
    }
    Launch 'Stop'
    $dbFile = Join-Path $workspace 'data\agent-local.mv.db'
    $dbHash = (Get-FileHash -LiteralPath $dbFile -Algorithm SHA256).Hash
    $uninstall = Start-Process -FilePath (Join-Path $installed 'unins000.exe') -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART',('/LOG="' + (Join-Path $testRoot 'uninstall.log') + '"')) -WindowStyle Hidden -Wait -PassThru
    Assert-That ($uninstall.ExitCode -eq 0) "Uninstall failed: $($uninstall.ExitCode)"
    $installedByTest = $false
    Assert-That (-not (Test-Path -LiteralPath $launcher)) 'Uninstall did not remove program files'
    Assert-That (-not (Test-Path -LiteralPath (Join-Path ([Environment]::GetFolderPath('Programs')) $group))) 'Uninstall did not remove menu entries'
    Assert-That ((Get-FileHash -LiteralPath $dbFile -Algorithm SHA256).Hash -eq $dbHash) 'Uninstall changed user database'
    Assert-That ((Get-FileHash -LiteralPath $secretsFile -Algorithm SHA256).Hash -eq $secretsHash) 'Uninstall changed user secrets'
    Write-Output "PASS: uninstall removed program/menu entries and preserved the database and keys. Evidence: $testRoot"
} finally {
    if ($heldPort) { $heldPort.Stop() }
    if ($installedByTest -and (Test-Path -LiteralPath $launcher)) {
        & $shell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File $launcher -WorkspacePath $workspace -Headless -Action Stop
        Write-Output "Test installation retained for diagnosis: $installed"
    }
    Stop-Transcript | Out-Null
}
