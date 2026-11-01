package com.mooscles.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTest {
    @OptIn(ExperimentalTestApi::class)
    private fun readTheme(dark: Boolean, reducedMotion: Boolean = false): Triple<MoosclesColors, Boolean, Boolean> {
        var result: Triple<MoosclesColors, Boolean, Boolean>? = null
        runComposeUiTest {
            withTheme(dark = dark, reducedMotion = reducedMotion) {
                result = Triple(MTheme.colors, MTheme.isDark, MTheme.reducedMotion)
            }
            waitForIdle()
        }
        return checkNotNull(result) { "The theme content never ran" }
    }

    @Test
    fun darkAndLightProvideTheirColorSets() {
        val dark = readTheme(dark = true)
        assertEquals(MoosclesColors.Dark, dark.first)
        assertEquals(true, dark.second)
        val light = readTheme(dark = false)
        assertEquals(MoosclesColors.Light, light.first)
        assertEquals(false, light.second)
    }

    @Test
    fun reducedMotionOverrideIsVisible() {
        assertEquals(true, readTheme(dark = false, reducedMotion = true).third)
        assertEquals(false, readTheme(dark = false, reducedMotion = false).third)
    }
}
