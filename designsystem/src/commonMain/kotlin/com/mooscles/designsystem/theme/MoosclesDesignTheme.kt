package com.mooscles.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import com.mooscles.designsystem.i18n.LocaleEpoch
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.rememberPlatformHaptics
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesIllustration
import com.mooscles.designsystem.tokens.rememberMoosclesTypography

/** The figure and body map strokes (phase 4A); the same in both themes. */
private val Illustration = MoosclesIllustration()

/**
 * Provides every design system token to [content]. [reducedMotion] defaults to the system
 * setting; tests and the gallery may override it. [haptics] defaults to the
 * platform implementation.
 */
@Composable
fun MoosclesDesignTheme(
    dark: Boolean,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    haptics: MoosclesHaptics = rememberPlatformHaptics(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) MoosclesColors.Dark else MoosclesColors.Light
    // Rebuilds the content after a gallery language override was undone (see LocaleEpoch).
    key(LocaleEpoch.value.intValue) {
        CompositionLocalProvider(
            LocalMoosclesColors provides colors,
            LocalMoosclesTypography provides rememberMoosclesTypography(),
            LocalMoosclesReducedMotion provides reducedMotion,
            LocalMoosclesHaptics provides haptics,
            LocalMoosclesDark provides dark,
            LocalMoosclesIllustration provides Illustration,
            LocalMContentColor provides colors.textPrimary,
            content = content,
        )
    }
}
