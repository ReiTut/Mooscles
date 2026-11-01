package com.mooscles.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.MoosclesHaptics
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.screens.workout.LocalRecompositionProbe

/**
 * Sets [content] inside the design system theme, as a screen sees it in the app.
 *
 * [fontScale] replaces the density's font scale (use [LINEAR_TWO_X_FONT_SCALE] for the iOS text
 * sizes at 2x on Android); [density], when given, replaces the screen density (1 is the iOS
 * simulator's, where a dp is a pixel). [locale] is a BCP 47 tag such as `pt-BR`, applied with
 * [ProvideAppLocale]; a class that passes it should call `LocalAppLocale.restore()` in an
 * `@AfterTest`, so a failure cannot leak the language into the next test. [size] gives the screen
 * exactly that size, as a phone of that width would. [probe] receives every key reported by
 * `RecompositionProbe`. With [restoration] (`screenRestoration()`), the content is set through it, so
 * `emulateSaveAndRestore()` can recreate it from its saved state. v2 queues the composition: call
 * `waitForIdle()`.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.withScreen(
    dark: Boolean = false,
    fontScale: Float = 1f,
    reducedMotion: Boolean = false,
    haptics: MoosclesHaptics = RecordingHaptics(),
    locale: String? = null,
    size: DpSize? = null,
    density: Float? = null,
    probe: ((String) -> Unit)? = null,
    restoration: ScreenRestoration? = null,
    content: @Composable () -> Unit,
) {
    val frame: @Composable () -> Unit = {
        ScreenFrame(dark, fontScale, reducedMotion, haptics, locale, size, density, probe, content)
    }
    if (restoration != null) restoration.setContent(frame) else setContent(frame)
}

/**
 * The frame [withScreen] sets: the density (with [fontScale], and [density] when given), the probe,
 * the design system theme, then [content] at exactly [size] when given, in [locale] when given. A
 * test that changes the theme, the font scale or the size within one composition (the
 * accessibility sweep) composes it with its values read from state.
 */
@Composable
fun ScreenFrame(
    dark: Boolean = false,
    fontScale: Float = 1f,
    reducedMotion: Boolean = false,
    haptics: MoosclesHaptics = RecordingHaptics(),
    locale: String? = null,
    size: DpSize? = null,
    density: Float? = null,
    probe: ((String) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val screen = density ?: LocalDensity.current.density
    CompositionLocalProvider(
        LocalDensity provides Density(screen, fontScale),
        LocalRecompositionProbe provides probe,
    ) {
        MoosclesDesignTheme(dark = dark, reducedMotion = reducedMotion, haptics = haptics) {
            val sized: @Composable () -> Unit =
                if (size == null) content else ({ Box(Modifier.requiredSize(size)) { content() } })
            if (locale == null) sized() else ProvideAppLocale(locale) { sized() }
        }
    }
}

/**
 * Android scales large text by less than the font scale up to 2x (non-linear font scaling: 28 sp
 * at 2x is about 37 dp, not 56); past its last table, 2x, it scales linearly, and iOS always
 * does. Just over 2x, then, every platform draws 28 sp at 56 dp, the text sizes iOS gets at 2x.
 * The same value as the design system's test constant.
 */
const val LINEAR_TWO_X_FONT_SCALE = 2.01f
