# Pure channel policy shared by publication and its focused local checks.
function Get-MoosclesReleaseChannelArguments {
    param([switch]$Prerelease, [switch]$Draft)
    if ($Prerelease) { return @('--prerelease=true', '--latest=false') }
    if ($Draft) { return @('--prerelease=false') }
    return @('--prerelease=false', '--latest')
}

function Assert-MoosclesLatestReleaseState {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [AllowEmptyString()][string]$LatestTag = '',
        [AllowEmptyString()][string]$PreviousLatestTag = '',
        [switch]$Prerelease
    )
    if ($Prerelease) {
        if ($LatestTag -ceq $Tag) { throw 'A prerelease must never become the Latest stable release.' }
        if ($LatestTag -cne $PreviousLatestTag) {
            throw 'The Latest stable release changed during prerelease publication. Review the repository state before proceeding.'
        }
    } elseif ($LatestTag -cne $Tag) {
        throw 'GitHub does not identify the published official release as Latest.'
    }
}
