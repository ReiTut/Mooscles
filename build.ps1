param([string[]]$Tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest'))
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$bundledJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($bundledJdk) { $env:JAVA_HOME = $bundledJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17 or 21 installation.' }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tooling\gradle-home'
Push-Location -LiteralPath $projectRoot
try {
    & '.\gradlew.bat' @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    $debugApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (($Tasks | Where-Object { $_ -match '(^|:)assembleDebug$' }) -and (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
        $releaseAssets = & (Join-Path $projectRoot 'tools\prepare-release.ps1') -ApkPath $debugApk `
            -OutputDirectory (Join-Path $projectRoot 'app\build\outputs\release') -PreviousVersionCode 15
        Write-Output "Verified release APK: $($releaseAssets.ApkPath)"
        Write-Output "Update metadata: $($releaseAssets.MetadataPath)"
    }
}
finally { Pop-Location }
