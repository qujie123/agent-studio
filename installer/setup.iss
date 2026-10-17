#ifndef PayloadDir
  #error PayloadDir is required
#endif
#ifndef OutputPath
  #error OutputPath is required
#endif
#ifndef AppVersion
  #define AppVersion "1.7.2"
#endif

[Setup]
AppId={{DE26B43D-6C16-4E49-8DD1-6B91D07331A4}
AppName=Agent Studio
AppVersion={#AppVersion}
AppPublisher=Agent Studio
DefaultDirName={localappdata}\Programs\AgentStudio
DefaultGroupName=Agent Studio
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
OutputDir={#OutputPath}
OutputBaseFilename=AgentStudio-{#AppVersion}-windows-x64-setup
UninstallDisplayIcon={app}\runtime\bin\java.exe
CloseApplications=no
RestartApplications=no
DisableProgramGroupPage=yes
SetupLogging=yes

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式 / Create a desktop shortcut"; Flags: checkedonce

[Files]
Source: "{#PayloadDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Agent Studio"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\desktop.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\runtime\bin\java.exe"
Name: "{autodesktop}\Agent Studio"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\desktop.ps1"""; WorkingDir: "{app}"; IconFilename: "{app}\runtime\bin\java.exe"; Tasks: desktopicon
Name: "{group}\停止 Agent Studio"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\desktop.ps1"" -Action Stop"; WorkingDir: "{app}"
Name: "{group}\查看初始登录信息"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\desktop.ps1"" -Action Credentials"; WorkingDir: "{app}"
Name: "{group}\准备 Python 预览（需 Docker）"; Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -NoExit -ExecutionPolicy Bypass -File ""{app}\scripts\prepare-python-preview.ps1"""; WorkingDir: "{app}"
Name: "{group}\使用说明"; Filename: "{app}\使用说明.txt"
Name: "{group}\卸载 Agent Studio"; Filename: "{uninstallexe}"

[Run]
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -STA -WindowStyle Hidden -ExecutionPolicy Bypass -File ""{app}\desktop.ps1"""; Description: "启动 Agent Studio / Launch Agent Studio"; Flags: nowait postinstall skipifsilent

[Code]
function WorkspaceStopped(): Boolean;
var
  ExitCode: Integer;
begin
  Result := True;
  if FileExists(ExpandConstant('{app}\desktop.ps1')) then
    Result := Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'),
      '-NoProfile -NonInteractive -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\desktop.ps1') + '" -Action CheckStopped -Headless',
      ExpandConstant('{app}'), SW_HIDE, ewWaitUntilTerminated, ExitCode) and (ExitCode = 0);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if not WorkspaceStopped() then
    Result := '请先通过开始菜单停止 Agent Studio，再重试。 / Stop Agent Studio from the Start menu before updating.';
end;

function InitializeUninstall(): Boolean;
begin
  Result := WorkspaceStopped();
  if not Result then
    SuppressibleMsgBox('请先通过开始菜单停止 Agent Studio，再卸载。用户数据将保留。 / Stop Agent Studio before uninstalling. Your workspace data will be kept.', mbError, MB_OK, IDOK);
end;
