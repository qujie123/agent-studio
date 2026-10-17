param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$InnoCompiler,
    [ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version = '1.7.2'
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if (-not $JdkPath) { $JdkPath = Split-Path (Split-Path (Get-Command java -ErrorAction Stop).Source -Parent) -Parent }
if (-not $InnoCompiler) { $InnoCompiler = Join-Path $project 'target\installer-tools\InnoSetup\ISCC.exe' }
foreach ($required in @((Join-Path $JdkPath 'bin\jlink.exe'), $InnoCompiler)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing build tool: $required" }
}
$oldJava = $env:JAVA_HOME
$env:JAVA_HOME = $JdkPath
try {
    . (Join-Path $PSScriptRoot 'build.ps1')
    $jar = Get-ProjectJar -ProjectPath $project
    # Fresh allowlisted payload: never recursively copy the project or its runtime data.
    $staging = Join-Path $project ('target\windows-package-' + [Guid]::NewGuid().ToString('N'))
    $payload = Join-Path $staging 'payload'
    $output = Join-Path $staging 'output'
    New-Item -ItemType Directory -Path $payload, $output, (Join-Path $payload 'app'), (Join-Path $payload 'scripts'), (Join-Path $payload 'docker\python-preview') -Force | Out-Null
    Copy-Item -LiteralPath $jar -Destination (Join-Path $payload 'app\agent-platform.jar')
    Copy-Item -LiteralPath (Join-Path $project 'LICENSE') -Destination $payload
    New-Item -ItemType Directory -Path (Join-Path $payload 'docs') -Force | Out-Null
    $docs = Join-Path $project 'docs\public'
    if (-not (Test-Path -LiteralPath $docs)) { $docs = Join-Path $project 'docs' }
    foreach ($document in @('LIMITATIONS.md','THIRD_PARTY_NOTICES.md')) {
        Copy-Item -LiteralPath (Join-Path $docs $document) -Destination (Join-Path $payload 'docs')
    }
    # BOM is required for Chinese source text when launched by Windows PowerShell 5.1.
    $utf8Bom = New-Object Text.UTF8Encoding($true)
    foreach ($relative in @('desktop.ps1','使用说明.txt')) {
        [IO.File]::WriteAllText((Join-Path $payload $relative), [IO.File]::ReadAllText((Join-Path $project ('installer\' + $relative))), $utf8Bom)
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'prepare-python-preview.ps1') -Destination (Join-Path $payload 'scripts')
    Copy-Item -LiteralPath (Join-Path $project 'docker\python-preview\Dockerfile') -Destination (Join-Path $payload 'docker\python-preview')
    & (Join-Path $JdkPath 'bin\jlink.exe') --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.mscapi,jdk.charsets,jdk.localedata,jdk.zipfs,jdk.management,jdk.security.auth --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output (Join-Path $payload 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'Bundled Java runtime creation failed.' }
    $manifest = @{version=$Version;jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash;javaVersion=(& (Join-Path $payload 'runtime\bin\java.exe') --version | Select-Object -First 1)}
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $payload 'package.json') -Encoding UTF8
    $forbidden = @(Get-ChildItem -LiteralPath $payload -File -Recurse | Where-Object { $_.Name -match '\.(mv\.db|trace\.db|log|pem|key)$|^local-secrets\.xml$|\.java$|\.env' -or $_.FullName -match '\\(backups|\.idea|src)\\' })
    if ($forbidden.Count -gt 0) { throw 'Unexpected private/development files in installer payload.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $payload 'app\agent-platform.jar'))
    try {
        $badEntries = @($archive.Entries | Where-Object FullName -Match '(^|/)(data|backups|\.idea)/|local-secrets|\.(mv\.db|trace\.db|pem|key|java)$|(^|/)\.env')
        if ($badEntries.Count -gt 0) { throw 'Unexpected private/development files inside the application JAR.' }
    } finally { $archive.Dispose() }
    & $InnoCompiler "/DPayloadDir=$payload" "/DOutputPath=$output" "/DAppVersion=$Version" (Join-Path $project 'installer\setup.iss')
    if ($LASTEXITCODE -ne 0) { throw 'Windows installer compilation failed.' }
    $release = Join-Path $project ('dist\windows\' + $Version)
    New-Item -ItemType Directory -Path $release -Force | Out-Null
    $setupName = "AgentStudio-$Version-windows-x64-setup.exe"
    Copy-Item -LiteralPath (Join-Path $output $setupName) -Destination (Join-Path $release $setupName) -Force
    Copy-Item -LiteralPath (Join-Path $payload '使用说明.txt') -Destination $release -Force
    ((Get-FileHash -LiteralPath (Join-Path $release $setupName) -Algorithm SHA256).Hash + '  ' + $setupName) | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding ASCII
    @{payload=$payload;installer=(Join-Path $release $setupName);version=$Version;jarSha256=$manifest.jarSha256} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $project 'target\windows-package.json') -Encoding UTF8
    Write-Host "Installer ready: $(Join-Path $release $setupName)"
} finally { $env:JAVA_HOME = $oldJava }
