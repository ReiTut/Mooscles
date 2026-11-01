package com.mooscles.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.theme.MoosclesDesignTheme

/**
 * Sets [content] inside the design system theme. [fontScale] replaces the density's font
 * scale, so a test can render at 2x text; [density], when given, replaces the screen density
 * (1 is the iOS simulator's, where a dp is a pixel). v2 queues the composition: call `waitForIdle()`.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.withTheme(
    dark: Boolean = false,
    fontScale: Float = 1f,
    reducedMotion: Boolean = false,
    haptics: MoosclesHaptics = RecordingHaptics(),
    density: Float? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        val screen = density ?: LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(screen, fontScale)) {
            MoosclesDesignTheme(dark = dark, reducedMotion = reducedMotion, haptics = haptics, content = content)
        }
    }
}

/**
 * Android scales large text by less than the font scale up to 2x (non-linear font scaling: 28 sp
 * at 2x is about 37 dp, not 56); past its last table, 2x, it scales linearly, and iOS always
 * does. Just over 2x, then, every platform draws 28 sp at 56 dp, the text sizes iOS gets at 2x.
 */
const val LINEAR_TWO_X_FONT_SCALE = 2.01f
