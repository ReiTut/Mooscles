param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateSet('before', 'after')][string]$Stage,
    [Parameter(Mandatory = $true)][ValidateSet('typical', 'large')][string]$Fixture,
    [Parameter(Mandatory = $true)][ValidateSet('start', 'scroll', 'timer-scroll', 'resume', 'cycles')][string]$Scenario,
    [ValidateRange(10, 60)][int]$DurationSeconds = 20,
    [string]$ApkPath,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $projectRoot '.tooling\android-sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { $adb = (Get-Command adb -ErrorAction Stop).Source }
if ($Serial -notmatch '^[A-Za-z0-9._:-]+$') { throw 'Invalid device serial.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectRoot "app\build\outputs\performance\$Stage-$Fixture-$Scenario-$stamp" }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new output directory so previous captures are preserved.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
function Invoke-Device([string[]]$Arguments) {
    $result = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Device command failed: $($Arguments -join ' ')`n$result" }
    return $result
}
if ((Invoke-Device @('get-state')).Trim() -ne 'device') { throw 'A connected, authorized test device is required.' }
$sdk = [int](Invoke-Device @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
if ($sdk -lt 29) { throw 'Use API 29 or later for the profileable Perfetto capture.' }
$packageInfo = Invoke-Device @('shell', 'dumpsys', 'package', 'com.mooscles')
if (-not ($packageInfo -match 'Package \[com\.mooscles\]')) {
    throw 'The com.mooscles application is not installed on this test device.'
}
if ($packageInfo -match '(?m)^\s*flags=.*\bDEBUGGABLE\b') {
    throw 'The installed target is debuggable. Install app-performance.apk before drawing performance conclusions.'
}
$installedPaths = @(Invoke-Device @('shell', 'pm', 'path', 'com.mooscles') | Where-Object { $_ -like 'package:*' })
if ($installedPaths.Count -ne 1) { throw 'Use the single-APK performance build for a comparable capture.' }
$installedApk = Join-Path $OutputDirectory 'installed-target.apk'
Invoke-Device @('pull', $installedPaths[0].Substring(8).Trim(), $installedApk) | Out-Null
$installedSha256 = (Get-FileHash -LiteralPath $installedApk -Algorithm SHA256).Hash.ToLowerInvariant()
$apk = if ($ApkPath) { Get-Item -LiteralPath $ApkPath } else { $null }
$expectedSha256 = if ($apk) { (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null }
if ($expectedSha256 -and $expectedSha256 -ne $installedSha256) {
    throw 'The installed APK does not match -ApkPath. No performance capture was made.'
}
$processId = (Invoke-Device @('shell', 'pidof', 'com.mooscles')).Trim()
if (-not $processId) { throw 'Open the app and prepare the scenario before starting a capture.' }
$packageInfo | Set-Content -LiteralPath (Join-Path $OutputDirectory 'package.txt')
Invoke-Device @('shell', 'getprop') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'device-properties.txt')
Invoke-Device @('shell', 'dumpsys', 'display') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'display.txt')
Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'thermal-before.txt')
Invoke-Device @('shell', 'dumpsys', 'meminfo', 'com.mooscles') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'memory-before.txt')
Invoke-Device @('shell', 'dumpsys', 'gfxinfo', 'com.mooscles', 'reset') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'gfx-reset.txt')
$remoteTrace = "/data/misc/perfetto-traces/mooscles-$stamp.perfetto-trace"
$captureArgs = @('-s', $Serial, 'shell', 'perfetto', '-o', $remoteTrace, '-t', "${DurationSeconds}s",
    '-a', 'com.mooscles', 'sched', 'freq', 'idle', 'am', 'wm', 'gfx', 'view', 'binder_driver', 'dalvik', 'input')
Write-Host "Recording $Stage / $Fixture / $Scenario for $DurationSeconds seconds. Perform the documented scenario now."
$capture = Start-Process -FilePath $adb -ArgumentList $captureArgs -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $OutputDirectory 'perfetto.stdout.txt') `
    -RedirectStandardError (Join-Path $OutputDirectory 'perfetto.stderr.txt')
if (-not $capture.WaitForExit(($DurationSeconds + 20) * 1000)) {
    $capture.Kill()
    throw 'Perfetto did not finish; no performance result should be inferred.'
}
if ($capture.ExitCode -ne 0) { throw "Perfetto failed; inspect logs in $OutputDirectory" }
Invoke-Device @('pull', $remoteTrace, (Join-Path $OutputDirectory 'capture.perfetto-trace')) | Out-Null
Invoke-Device @('shell', 'rm', $remoteTrace) | Out-Null
Invoke-Device @('shell', 'dumpsys', 'gfxinfo', 'com.mooscles', 'framestats') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'frames.txt')
Invoke-Device @('shell', 'dumpsys', 'meminfo', 'com.mooscles') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'memory-after.txt')
Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content -LiteralPath (Join-Path $OutputDirectory 'thermal-after.txt')
[ordered]@{
    stage = $Stage; fixture = $Fixture; scenario = $Scenario; serial = $Serial; api = $sdk
    durationSeconds = $DurationSeconds; capturedAt = (Get-Date).ToString('o'); processId = $processId
    apkPath = if ($apk) { $apk.FullName } else { $null }
    apkSha256 = $expectedSha256; installedApkSha256 = $installedSha256
    measurement = 'Raw capture only. Analyze input-to-usable latency, frame deadlines and PSS before claiming improvement.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'capture.json') -Encoding utf8
Write-Output "Capture saved: $OutputDirectory"
Write-Output 'No latency, smoothness or memory conclusion has been calculated automatically.'
