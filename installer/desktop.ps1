param(
    [ValidateSet('Start','Stop','Credentials','CheckStopped')][string]$Action = 'Start',
    [string]$WorkspacePath = (Join-Path $env:LOCALAPPDATA 'AgentStudio\workspace'),
    [switch]$Headless
)
$ErrorActionPreference = 'Stop'
$install = $PSScriptRoot
$workspace = [IO.Path]::GetFullPath($WorkspacePath)
$data = Join-Path $workspace 'data'
$secretsFile = Join-Path $data 'local-secrets.xml'
$stateFile = Join-Path $data 'desktop-process.json'
$jar = Join-Path $install 'app\agent-platform.jar'
$java = Join-Path $install 'runtime\bin\java.exe'
$launchLock = $null
$statusWindow = $null

function Show-Notice([string]$message, [switch]$Failure) {
    if ($Headless) { Write-Output $message; return }
    Add-Type -AssemblyName System.Windows.Forms
    $icon = if ($Failure) { 'Error' } else { 'Information' }
    [Windows.Forms.MessageBox]::Show($message, 'Agent Studio', 'OK', $icon) | Out-Null
}
function Show-Credentials {
    $savedSecrets = Import-Clixml -LiteralPath $secretsFile
    if ($Headless) { Write-Output 'Initial credentials are stored with Windows DPAPI; not printed.'; return }
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object Windows.Forms.Form
    $dialog.Text = 'Agent Studio - 初始登录信息'
    $dialog.ClientSize = New-Object Drawing.Size(590, 205)
    $dialog.StartPosition = 'CenterScreen'
    $dialog.FormBorderStyle = 'FixedDialog'
    $dialog.MaximizeBox = $false
    $label = New-Object Windows.Forms.Label
    $label.SetBounds(20, 18, 550, 55)
    $label.Text = "账号：local_admin`r`n这是此电脑首次启动生成的密码。网页改密后请使用新密码。"
    $password = New-Object Windows.Forms.TextBox
    $password.SetBounds(20, 80, 550, 28)
    $password.ReadOnly = $true
    $password.UseSystemPasswordChar = $true
    $password.Text = $savedSecrets.LOCAL_ADMIN_PASSWORD.GetNetworkCredential().Password
    $copy = New-Object Windows.Forms.Button
    $copy.SetBounds(20, 122, 160, 34)
    $copy.Text = '复制初始密码'
    $copy.Add_Click({ [Windows.Forms.Clipboard]::SetText($password.Text); $copy.Text = '已复制，请粘贴登录' })
    $close = New-Object Windows.Forms.Button
    $close.SetBounds(400, 122, 170, 34)
    $close.Text = '关闭并继续'
    $close.DialogResult = 'OK'
    $dialog.Controls.AddRange(@($label, $password, $copy, $close))
    try { $dialog.ShowDialog() | Out-Null } finally { $password.Text = ''; $dialog.Dispose() }
}
function Get-WorkspaceProcess {
    if (-not (Test-Path -LiteralPath $stateFile)) { return $null }
    $saved = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $running = Get-Process -Id $saved.Id -ErrorAction SilentlyContinue
    if (-not $running -or $running.StartTime.ToUniversalTime().Ticks -ne $saved.StartTicks) { return $null }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId = $($running.Id)"
    if (-not $info.CommandLine -or -not $info.CommandLine.Contains(('"' + $jar + '"')) -or
        -not $info.CommandLine.Contains(('"-Dagent.studio.workspace=' + $workspace + '"'))) {
        throw '运行进程身份不匹配；没有停止任何进程。'
    }
    return $running
}
function Open-Workspace([int]$Port) {
    $url = "http://127.0.0.1:$Port/"
    if ($Headless) { Write-Output "Ready: $url" } else { Start-Process $url -WindowStyle Hidden }
}

