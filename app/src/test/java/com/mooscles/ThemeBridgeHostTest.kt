package com.mooscles

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.ui.MoosclesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The theme bridge keeps the old Material theme byte-for-byte and adds the design tokens under
 * it. Expected values are literals copied from Theme.kt as it was before the bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeBridgeHostTest {
    @get:Rule val compose = createComposeRule()

    private val choice = mutableStateOf("light")
    private var seenScheme: ColorScheme? = null

    private fun scheme(theme: String): ColorScheme {
        choice.value = theme
        compose.waitForIdle()
        return seenScheme!!
    }

    private var seenColors: MoosclesColors? = null
    private var composed = false

    private fun designColors(theme: String): MoosclesColors {
        if (!composed) {
            composed = true
            compose.setContent { MoosclesTheme(choice.value) { seenColors = MTheme.colors } }
        }
        choice.value = theme
        compose.waitForIdle()
        return seenColors!!
    }

    private fun assertScheme(s: ColorScheme, v: List<Long>) {
        val actual = listOf(
            s.primary, s.onPrimary, s.primaryContainer, s.onPrimaryContainer,
            s.secondary, s.secondaryContainer, s.tertiary, s.tertiaryContainer,
            s.background, s.onBackground, s.surface, s.onSurface,
            s.surfaceVariant, s.onSurfaceVariant, s.outline, s.outlineVariant,
        )
        val names = listOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
            "secondary", "secondaryContainer", "tertiary", "tertiaryContainer",
            "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "outline", "outlineVariant",
        )
        names.forEachIndexed { i, n -> assertEquals(n, Color(v[i]), actual[i]) }
    }

    @Test fun legacyColorSchemeIsUnchanged() {
        compose.setContent { MoosclesTheme(choice.value) { seenScheme = MaterialTheme.colorScheme } }
        assertScheme(scheme("light"), listOf(
            0xFF006B50, 0xFFFFFFFF, 0xFFBEF3D8, 0xFF003828,
            0xFF50645B, 0xFFD2E8DA, 0xFF895200, 0xFFFFDDB1,
            0xFFF7F8F2, 0xFF18201B, 0xFFF7F8F2, 0xFF18201B,
            0xFFE0E7DF, 0xFF414B44, 0xFF717B73, 0xFFC1CCC2,
        ))
        assertScheme(scheme("dark"), listOf(
            0xFF83DBB0, 0xFF003828, 0xFF00513C, 0xFFBEF3D8,
            0xFFB7CCBE, 0xFF384D41, 0xFFFFBA66, 0xFF683D00,
            0xFF101713, 0xFFE0E6DE, 0xFF101713, 0xFFE0E6DE,
            0xFF303C34, 0xFFBFCBC0, 0xFF89958B, 0xFF414D44,
        ))
    }

    private fun assertStyle(name: String, s: TextStyle, size: Float, line: Float, weight: FontWeight?, spacing: Float?, family: FontFamily? = null) {
        assertEquals("$name size", size.sp, s.fontSize)
        assertEquals("$name line", line.sp, s.lineHeight)
        assertEquals("$name weight", weight, s.fontWeight)
        assertEquals("$name spacing", spacing?.sp ?: TextUnit.Unspecified, s.letterSpacing)
        assertEquals("$name family", family, s.fontFamily)
    }

    @Test fun legacyTypographyIsUnchanged() {
        var t: Typography? = null
        compose.setContent { MoosclesTheme("light") { t = MaterialTheme.typography } }
        compose.waitForIdle()
        val ty = t!!
        assertStyle("displaySmall", ty.displaySmall, 34f, 39f, FontWeight.ExtraBold, -1f, FontFamily.SansSerif)
        assertStyle("headlineLarge", ty.headlineLarge, 30f, 36f, FontWeight.Bold, -0.7f)
        assertStyle("headlineMedium", ty.headlineMedium, 25f, 31f, FontWeight.Bold, null)
        assertStyle("headlineSmall", ty.headlineSmall, 22f, 28f, FontWeight.Bold, null)
        assertStyle("titleLarge", ty.titleLarge, 20f, 26f, FontWeight.Bold, null)
        assertStyle("titleMedium", ty.titleMedium, 16f, 23f, FontWeight.SemiBold, null)
        assertStyle("bodyLarge", ty.bodyLarge, 16f, 24f, null, null)
        assertStyle("bodyMedium", ty.bodyMedium, 14f, 21f, null, null)
        assertStyle("labelLarge", ty.labelLarge, 14f, 20f, FontWeight.SemiBold, null)
    }

    @Test fun designTokensFollowTheSameThemeChoice() {
        assertEquals(MoosclesColors.Dark, designColors("dark"))
        assertEquals(MoosclesColors.Light, designColors("light"))
    }

    @Test @Config(qualifiers = "night") fun systemThemeFollowsNightMode() {
        assertEquals(MoosclesColors.Dark, designColors("system"))
    }

    @Test @Config(qualifiers = "notnight") fun systemThemeFollowsDayMode() {
        assertEquals(MoosclesColors.Light, designColors("system"))
    }

    @Test @Config(qualifiers = "night") fun unknownThemeValueFollowsTheSystem() {
        assertEquals(MoosclesColors.Dark, designColors("amoled"))
    }
}
