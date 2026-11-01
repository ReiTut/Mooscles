package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.reflect.KProperty1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTokensTest {
    private enum class Kind { Text, NonText, Exempt, Decorative }

    private class PairRow(
        val name: String,
        val fg: KProperty1<MoosclesColors, Color>,
        val bg: KProperty1<MoosclesColors, Color>,
        val kind: Kind,
        val light: Double,
        val dark: Double,
    )

    private fun pair(fg: KProperty1<MoosclesColors, Color>, bg: KProperty1<MoosclesColors, Color>, kind: Kind, light: Double, dark: Double) =
        PairRow("${fg.name} on ${bg.name}", fg, bg, kind, light, dark)

    /** The spec's "Pares medidos" table, pasted (50 rows). */
    private val pairs = listOf(
        pair(MoosclesColors::textPrimary, MoosclesColors::bgBase, Kind.Text, light = 15.85, dark = 15.91),
        pair(MoosclesColors::textPrimary, MoosclesColors::bgSurface, Kind.Text, light = 17.30, dark = 14.56),
        pair(MoosclesColors::textPrimary, MoosclesColors::bgRaised, Kind.Text, light = 17.75, dark = 12.95),
        pair(MoosclesColors::textPrimary, MoosclesColors::bgOverlay, Kind.Text, light = 17.75, dark = 11.76),
        pair(MoosclesColors::textPrimary, MoosclesColors::bgInset, Kind.Text, light = 14.49, dark = 16.79),
        pair(MoosclesColors::textSecondary, MoosclesColors::bgBase, Kind.Text, light = 7.57, dark = 9.86),
        pair(MoosclesColors::textSecondary, MoosclesColors::bgSurface, Kind.Text, light = 8.26, dark = 9.02),
        pair(MoosclesColors::textSecondary, MoosclesColors::bgRaised, Kind.Text, light = 8.48, dark = 8.02),
        pair(MoosclesColors::textSecondary, MoosclesColors::bgOverlay, Kind.Text, light = 8.48, dark = 7.29),
        pair(MoosclesColors::textSecondary, MoosclesColors::bgInset, Kind.Text, light = 6.92, dark = 10.40),
        pair(MoosclesColors::textTertiary, MoosclesColors::bgBase, Kind.Text, light = 5.32, dark = 6.92),
        pair(MoosclesColors::textTertiary, MoosclesColors::bgSurface, Kind.Text, light = 5.81, dark = 6.33),
        pair(MoosclesColors::textTertiary, MoosclesColors::bgRaised, Kind.Text, light = 5.96, dark = 5.63),
        pair(MoosclesColors::textTertiary, MoosclesColors::bgOverlay, Kind.Text, light = 5.96, dark = 5.11),
        pair(MoosclesColors::textTertiary, MoosclesColors::bgInset, Kind.Text, light = 4.86, dark = 7.30),
        pair(MoosclesColors::textInverse, MoosclesColors::bgInverse, Kind.Text, light = 14.24, dark = 14.52),
        pair(MoosclesColors::accentInverseText, MoosclesColors::bgInverse, Kind.Text, light = 9.97, dark = 5.12),
        pair(MoosclesColors::textPrimary, MoosclesColors::accentSubtle, Kind.Text, light = 14.71, dark = 11.59),
        pair(MoosclesColors::textSecondary, MoosclesColors::accentSubtle, Kind.Text, light = 7.03, dark = 7.18),
        pair(MoosclesColors::accentText, MoosclesColors::accentSubtle, Kind.Text, light = 5.38, dark = 8.39),
        pair(MoosclesColors::accentOn, MoosclesColors::accentSolid, Kind.Text, light = 5.68, dark = 8.90),
        pair(MoosclesColors::accentOn, MoosclesColors::accentSolidPressed, Kind.Text, light = 7.44, dark = 7.16),
        pair(MoosclesColors::accentText, MoosclesColors::bgBase, Kind.Text, light = 5.80, dark = 11.52),
        pair(MoosclesColors::accentText, MoosclesColors::bgSurface, Kind.Text, light = 6.33, dark = 10.54),
        pair(MoosclesColors::accentText, MoosclesColors::bgRaised, Kind.Text, light = 6.49, dark = 9.38),
        pair(MoosclesColors::accentText, MoosclesColors::bgOverlay, Kind.Text, light = 6.49, dark = 8.52),
        pair(MoosclesColors::accentSolid, MoosclesColors::bgSurface, Kind.NonText, light = 5.54, dark = 8.34),
        pair(MoosclesColors::accentSolid, MoosclesColors::bgRaised, Kind.NonText, light = 5.68, dark = 7.41),
        pair(MoosclesColors::prOn, MoosclesColors::prSolid, Kind.Text, light = 11.61, dark = 11.61),
        pair(MoosclesColors::prText, MoosclesColors::bgSurface, Kind.Text, light = 5.97, dark = 11.64),
        pair(MoosclesColors::prText, MoosclesColors::bgRaised, Kind.Text, light = 6.13, dark = 10.35),
        pair(MoosclesColors::prText, MoosclesColors::prSubtle, Kind.Text, light = 5.13, dark = 9.20),
        pair(MoosclesColors::textPrimary, MoosclesColors::prSubtle, Kind.Text, light = 14.85, dark = 11.51),
        pair(MoosclesColors::dangerOn, MoosclesColors::dangerSolid, Kind.Text, light = 6.06, dark = 6.12),
        pair(MoosclesColors::dangerText, MoosclesColors::bgSurface, Kind.Text, light = 6.36, dark = 6.90),
        pair(MoosclesColors::dangerText, MoosclesColors::bgOverlay, Kind.Text, light = 6.52, dark = 5.57),
        pair(MoosclesColors::dangerText, MoosclesColors::dangerSubtle, Kind.Text, light = 5.41, dark = 5.75),
        pair(MoosclesColors::textPrimary, MoosclesColors::dangerSubtle, Kind.Text, light = 14.73, dark = 12.14),
        pair(MoosclesColors::borderStrong, MoosclesColors::bgBase, Kind.NonText, light = 3.97, dark = 4.14),
        pair(MoosclesColors::borderStrong, MoosclesColors::bgSurface, Kind.NonText, light = 4.33, dark = 3.79),
        pair(MoosclesColors::borderStrong, MoosclesColors::bgRaised, Kind.NonText, light = 4.44, dark = 3.37),
        pair(MoosclesColors::borderStrong, MoosclesColors::bgInset, Kind.NonText, light = 3.63, dark = 4.37),
        pair(MoosclesColors::focusRing, MoosclesColors::bgBase, Kind.NonText, light = 5.80, dark = 11.52),
        pair(MoosclesColors::focusRing, MoosclesColors::bgSurface, Kind.NonText, light = 6.33, dark = 10.54),
        pair(MoosclesColors::focusRing, MoosclesColors::bgRaised, Kind.NonText, light = 6.49, dark = 9.38),
        pair(MoosclesColors::chartLine, MoosclesColors::bgSurface, Kind.NonText, light = 5.54, dark = 8.34),
        pair(MoosclesColors::chartPr, MoosclesColors::bgSurface, Kind.NonText, light = 9.08, dark = 11.28),
        pair(MoosclesColors::timerProgress, MoosclesColors::bgRaised, Kind.NonText, light = 5.68, dark = 7.41),
        pair(MoosclesColors::textDisabled, MoosclesColors::bgSurface, Kind.Exempt, light = 2.59, dark = 2.83),
        pair(MoosclesColors::borderSubtle, MoosclesColors::bgSurface, Kind.Decorative, light = 1.36, dark = 1.50),
    )

    private fun ratio(p: PairRow, colors: MoosclesColors) = Contrast.ratio(p.fg.get(colors), p.bg.get(colors))

    @Test
    fun theTableHasFiftyRows() {
        assertEquals(50, pairs.size)
    }

    @Test
    fun everyTextPairMeetsFourAndAHalfInBothThemes() {
        for (p in pairs.filter { it.kind == Kind.Text }) {
            assertTrue(ratio(p, MoosclesColors.Light) >= 4.5, "${p.name} light")
            assertTrue(ratio(p, MoosclesColors.Dark) >= 4.5, "${p.name} dark")
        }
    }

    @Test
    fun everyNonTextPairMeetsThreeInBothThemes() {
        for (p in pairs.filter { it.kind == Kind.NonText }) {
            assertTrue(ratio(p, MoosclesColors.Light) >= 3.0, "${p.name} light")
            assertTrue(ratio(p, MoosclesColors.Dark) >= 3.0, "${p.name} dark")
        }
    }

    @Test
    fun ratiosMatchTheSpecTable() {
        for (p in pairs) {
            assertEquals(p.light, round2(ratio(p, MoosclesColors.Light)), 0.0101, "${p.name} light")
            assertEquals(p.dark, round2(ratio(p, MoosclesColors.Dark)), 0.0101, "${p.name} dark")
        }
    }

    @Test
    fun exemptPairsAreOnlyDisabledTextAndSubtleBorder() {
        // Exemption comes from the token names, not from the Kind tag of the table row.
        val exemptNames = setOf("textDisabled", "borderSubtle")
        assertEquals(2, pairs.count { it.fg.name in exemptNames })
        for (p in pairs.filter { it.fg.name !in exemptNames }) {
            val isText = p.fg.name.startsWith("text") || p.fg.name.endsWith("Text") ||
                p.fg.name.endsWith("On") || p.fg.name == "accentInverseText"
            val minimum = if (isText) 4.5 else 3.0
            assertEquals(if (isText) Kind.Text else Kind.NonText, p.kind, "${p.name} kind")
            assertTrue(ratio(p, MoosclesColors.Light) >= minimum, "${p.name} light")
            assertTrue(ratio(p, MoosclesColors.Dark) >= minimum, "${p.name} dark")
        }
    }

    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
}
