package com.mooscles.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.NoHaptics
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesElevation
import com.mooscles.designsystem.tokens.MoosclesIllustration
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesRadius
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesSpace
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.tokens.MoosclesTypography

internal val LocalMoosclesColors = staticCompositionLocalOf<MoosclesColors> { error("No MoosclesDesignTheme") }
internal val LocalMoosclesTypography = staticCompositionLocalOf<MoosclesTypography> { error("No MoosclesDesignTheme") }
internal val LocalMoosclesSpace = staticCompositionLocalOf { MoosclesSpace() }
internal val LocalMoosclesRadius = staticCompositionLocalOf { MoosclesRadius() }
internal val LocalMoosclesElevation = staticCompositionLocalOf { MoosclesElevation() }
internal val LocalMoosclesTargets = staticCompositionLocalOf { MoosclesTargets() }
internal val LocalMoosclesSizes = staticCompositionLocalOf { MoosclesSizes() }
internal val LocalMoosclesMotion = staticCompositionLocalOf { MoosclesMotion() }
internal val LocalMoosclesTiming = staticCompositionLocalOf { MoosclesTiming() }
internal val LocalMoosclesIllustration = staticCompositionLocalOf { MoosclesIllustration() }
internal val LocalMoosclesReducedMotion = staticCompositionLocalOf { false }
internal val LocalMoosclesHaptics = staticCompositionLocalOf<MoosclesHaptics> { NoHaptics }
internal val LocalMoosclesDark = staticCompositionLocalOf { false }

/**
 * The content (text and icon) color of the nearest container. [Color.Unspecified] means
 * "use `MTheme.colors.textPrimary`"; [MoosclesDesignTheme] provides `textPrimary`.
 */
val LocalMContentColor = staticCompositionLocalOf { Color.Unspecified }

/** Read-only access to the design system tokens of the enclosing [MoosclesDesignTheme]. */
object MTheme {
    val colors: MoosclesColors @Composable @ReadOnlyComposable get() = LocalMoosclesColors.current
    val type: MoosclesTypography @Composable @ReadOnlyComposable get() = LocalMoosclesTypography.current
    val space: MoosclesSpace @Composable @ReadOnlyComposable get() = LocalMoosclesSpace.current
    val radius: MoosclesRadius @Composable @ReadOnlyComposable get() = LocalMoosclesRadius.current
    val elevation: MoosclesElevation @Composable @ReadOnlyComposable get() = LocalMoosclesElevation.current
    val targets: MoosclesTargets @Composable @ReadOnlyComposable get() = LocalMoosclesTargets.current
    val sizes: MoosclesSizes @Composable @ReadOnlyComposable get() = LocalMoosclesSizes.current
    val motion: MoosclesMotion @Composable @ReadOnlyComposable get() = LocalMoosclesMotion.current
    val timing: MoosclesTiming @Composable @ReadOnlyComposable get() = LocalMoosclesTiming.current
    val illustration: MoosclesIllustration @Composable @ReadOnlyComposable get() = LocalMoosclesIllustration.current
    val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalMoosclesReducedMotion.current
    val haptics: MoosclesHaptics @Composable @ReadOnlyComposable get() = LocalMoosclesHaptics.current
    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalMoosclesDark.current
}
