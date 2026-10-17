param()
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $project ('target\opensource-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $destination | Out-Null
$files = @()
foreach ($folder in @('src','scripts','docker','installer','.mvn')) {
    $entries = @(Get-ChildItem -LiteralPath (Join-Path $project $folder) -Recurse -Force)
    if ($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'Source export refuses linked files/directories.' }
    $files += @($entries | Where-Object { -not $_.PSIsContainer })
}
foreach ($name in @('pom.xml','package.json','package-lock.json','mvnw','mvnw.cmd','.gitignore','.gitattributes','LICENSE','start-local.cmd','stop-local.cmd','prepare-python-preview.cmd','test.http')) {
    $files += Get-Item -LiteralPath (Join-Path $project $name)
}
foreach ($file in $files) {
    $relative = $file.FullName.Substring($project.Length + 1)
    if ($relative -match '(?i)(^|[\\/])(data|backups|target|node_modules|\.git|\.idea)([\\/]|$)|local-secrets|\.env|\.(mv\.db|trace\.db|log|pem|key|p12|pfx|class|exe|jar)$') {
        throw "Unexpected source export file: $relative"
    }
    $copy = Join-Path $destination $relative
    New-Item -ItemType Directory -Path (Split-Path $copy -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $copy
    if ((Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath $copy).Hash) { throw "Export mismatch: $relative" }
}
# Historical local notes may contain personal paths or runtime evidence: publish the curated docs only.
$public = Join-Path $project 'docs\public'
if (Test-Path -LiteralPath $public) {
    Copy-Item -LiteralPath (Join-Path $public 'README.md'),(Join-Path $public 'CONTRIBUTING.md') -Destination $destination
    New-Item -ItemType Directory -Path (Join-Path $destination 'docs') | Out-Null
    Get-ChildItem -LiteralPath $public -File | Where-Object Name -NotIn 'README.md','CONTRIBUTING.md' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $destination 'docs')
    }
} else {
    Copy-Item -LiteralPath (Join-Path $project 'README.md'),(Join-Path $project 'CONTRIBUTING.md') -Destination $destination
    Copy-Item -LiteralPath (Join-Path $project 'docs') -Destination $destination -Recurse
}
Write-Output $destination
