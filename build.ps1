param([string[]]$Tasks = @(':app:testDebugUnitTest', ':shared:testAndroidHostTest', ':designsystem:testAndroidHostTest', ':screens:testAndroidHostTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest'))
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
# Windows PowerShell 5.x defines neither $IsLinux nor $IsMacOS and only runs on Windows.
$isWindowsHost = -not ($IsLinux -or $IsMacOS)
$bundledJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($bundledJdk) { $env:JAVA_HOME = $bundledJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 or 21 installation.' }
$env:GRADLE_USER_HOME = Join-Path (Join-Path $projectRoot '.tooling') 'gradle-home'
$gradleWrapper = if ($isWindowsHost) { '.\gradlew.bat' } else { './gradlew' }
Push-Location -LiteralPath $projectRoot
try {
    & $gradleWrapper @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    $buildOutputs = Join-Path (Join-Path $projectRoot 'app') (Join-Path 'build' 'outputs')
    $debugApk = Join-Path (Join-Path (Join-Path $buildOutputs 'apk') 'debug') 'app-debug.apk'
    if (($Tasks | Where-Object { $_ -match '(^|:)assembleDebug$' }) -and (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
        $releaseAssets = & (Join-Path (Join-Path $projectRoot 'tools') 'prepare-release.ps1') -ApkPath $debugApk `
            -OutputDirectory (Join-Path $buildOutputs 'release') -PreviousVersionCode 25
        Write-Output "Verified release APK: $($releaseAssets.ApkPath)"
        Write-Output "Update metadata: $($releaseAssets.MetadataPath)"
    }
}
finally { Pop-Location }
