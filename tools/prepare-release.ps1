param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [string]$OutputDirectory = '',
    [string]$AndroidSdkDirectory = '',
    [string]$ExpectedApplicationId = 'com.mooscles',
    # Public fingerprint of the existing Mooscles debug signing certificate, not a secret.
    [string]$ExpectedSignerSha256 = '0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35',
    [long]$PreviousVersionCode = 0,
    # Recovery releases may reuse versionName but always have a newer Android build number.
    [switch]$IncludeVersionCode
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Windows PowerShell 5.x defines neither $IsLinux nor $IsMacOS and only runs on Windows.
$isWindowsHost = -not ($IsLinux -or $IsMacOS)
# Windows and default macOS volumes ignore path case; Linux does not.
$pathComparison = if ($IsLinux) { [StringComparison]::Ordinal } else { [StringComparison]::OrdinalIgnoreCase }
$aaptName = if ($isWindowsHost) { 'aapt.exe' } else { 'aapt' }
$apksignerName = if ($isWindowsHost) { 'apksigner.bat' } else { 'apksigner' }
$apkFullPath = (Resolve-Path -LiteralPath $ApkPath).Path
if (-not (Test-Path -LiteralPath $apkFullPath -PathType Leaf) -or [IO.Path]::GetExtension($apkFullPath) -ne '.apk') {
    throw 'ApkPath must identify a built APK file.'
}

if (-not $AndroidSdkDirectory) {
    $sdkCandidates = @(
        (Join-Path (Join-Path $projectRoot '.tooling') 'android-sdk'),
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME
    )
    if ($isWindowsHost) { $sdkCandidates += Join-Path (Join-Path $env:LOCALAPPDATA 'Android') 'Sdk' }
    if ($IsMacOS) { $sdkCandidates += Join-Path (Join-Path (Join-Path $HOME 'Library') 'Android') 'sdk' }
    $AndroidSdkDirectory = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) } | Select-Object -First 1
}
if (-not $AndroidSdkDirectory) { throw 'Android SDK not found. Pass -AndroidSdkDirectory with installed Android SDK Build Tools.' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $AndroidSdkDirectory 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending |
    Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName $aaptName)) -and (Test-Path -LiteralPath (Join-Path $_.FullName $apksignerName)) } |
    Select-Object -First 1
if (-not $buildTools) { throw "Android SDK Build Tools with $aaptName and $apksignerName are required." }

if (-not $env:JAVA_HOME) {
    $releaseJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($releaseJdk) { $env:JAVA_HOME = $releaseJdk.FullName }
}
$badging = @(& (Join-Path $buildTools.FullName $aaptName) dump badging $apkFullPath 2>&1)
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

$signature = @(& (Join-Path $buildTools.FullName $apksignerName) verify --verbose --print-certs $apkFullPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $($signature -join [Environment]::NewLine)" }
# Build Tools 35 print "Signer #1 certificate SHA-256 digest: ..."; 37 and later print
# "V2 Signer: certificate SHA-256 digest: ..." per scheme, and other prefixes for rotation
# and hybrid schemes. Every certificate digest apksigner prints is a signer except the
# source stamp, so unknown signer formats are counted, never ignored (fail closed). The
# same certificate can appear once per scheme, so compare distinct digests.
$signerHashes = @($signature | ForEach-Object {
    if ("$_" -match '^(.*?) ?certificate SHA-256 digest: ([0-9a-fA-F]{64})$') {
        $who = $Matches[1]
        $digest = $Matches[2].ToLowerInvariant()
        if ($who -notlike 'Source Stamp Signer*') { $digest }
    }
} | Select-Object -Unique)
$expectedSigner = ($ExpectedSignerSha256 -replace ':', '').ToLowerInvariant()
if ($expectedSigner -notmatch '^[0-9a-f]{64}$') { throw 'ExpectedSignerSha256 must be the trusted existing signing certificate fingerprint.' }
if ($signerHashes.Count -ne 1 -or $signerHashes[0] -ne $expectedSigner) {
    throw 'The APK does not use the trusted existing signing certificate. Do not create a new key as an update workaround.'
}

if (-not $OutputDirectory) { $OutputDirectory = Join-Path (Join-Path (Join-Path $projectRoot 'app') 'build') (Join-Path 'outputs' 'release') }
$outputFullPath = [IO.Path]::GetFullPath($OutputDirectory)
$ignoredBuildRoot = [IO.Path]::GetFullPath((Join-Path (Join-Path $projectRoot 'app') 'build')) + [IO.Path]::DirectorySeparatorChar
$ignoredToolingRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tooling')) + [IO.Path]::DirectorySeparatorChar
if (-not $outputFullPath.StartsWith($ignoredBuildRoot, $pathComparison) -and
    -not $outputFullPath.StartsWith($ignoredToolingRoot, $pathComparison)) {
    throw 'Release assets must be generated under ignored app/build/ or .tooling/ directories, never the source tree.'
}
[void][IO.Directory]::CreateDirectory($outputFullPath)
$safeVersionName = $versionName -replace '[^0-9A-Za-z._-]', '-'
$buildSuffix = if ($debugBuild) { '-debug' } else { '' }
$versionCodeSuffix = if ($IncludeVersionCode) { "-build$versionCode" } else { '' }
$apkFileName = "Mooscles-v$safeVersionName$versionCodeSuffix$buildSuffix.apk"
$releaseApk = Join-Path $outputFullPath $apkFileName
if (-not $apkFullPath.Equals($releaseApk, $pathComparison)) {
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
    IsDebugBuild = $debugBuild
}
