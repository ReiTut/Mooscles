param(
    [Parameter(Mandatory = $true)][ValidatePattern('^v[0-9][0-9A-Za-z.+_-]*$')][string]$Tag,
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [Parameter(Mandatory = $true)][ValidateRange(1, [long]::MaxValue)][long]$PreviousVersionCode,
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository = 'ReiTut/Mooscles',
    [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$Remote = 'origin',
    [switch]$Publish,
    # A unique recovery tag can reuse versionName without replacing its original release.
    [switch]$UseBuildTag,
    # Official releases are the owner's default; build variant and distribution channel are independent.
    [switch]$Prerelease
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$appBuild = Join-Path (Join-Path $projectRoot 'app') 'build'
$notesPath = (Resolve-Path -LiteralPath $NotesFile).Path
if (-not (Test-Path -LiteralPath $notesPath -PathType Leaf) -or
    [string]::IsNullOrWhiteSpace((Get-Content -LiteralPath $notesPath -Raw))) {
    throw 'NotesFile must contain the reviewed release notes.'
}
$gitCommand = Get-Command git -ErrorAction SilentlyContinue
if (-not $gitCommand) { throw 'Git is required. Install Git and retry.' }
$gitExecutable = $gitCommand.Source
$githubCommand = Get-Command gh -ErrorAction SilentlyContinue
# Windows PowerShell 5.x defines neither $IsLinux nor $IsMacOS and only runs on Windows.
$isWindowsHost = -not ($IsLinux -or $IsMacOS)
$bundledGitHubCli = Join-Path (Join-Path (Join-Path (Join-Path $projectRoot '.tooling') 'github-cli') 'bin') $(if ($isWindowsHost) { 'gh.exe' } else { 'gh' })
$githubExecutable = if ($githubCommand) { $githubCommand.Source } else { $bundledGitHubCli }
if (-not (Test-Path -LiteralPath $githubExecutable -PathType Leaf)) { throw 'GitHub CLI is required. Install it and sign in with gh auth login.' }

function Invoke-GitChecked([string[]]$Arguments) {
    $output = @(& $gitExecutable @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Git failed: $($output -join [Environment]::NewLine)" }
    return $output
}
function Invoke-GitHubChecked([string[]]$Arguments) {
    $output = @(& $githubExecutable @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "GitHub CLI failed. Check authentication and repository permissions: $($output -join [Environment]::NewLine)" }
    return $output
}
function Assert-CleanCommit([string]$ExpectedCommit = '') {
    $status = @(Invoke-GitChecked -Arguments @('status', '--porcelain=v1', '--untracked-files=all'))
    if ($status.Count -gt 0) { throw 'Commit all intended source changes and resolve unrelated changes before creating a release. The working tree must be clean.' }
    $commit = (Invoke-GitChecked -Arguments @('rev-parse', 'HEAD') | Select-Object -First 1).Trim()
    if ($ExpectedCommit -and $commit -ne $ExpectedCommit) { throw 'HEAD changed during release verification. No release will be published.' }
    return $commit
}
function Assert-ExactRemoteTag([string]$ExpectedCommit) {
    $localCommit = (Invoke-GitChecked -Arguments @('rev-parse', "refs/tags/$Tag^{commit}") | Select-Object -First 1).Trim()
    if ($localCommit -ne $ExpectedCommit) { throw 'The local version tag must point to the exact committed source being tested.' }
    $remoteTags = @(Invoke-GitChecked -Arguments @('ls-remote', '--exit-code', '--tags', $Remote, "refs/tags/$Tag", "refs/tags/$Tag^{}"))
    $remoteTag = $remoteTags | Where-Object { $_ -match '\^\{\}$' } | Select-Object -First 1
    if (-not $remoteTag) { $remoteTag = $remoteTags | Select-Object -First 1 }
    if (-not $remoteTag -or ($remoteTag -split '\s+')[0] -ne $ExpectedCommit) {
        throw 'The remote version tag must already point to the exact committed source being tested. Push the intended tag normally; do not force-push.'
    }
}
function Get-ReleaseInfo {
    $json = Invoke-GitHubChecked -Arguments @('release', 'view', $Tag, '--repo', $Repository,
        '--json', 'url,isDraft,isPrerelease,assets,tagName,targetCommitish,body')
    return (($json -join [Environment]::NewLine) | ConvertFrom-Json)
}
function Assert-ReleaseAssets($Release, $ExpectedAssets) {
    if ($Release.tagName -ne $Tag) { throw 'GitHub returned a different release tag.' }
    if ([bool]$Release.isPrerelease -ne $Prerelease.IsPresent) { throw 'The release channel differs from the requested official/prerelease channel.' }
    if (@($Release.assets).Count -ne $ExpectedAssets.Count) { throw 'The release does not contain the expected APK, update.json and checksum assets.' }
    foreach ($expected in $ExpectedAssets) {
        $asset = @($Release.assets | Where-Object { $_.name -ceq $expected.Name })
        if ($asset.Count -ne 1 -or [long]$asset[0].size -ne $expected.Size) {
            throw "Release asset '$($expected.Name)' is missing or has an unexpected size."
        }
        if ($asset[0].digest -and $asset[0].digest -ne ('sha256:' + $expected.Hash)) {
            throw "GitHub's digest does not match '$($expected.Name)'."
        }
    }
}

Push-Location -LiteralPath $projectRoot
$draftCreated = $false
$draftCreateAttempted = $false
try {
    $testedCommit = Assert-CleanCommit
    $remoteUrl = (Invoke-GitChecked -Arguments @('remote', 'get-url', $Remote) | Select-Object -First 1).Trim() -replace '\.git$', ''
    if ($remoteUrl -notin @("https://github.com/$Repository", "git@github.com:$Repository", "ssh://git@github.com/$Repository")) {
        throw 'The selected Git remote does not identify the requested GitHub repository. Review the repository/remote choice first.'
    }
    Assert-ExactRemoteTag -ExpectedCommit $testedCommit
    $repoJson = Invoke-GitHubChecked -Arguments @('repo', 'view', $Repository, '--json', 'isPrivate,viewerPermission')
    $repositoryInfo = ($repoJson -join [Environment]::NewLine) | ConvertFrom-Json
    if ($repositoryInfo.isPrivate) { throw 'The updater needs public release assets. Choose a public release-only repository before publishing; do not embed a shared token in the APK.' }
    if ($repositoryInfo.viewerPermission -notin @('WRITE', 'MAINTAIN', 'ADMIN')) { throw 'The signed-in GitHub account needs write permission to create this release.' }

    # Building here associates the checks and APK with this exact clean commit.
    # Gradle validates task inputs even when existing verified outputs are up to date.
    & (Join-Path $projectRoot 'build.ps1')
    [void](Assert-CleanCommit -ExpectedCommit $testedCommit)
    Assert-ExactRemoteTag -ExpectedCommit $testedCommit

    $testReports = @(Get-ChildItem -LiteralPath (Join-Path $appBuild (Join-Path 'test-results' 'testDebugUnitTest')) -Filter 'TEST-*.xml' -File)
    if ($testReports.Count -eq 0) { throw 'No unit-test results were generated.' }
    $testCount = 0
    foreach ($report in $testReports) {
        [xml]$testXml = Get-Content -LiteralPath $report.FullName -Raw
        if (-not $testXml.testsuite -or [int]$testXml.testsuite.failures -ne 0 -or [int]$testXml.testsuite.errors -ne 0) {
            throw "Unit-test report '$($report.Name)' is missing or unsuccessful."
        }
        $testCount += [int]$testXml.testsuite.tests
    }
    if ($testCount -eq 0) { throw 'The release must have executed unit tests.' }
    [xml]$lintXml = Get-Content -LiteralPath (Join-Path $appBuild (Join-Path 'reports' 'lint-results-debug.xml')) -Raw
    if (-not $lintXml.issues -or @($lintXml.issues.issue | Where-Object { $_.severity -in @('Error', 'Fatal') }).Count -gt 0) {
        throw 'Lint results are missing or contain errors.'
    }
    $assets = & (Join-Path (Join-Path $projectRoot 'tools') 'prepare-release.ps1') `
        -ApkPath (Join-Path (Join-Path (Join-Path $appBuild 'outputs') 'apk') (Join-Path 'debug' 'app-debug.apk')) `
        -OutputDirectory (Join-Path (Join-Path $appBuild 'outputs') 'release') `
        -PreviousVersionCode $PreviousVersionCode -IncludeVersionCode:$UseBuildTag
    $metadata = Get-Content -LiteralPath $assets.MetadataPath -Raw | ConvertFrom-Json
    $expectedTag = 'v' + $metadata.versionName
    if ($UseBuildTag) { $expectedTag += '-build' + $metadata.versionCode }
    if ($Tag -cne $expectedTag) { throw 'The tag must match the actual APK versionName and, with UseBuildTag, its build number.' }
    if (-not $assets.IsDebugBuild) { throw 'This workflow distributes the existing debug variant. Review production signing and release verification before changing variants.' }

    $expectedAssets = @($assets.ApkPath, $assets.MetadataPath, $assets.ChecksumPath) | ForEach-Object {
        [pscustomobject]@{ Name = [IO.Path]::GetFileName($_); Size = (Get-Item -LiteralPath $_).Length; Hash = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    if ($expectedAssets[0].Hash -ne $metadata.sha256 -or $expectedAssets[0].Size -ne [long]$metadata.apkSize) {
        throw 'The APK changed after metadata generation. Rebuild and retry.'
    }
    [void](Assert-CleanCommit -ExpectedCommit $testedCommit)
    $releaseListJson = Invoke-GitHubChecked -Arguments @('release', 'list', '--repo', $Repository, '--limit', '1000', '--json', 'tagName,isDraft')
    $existingRelease = @(($releaseListJson -join [Environment]::NewLine) | ConvertFrom-Json) | Where-Object { $_.tagName -ceq $Tag } | Select-Object -First 1
    if ($existingRelease) {
        if (-not $existingRelease.isDraft) { throw 'This release is already published. Published releases and assets are never overwritten by this helper.' }
        $release = Get-ReleaseInfo
        $expectedNotes = (Get-Content -LiteralPath $notesPath -Raw).Replace("`r`n", "`n").TrimEnd()
        if (([string]$release.body).Replace("`r`n", "`n").TrimEnd() -cne $expectedNotes) {
            throw 'The existing draft notes differ from NotesFile. Review them first; this helper never silently overwrites an existing draft.'
        }
    } else {
        $releaseTitle = if ($Prerelease) { "Mooscles $Tag (debug prerelease)" } else { "Mooscles $Tag" }
        $createArguments = @('release', 'create', $Tag, $assets.ApkPath, $assets.MetadataPath, $assets.ChecksumPath,
            '--repo', $Repository, '--verify-tag', '--target', $testedCommit, '--draft',
            '--title', $releaseTitle, '--notes-file', $notesPath)
        if ($Prerelease) { $createArguments += '--prerelease' }
        $draftCreateAttempted = $true
        [void](Invoke-GitHubChecked -Arguments $createArguments)
        $draftCreated = $true
        $release = Get-ReleaseInfo
    }
    if (-not $release.isDraft) { throw 'The newly created release must remain a draft until its assets are verified.' }
    Assert-ReleaseAssets -Release $release -ExpectedAssets $expectedAssets

    # Verify actual uploaded bytes, including when GitHub omits asset digests.
    $verificationDirectory = Join-Path (Join-Path (Join-Path $projectRoot '.tooling') 'release-publish-check') ($Tag + '-' + [Guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($verificationDirectory)
    $downloadArguments = @('release', 'download', $Tag, '--repo', $Repository, '--dir', $verificationDirectory)
    foreach ($expected in $expectedAssets) { $downloadArguments += @('--pattern', $expected.Name) }
    [void](Invoke-GitHubChecked -Arguments $downloadArguments)
    foreach ($expected in $expectedAssets) {
        $downloaded = Join-Path $verificationDirectory $expected.Name
        if (-not (Test-Path -LiteralPath $downloaded -PathType Leaf) -or
            (Get-Item -LiteralPath $downloaded).Length -ne $expected.Size -or
            (Get-FileHash -LiteralPath $downloaded -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected.Hash) {
            throw "Uploaded asset '$($expected.Name)' failed independent download verification."
        }
    }
    if ($Publish) {
        [void](Assert-CleanCommit -ExpectedCommit $testedCommit)
        Assert-ExactRemoteTag -ExpectedCommit $testedCommit
        $publishArguments = @('release', 'edit', $Tag, '--repo', $Repository, '--draft=false')
        if (-not $Prerelease) { $publishArguments += @('--prerelease=false', '--latest') }
        [void](Invoke-GitHubChecked -Arguments $publishArguments)
        $release = Get-ReleaseInfo
        if ($release.isDraft) { throw 'GitHub still reports a draft after the publish request.' }
        Assert-ReleaseAssets -Release $release -ExpectedAssets $expectedAssets
    }
    [pscustomobject]@{
        RepositoryUrl = "https://github.com/$Repository"
        ReleaseUrl = $release.url
        Draft = [bool]$release.isDraft
        Prerelease = [bool]$release.isPrerelease
        TestedCommit = $testedCommit
        UnitTests = $testCount
        LintErrors = 0
        ApkPath = $assets.ApkPath
        MetadataPath = $assets.MetadataPath
        ApkDownloadUrl = if ($Publish) { "https://github.com/$Repository/releases/download/$([Uri]::EscapeDataString($Tag))/$([Uri]::EscapeDataString($metadata.apkFileName))" } else { $null }
    }
} catch {
    if ($draftCreated -or $draftCreateAttempted) { Write-Warning 'A draft may remain in GitHub. Review its state; failures before the explicit publication step leave it unpublished.' }
    throw
} finally { Pop-Location }
