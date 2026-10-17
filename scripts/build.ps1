function Get-ProjectJar {
    param([Parameter(Mandatory)][string]$ProjectPath)
    $jar = Join-Path $ProjectPath 'target/agent-platform-0.0.1-SNAPSHOT.jar'
    $stamp = Join-Path $ProjectPath 'target/build-inputs.json'
    $inputs = @(Get-ChildItem -LiteralPath (Join-Path $ProjectPath 'src'), (Join-Path $ProjectPath '.mvn') -File -Recurse)
    $inputs += @(Get-Item -LiteralPath (Join-Path $ProjectPath 'pom.xml'), (Join-Path $ProjectPath 'mvnw'), (Join-Path $ProjectPath 'mvnw.cmd'))
    [string[]]$inputPaths = @($inputs | ForEach-Object { $_.FullName })
    [Array]::Sort($inputPaths, [StringComparer]::OrdinalIgnoreCase)
    $fingerprint = ($inputPaths | ForEach-Object {
        $_.Substring($ProjectPath.Length) + '=' + (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash
    }) -join "`n"
    $current = $false
    if ((Test-Path -LiteralPath $jar) -and (Test-Path -LiteralPath $stamp)) {
        try {
            $saved = Get-Content -LiteralPath $stamp -Raw -Encoding UTF8 | ConvertFrom-Json
            $current = $saved.Inputs -eq $fingerprint -and $saved.JarHash -eq (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
        } catch { $current = $false }
    }
    if (-not $current) {
        Write-Host 'Source or build changed. Verifying and packaging the current project...'
        Push-Location $ProjectPath
        try {
            & ./mvnw.cmd -B -ntp verify | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Build failed; no application was started and the previous runtime was not replaced.' }
            @{Inputs=$fingerprint;JarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -LiteralPath $stamp -Encoding UTF8
        } finally { Pop-Location }
    }
    return $jar
}