try {
    if ($Action -in @('Stop','CheckStopped') -and -not (Test-Path -LiteralPath $data)) { return }
    if ($Action -eq 'Credentials' -and -not (Test-Path -LiteralPath $secretsFile)) { throw '请先启动一次 Agent Studio。' }
    if (-not (Test-Path -LiteralPath $data)) {
        New-Item -ItemType Directory -Path $data -Force | Out-Null
        $acl = Get-Acl -LiteralPath $data
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value, 'S-1-5-18', 'S-1-5-32-544')) {
            $rule = New-Object Security.AccessControl.FileSystemAccessRule([Security.Principal.SecurityIdentifier]::new($sid), 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        Set-Acl -LiteralPath $data -AclObject $acl
    }
    try { $launchLock = [IO.File]::Open((Join-Path $data 'launcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Agent Studio 正在启动或停止，请稍后再试。' }
    $running = Get-WorkspaceProcess
    if ($Action -eq 'CheckStopped') {
        if ($running) { throw '请先从开始菜单选择“停止 Agent Studio”，再安装或卸载。' }
        return
    }
    if ($Action -eq 'Stop') {
        if ($running) { Stop-Process -Id $running.Id; if (-not $running.WaitForExit(15000)) { throw '停止超时，请稍后重试。' } }
        Show-Notice 'Agent Studio 已停止，数据库和项目成果已保留。'
        return
    }
    if ($Action -eq 'Credentials') { Show-Credentials; return }
    if ($running) {
        $saved = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
        Open-Workspace $saved.Port
        return
    }
    if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $jar)) { throw '安装文件不完整，请重新安装。用户数据不会被覆盖。' }
    $manifest = Get-Content -LiteralPath (Join-Path $install 'package.json') -Raw | ConvertFrom-Json
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $manifest.jarSha256) { throw '程序文件校验失败，请重新安装可信安装包。' }
    $firstRun = -not (Test-Path -LiteralPath $secretsFile)
    if ($firstRun) {
        if (Test-Path -LiteralPath (Join-Path $data 'agent-local.mv.db')) { throw '数据库存在但密钥丢失。请恢复原密钥，不会生成新密钥覆盖旧数据。' }
        $secrets = @{}
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            foreach ($name in @('JWT_SECRET','SETTINGS_ENCRYPTION_KEY','LOCAL_DB_PASSWORD','LOCAL_ADMIN_PASSWORD')) {
                $bytes = New-Object byte[] 48
                $rng.GetBytes($bytes)
                $secrets[$name] = New-Object Management.Automation.PSCredential($name, (ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force))
            }
            $secrets | Export-Clixml -LiteralPath $secretsFile
        } finally { $rng.Dispose() }
    }
    $secrets = Import-Clixml -LiteralPath $secretsFile
    $port = 18881
    while ($port -lt 18901) {
        $probe = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
        try { $probe.Start(); break } catch { $port++ } finally { $probe.Stop() }
    }
    if ($port -ge 18901) { throw '本机端口 18881–18900 均被占用，请关闭冲突程序后重试。' }
    if (-not $Headless) {
        Add-Type -AssemblyName System.Windows.Forms
        $statusWindow = New-Object Windows.Forms.Form
        $statusWindow.Text = 'Agent Studio'
        $statusWindow.ClientSize = New-Object Drawing.Size(440, 85)
        $statusWindow.StartPosition = 'CenterScreen'
        $statusWindow.ControlBox = $false
        $label = New-Object Windows.Forms.Label
        $label.SetBounds(20, 25, 400, 45)
        $label.Text = '正在启动本地工作台，首次启动需要初始化数据库……'
        $statusWindow.Controls.Add($label)
        $statusWindow.Show()
        [Windows.Forms.Application]::DoEvents()
    }
    $oldValues = @{}
    try {
        # Do not inherit host model keys, external Spring config, or JVM injection options.
        $names = @(Get-ChildItem Env: | Where-Object Name -Match '^(SPRING_|SERVER_|APP_|LLM_|JWT_|SETTINGS_|ADMIN_|DB_|FLYWAY_|WORKER_|SANDBOX_|LOCAL_DB_|LOCAL_ADMIN_|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|SHOW_SQL$)' | Select-Object -ExpandProperty Name)
        $names += @($secrets.Keys)
        foreach ($name in ($names | Select-Object -Unique)) {
            $oldValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        }
        foreach ($name in $secrets.Keys) { [Environment]::SetEnvironmentVariable($name, $secrets[$name].GetNetworkCredential().Password, 'Process') }
        $arguments = @('-Xms128m','-Xmx1024m', ('"-Dagent.studio.workspace=' + $workspace + '"'), '-jar', ('"' + $jar + '"'),
            '--spring.config.location=classpath:/application.yml,classpath:/application-local.yml', '--spring.profiles.active=local',
            '--server.address=127.0.0.1', "--server.port=$port")
        $running = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $workspace -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $data 'desktop-stdout.log') -RedirectStandardError (Join-Path $data 'desktop-stderr.log')
        @{Id=$running.Id;StartTicks=$running.StartTime.ToUniversalTime().Ticks;Port=$port} | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    } finally {
        foreach ($name in $oldValues.Keys) { [Environment]::SetEnvironmentVariable($name, $oldValues[$name], 'Process') }
    }
    $ready = $false
    for ($attempt=0; $attempt -lt 180; $attempt++) {
        $running.Refresh()
        if ($running.HasExited) { throw "启动失败。日志目录：$data" }
        try { $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 1; $ready = $response.StatusCode -eq 200 } catch { }
        if ($ready) { break }
        if ($statusWindow) { [Windows.Forms.Application]::DoEvents() }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) {
        $owned = Get-WorkspaceProcess
        if ($owned) { Stop-Process -Id $owned.Id }
        throw "启动超时，已停止本次启动的进程。日志目录：$data"
    }
    if ($statusWindow) { $statusWindow.Close(); $statusWindow.Dispose(); $statusWindow = $null }
    $launchLock.Dispose(); $launchLock = $null
    if ($firstRun) { Show-Credentials }
    Open-Workspace $port
} catch {
    if ($statusWindow) { $statusWindow.Close(); $statusWindow.Dispose(); $statusWindow = $null }
    Show-Notice $_.Exception.Message -Failure
    exit 1
} finally {
    if ($launchLock) { $launchLock.Dispose() }
    if ($statusWindow) { $statusWindow.Dispose() }
}
