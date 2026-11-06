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
 * (at density 1, a dp is a pixel). Call `waitForIdle()` after v2 queues the composition.
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
 * Android's nonlinear scaling can draw 28 sp at about 37 dp at 2x. A scale just above 2x
 * produces a larger, linearly scaled case for layout stress tests.
 */
const val LINEAR_TWO_X_FONT_SCALE = 2.01f
