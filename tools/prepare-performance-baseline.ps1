param(
    [string]$SourceCommit = '4c362999d3c33fdcb6cfd90ed86e16850e795d43',
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Windows and default macOS volumes ignore path case; Linux does not.
$pathComparison = if ($IsLinux) { [StringComparison]::Ordinal } else { [StringComparison]::OrdinalIgnoreCase }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path (Join-Path $projectRoot '.tooling') 'performance-baseline-project' }
$destination = [IO.Path]::GetFullPath($OutputDirectory)
$permittedRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tooling')) + [IO.Path]::DirectorySeparatorChar
if (-not $destination.StartsWith($permittedRoot, $pathComparison)) {
    throw 'Baseline output must be a new directory inside this project .tooling folder.'
}
if (Test-Path -LiteralPath $destination) { throw "Refusing to overwrite an existing baseline: $destination" }
Push-Location -LiteralPath $projectRoot
try {
    $resolvedCommit = & git rev-parse --verify "$SourceCommit^{commit}"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the baseline source commit.' }
    $archive = Join-Path ([IO.Path]::GetTempPath()) ("mooscles-baseline-" + [Guid]::NewGuid().ToString('N') + '.zip')
    try {
        & git archive --format=zip "--output=$archive" $resolvedCommit
        if ($LASTEXITCODE -ne 0) { throw 'Could not archive the historical source.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $destination
    }
    finally { if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive } }
    # Configuration only: application sources remain exactly the baseline
    # commit. Use the same R8/signing/profileable settings for both captures.
    $overlay = @((Join-Path 'app' 'build.gradle.kts'), (Join-Path (Join-Path (Join-Path 'app' 'src') 'performance') 'AndroidManifest.xml'))
    foreach ($relative in $overlay) {
        $target = Join-Path $destination $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $projectRoot $relative) -Destination $target
    }
    $local = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $local) { Copy-Item -LiteralPath $local -Destination (Join-Path $destination 'local.properties') }
    [ordered]@{
        sourceCommit = $resolvedCommit.Trim()
        configurationOverlay = @($overlay | ForEach-Object {
            @{ path = $_; sha256 = (Get-FileHash -LiteralPath (Join-Path $projectRoot $_) -Algorithm SHA256).Hash.ToLowerInvariant() }
        })
        sourcesModified = $false
        runtimeMeasured = $false
        note = 'Only shared measurement build configuration is overlaid. No application code is modified.'
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'performance-baseline-source.json') -Encoding utf8
    Write-Output "Baseline prepared: $destination"
    Write-Output "Source commit: $resolvedCommit"
    Write-Output 'Build :app:assemblePerformance in that directory using the same JDK, SDK, Gradle home and signing key as the main project.'
}
finally { Pop-Location }
