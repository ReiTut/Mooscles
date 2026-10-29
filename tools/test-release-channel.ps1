$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'release-channel.ps1')
$checks = 0
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks += 1
}
function Assert-Rejected([scriptblock]$Action, [string]$Message) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    Assert-True $rejected $Message
}

$officialDraft = @(Get-MoosclesReleaseChannelArguments -Draft)
Assert-True (($officialDraft -join ' ') -ceq '--prerelease=false') 'An official draft must not be marked Latest before publication.'
$official = @(Get-MoosclesReleaseChannelArguments)
Assert-True (($official -join ' ') -ceq '--prerelease=false --latest') 'The existing default must still publish a full Latest release.'
foreach ($draft in @($false, $true)) {
    $preview = @(Get-MoosclesReleaseChannelArguments -Prerelease -Draft:$draft)
    Assert-True (($preview -join ' ') -ceq '--prerelease=true --latest=false') 'Draft and publication must explicitly preserve the prerelease channel and exclude Latest.'
}

Assert-MoosclesLatestReleaseState -Tag 'v1.4.7-beta.1' -LatestTag 'v1.4.6' -PreviousLatestTag 'v1.4.6' -Prerelease
$checks += 1
Assert-MoosclesLatestReleaseState -Tag 'v1.4.7-beta.1' -Prerelease
$checks += 1
Assert-Rejected { Assert-MoosclesLatestReleaseState -Tag 'v1.4.7-beta.1' -LatestTag 'v1.4.7-beta.1' -PreviousLatestTag 'v1.4.6' -Prerelease } 'A prerelease selected as Latest must be rejected.'
Assert-Rejected { Assert-MoosclesLatestReleaseState -Tag 'v1.4.7-beta.1' -LatestTag 'v1.4.8' -PreviousLatestTag 'v1.4.6' -Prerelease } 'A concurrent stable-release change must be detected.'
Assert-Rejected { Assert-MoosclesLatestReleaseState -Tag 'v1.4.7-beta.1' -LatestTag '' -PreviousLatestTag 'v1.4.6' -Prerelease } 'Losing the previous stable Latest must be detected.'
Assert-MoosclesLatestReleaseState -Tag 'v1.4.7' -LatestTag 'v1.4.7'
$checks += 1
Assert-Rejected { Assert-MoosclesLatestReleaseState -Tag 'v1.4.7' -LatestTag 'v1.4.6' } 'An official publication that did not become Latest must be rejected.'

[pscustomobject]@{ Checks = $checks; Failures = 0; NetworkAccess = $false; PublishedAnything = $false }
