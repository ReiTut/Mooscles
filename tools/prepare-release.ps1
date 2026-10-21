param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$OutputDirectory = '',
    [string]$AndroidSdkDirectory = '',
    [string]$ExpectedApplicationId = 'com.mooscles',
    # Public fingerprint of the existing Mooscles debug signing certificate, not a secret.
    [string]$ExpectedSignerSha256 = '0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35',
    [long]$PreviousVersionCode = 0
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apkFullPath = (Resolve-Path -LiteralPath $ApkPath).Path
if (-not (Test-Path -LiteralPath $apkFullPath -PathType Leaf) -or [IO.Path]::GetExtension($apkFullPath) -ne '.apk') {
    throw 'ApkPath must identify a built APK file.'
}

if (-not $AndroidSdkDirectory) {
    $sdkCandidates = @(
        (Join-Path $projectRoot '.tooling\android-sdk'),
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) }
    $AndroidSdkDirectory = $sdkCandidates | Select-Object -First 1
}
if (-not $AndroidSdkDirectory) { throw 'Android SDK not found. Pass -AndroidSdkDirectory with installed Android SDK Build Tools.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkDirectory 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe')) -and (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat')) } |
    Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK Build Tools with aapt.exe and apksigner.bat are required.' }

if (-not $env:JAVA_HOME) {
    $releaseJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($releaseJdk) { $env:JAVA_HOME = $releaseJdk.FullName }
}
$badging = @(& (Join-Path $buildTools.FullName 'aapt.exe') dump badging $apkFullPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw "APK inspection failed: $($badging -join [Environment]::NewLine)" }
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if ($packageLine -notmatch "^package:\s+name='([^']+)'\s+versionCode='([0-9]+)'\s+versionName='([^']*)'") {
    throw 'Could not read application ID and version from the actual APK.'
}
$applicationId = $Matches[1]
$versionCode = [long]$Matches[2]
$versionName = $Matches[3]
if ($applicationId -ne $ExpectedApplicationId) { throw "APK application ID '$applicationId' does not match '$ExpectedApplicationId'." }
if ($versionCode -le 0 -or $versionCode -le $PreviousVersionCode) { throw 'The APK versionCode must be positive and greater than PreviousVersionCode.' }
if ([string]::IsNullOrWhiteSpace($versionName)) { throw 'The APK must have a versionName.' }
$minimumLine = $badging | Where-Object { $_ -match '^sdkVersion:' } | Select-Object -First 1
if ($minimumLine -notmatch "^sdkVersion:'([0-9]+)'") { throw 'Could not read minimum supported Android SDK from the actual APK.' }
$minSdk = [int]$Matches[1]
$debugBuild = [bool]($badging | Where-Object { $_ -eq 'application-debuggable' })

$signature = @(& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apkFullPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $($signature -join [Environment]::NewLine)" }
$signerHashes = @($signature | ForEach-Object {
    if ($_ -match '^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$') { $Matches[1].ToLowerInvariant() }
})
$expectedSigner = ($ExpectedSignerSha256 -replace ':', '').ToLowerInvariant()
if ($expectedSigner -notmatch '^[0-9a-f]{64}$') { throw 'ExpectedSignerSha256 must be the trusted existing signing certificate fingerprint.' }
if ($signerHashes.Count -ne 1 -or $signerHashes[0] -ne $expectedSigner) {
    throw 'The APK does not use the trusted existing signing certificate. Do not create a new key as an update workaround.'
}

if (-not $OutputDirectory) { $OutputDirectory = Join-Path $projectRoot 'app\build\outputs\release' }
$outputFullPath = [IO.Path]::GetFullPath($OutputDirectory)
$ignoredBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'app\build')) + [IO.Path]::DirectorySeparatorChar
$ignoredToolingRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tooling')) + [IO.Path]::DirectorySeparatorChar
if (-not $outputFullPath.StartsWith($ignoredBuildRoot, [StringComparison]::OrdinalIgnoreCase) -and
    -not $outputFullPath.StartsWith($ignoredToolingRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Release assets must be generated under ignored app/build/ or .tooling/ directories, never the source tree.'
}
[void][IO.Directory]::CreateDirectory($outputFullPath)
$safeVersionName = $versionName -replace '[^0-9A-Za-z._-]', '-'
$buildSuffix = if ($debugBuild) { '-debug' } else { '' }
$apkFileName = "Mooscles-v$safeVersionName$buildSuffix.apk"
$releaseApk = Join-Path $outputFullPath $apkFileName
if (-not $apkFullPath.Equals($releaseApk, [StringComparison]::OrdinalIgnoreCase)) {
    Copy-Item -LiteralPath $apkFullPath -Destination $releaseApk -Force
}
$apkSize = (Get-Item -LiteralPath $releaseApk).Length
$sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
$metadata = [ordered]@{
    applicationId = $applicationId
    versionCode = $versionCode
    versionName = $versionName
    minSdk = $minSdk
    apkFileName = $apkFileName
    apkSize = $apkSize
    sha256 = $sha256
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
$metadataPath = Join-Path $outputFullPath 'update.json'
[IO.File]::WriteAllText($metadataPath, (($metadata | ConvertTo-Json) + [Environment]::NewLine), $utf8)
$checksumPath = Join-Path $outputFullPath "$apkFileName.sha256"
[IO.File]::WriteAllText($checksumPath, "$sha256  $apkFileName$([Environment]::NewLine)", $utf8)

# Re-read generated artifacts so a release never receives stale metadata or a partial copy.
$writtenMetadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
if ((Get-Item -LiteralPath $releaseApk).Length -ne $writtenMetadata.apkSize -or
    (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $writtenMetadata.sha256) {
    throw 'Generated release artifacts failed final size/checksum verification.'
}
[pscustomobject]@{
    ApkPath = $releaseApk
    MetadataPath = $metadataPath
    ChecksumPath = $checksumPath
    ApplicationId = $applicationId
    VersionName = $versionName
    VersionCode = $versionCode
    MinSdk = $minSdk
    ApkSize = $apkSize
    Sha256 = $sha256
    SignerSha256 = $signerHashes[0]
    PrereleaseRequired = $debugBuild
}
