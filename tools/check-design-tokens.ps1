<#
.SYNOPSIS
Fails when a design system component hard-codes a color, a measure or a duration.

.DESCRIPTION
Values live only in designsystem/.../tokens/. Everywhere else the rules below apply, and every
violation is printed as "file:line: rule". Exit code 1 when there is any, 0 when clean.
Line comments (//) and KDoc lines are ignored.

Without -Path the target is the component, gallery, licenses, illustration and charts folders of :designsystem
and its interaction/Reorder.kt
(folders that do not exist yet are skipped), the files of the pilot screen (Settings) in :app and
the common code of the redesigned screens (:screens).
.PARAMETER Path
Files or folders to check. A folder is searched for *.kt files.
#>
[CmdletBinding()]
param([string[]]$Path)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $Path -or $Path.Count -eq 0) {
    $base = 'designsystem/src/commonMain/kotlin/com/mooscles/designsystem'
    $pilot = 'app/src/main/java/com/mooscles/ui'
    $Path = @(
        "$base/components", "$base/gallery", "$base/licenses", "$base/illustration",
        # Phase 5A: the charts.
        "$base/charts",
        # Phase 4B: the reordering modifiers (only this file of interaction/: MotionPolicy's
        # `MotionChoice.Spring(` would match the case-insensitive spring rule).
        "$base/interaction/Reorder.kt",
        "$pilot/SettingsScreen.kt", "$pilot/UpdateSection.kt", "$pilot/DataSection.kt",
        "$pilot/SettingsRestDurationField.kt", "$pilot/SettingsTexts.kt",
        'screens/src/commonMain/kotlin/com/mooscles/screens'
    ) | ForEach-Object { Join-Path $root $_ }
}

# Rule name, then regex. Color.Transparent and Color.Unspecified are not color values: allowed.
$rules = @(
    @{ Name = 'hard-coded Color(0x...)'; Pattern = 'Color\(\s*0x' }
    @{ Name = 'Color.White or Color.Black'; Pattern = 'Color\.(White|Black)\b' }
    @{ Name = 'named color (Color.Red, Gray, ...)'; Pattern = 'Color\.(Red|Green|Blue|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b' }
    @{ Name = 'number followed by .dp or .sp'; Pattern = '\d\.(dp|sp)\b' }
    @{ Name = 'tween( with a number'; Pattern = '\btween\(\s*(durationMillis\s*=\s*)?\d' }
    @{ Name = 'spring( with a number'; Pattern = '\bspring\([^)]*\d' }
    @{ Name = 'delay( with a number'; Pattern = '\bdelay\(\s*\d' }
    @{ Name = 'Primitives used outside tokens/'; Pattern = '\bPrimitives\.'; SkipInTokens = $true }
)

$files = @()
foreach ($entry in $Path) {
    if (-not (Test-Path -LiteralPath $entry)) { continue }
    $item = Get-Item -LiteralPath $entry
    if ($item.PSIsContainer) { $files += Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Filter '*.kt' }
    else { $files += $item }
}

$violations = 0
foreach ($file in $files) {
    $full = $file.FullName
    $display = if ($full.StartsWith($root)) { $full.Substring($root.Length).TrimStart('\', '/') } else { $full }
    $inTokens = ($full -replace '\\', '/') -match '/tokens/'
    $lines = Get-Content -LiteralPath $full
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $trimmed = $lines[$i].TrimStart()
        if ($trimmed.StartsWith('*') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('//')) { continue }
        $code = $lines[$i] -replace '//.*$', ''
        foreach ($rule in $rules) {
            if ($rule.SkipInTokens -and $inTokens) { continue }
            if ($code -match $rule.Pattern) {
                Write-Output ("{0}:{1}: {2}" -f $display, ($i + 1), $rule.Name)
                $violations++
            }
        }
    }
}

if ($violations -gt 0) {
    Write-Output "$violations design token violation(s)."
    exit 1
}
Write-Output "Design tokens: clean ($($files.Count) file(s) checked)."
exit 0
