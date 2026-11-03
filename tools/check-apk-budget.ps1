<#
.SYNOPSIS
Checks the debug APK against the size budget of the current phase in tools/apk-budget.json.

.DESCRIPTION
Prints the numbers and fails (exit 1) when any limit is exceeded:
  * the whole APK may grow at most maxGrowthBytes over baseApkBytes (the debug APK of
    baseCommit, the main commit the phase started from, as built by CI);
  * the fonts, summed as the compressed size of the assets/composeResources/<package>/font*/
    entries, may take at most maxFontBytes;
  * fonts and icons together may take at most maxFontsAndIconsBytes (the spec's 1 MB).
    The icons are Kotlin code (ImageVectors), not resources, and the APK's classes*.dex cannot
    be split per package without a dex tool. So they are measured as an upper bound: the summed
    size of the compiled .class files of com.mooscles.designsystem.icons.** in the :designsystem
    Android build output (designsystem/build/**/classes/**), which dex only shrinks. When the
    directory has no icon classes (the module was not built), the check fails.

.EXAMPLE
pwsh tools/check-apk-budget.ps1 -Apk app/build/outputs/apk/debug/app-debug.apk
#>
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Config = (Join-Path $PSScriptRoot 'apk-budget.json'),
    [string]$DesignSystemBuild = (Join-Path (Split-Path $PSScriptRoot -Parent) 'designsystem/build')
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
if (-not (Test-Path -LiteralPath $Config -PathType Leaf)) { throw "Budget file not found: $Config" }
$budget = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
foreach ($field in 'baseCommit', 'baseApkBytes', 'maxGrowthBytes', 'maxFontBytes', 'maxFontsAndIconsBytes') {
    if ($null -eq $budget.$field) { throw "$Config has no '$field'." }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$apkBytes = (Get-Item -LiteralPath $apkPath).Length
$fontPattern = '^assets/composeResources/[^/]+/font(-[^/]+)?/[^/]+$'
$fontBytes = [long]0
$fontEntries = @()
$zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match $fontPattern) {
            $fontBytes += $entry.CompressedLength
            $fontEntries += "- ``$($entry.FullName)``: $($entry.CompressedLength) bytes compressed ($($entry.Length) uncompressed)"
        }
    }
}
finally { $zip.Dispose() }

# Icons: the upper bound described above. Only the Android target's classes (kotlin/ and
# the AGP javac output), never the iOS klib.
$iconFiles = @()
if (Test-Path -LiteralPath $DesignSystemBuild -PathType Container) {
    $iconFiles = @(Get-ChildItem -LiteralPath $DesignSystemBuild -Recurse -Filter '*.class' -File |
        Where-Object {
            $p = $_.FullName.Replace('\', '/')
            $p -match '/com/mooscles/designsystem/icons/' -and $p -notmatch '/(test|androidHostTest|androidDeviceTest|unitTest)/' -and $p -match '/classes/'
        })
}
$iconBytes = [long]0
foreach ($file in $iconFiles) { $iconBytes += $file.Length }
$fontsAndIcons = $fontBytes + $iconBytes
$baseApk = [long]$budget.baseApkBytes
$growth = $apkBytes - $baseApk

function Format-Check([string]$label, [long]$value, [long]$limit) {
    $state = if ($value -le $limit) { 'ok' } else { 'OVER' }
    "| $label | $value | $limit | $state |"
}

$lines = @(
    '### APK budget',
    "``$Apk``: $apkBytes bytes. Base: $baseApk bytes at $($budget.baseCommit).",
    '',
    '| Check | Bytes | Limit | Result |',
    '|---|---:|---:|---|',
    (Format-Check 'APK growth over the base' $growth ([long]$budget.maxGrowthBytes)),
    (Format-Check 'Fonts (compressed)' $fontBytes ([long]$budget.maxFontBytes)),
    (Format-Check 'Fonts and icons (icon classes as an upper bound)' $fontsAndIcons ([long]$budget.maxFontsAndIconsBytes)),
    '',
    "Icon classes: $($iconFiles.Count) files, $iconBytes bytes (upper bound of their dex size).",
    "Font entries: $($fontEntries.Count)."
) + $fontEntries
$lines | Write-Output
if ($env:GITHUB_STEP_SUMMARY) { $lines | Out-File -FilePath $env:GITHUB_STEP_SUMMARY -Append -Encoding utf8 }

$over = @()
if ($growth -gt [long]$budget.maxGrowthBytes) { $over += "the APK grew $growth bytes (limit $($budget.maxGrowthBytes))" }
if ($fontBytes -gt [long]$budget.maxFontBytes) { $over += "the fonts take $fontBytes bytes (limit $($budget.maxFontBytes))" }
if ($fontsAndIcons -gt [long]$budget.maxFontsAndIconsBytes) { $over += "fonts and icons take $fontsAndIcons bytes (limit $($budget.maxFontsAndIconsBytes))" }
if ($iconFiles.Count -eq 0) { $over += 'no icon class was found under the :designsystem build output, so the icons were not measured' }
if ($fontEntries.Count -eq 0) { $over += 'no font entry matched, so the font check cannot have measured anything' }
if ($over.Count -gt 0) {
    Write-Error ("APK budget exceeded: " + ($over -join '; ') + '.') -ErrorAction Continue
    exit 1
}
