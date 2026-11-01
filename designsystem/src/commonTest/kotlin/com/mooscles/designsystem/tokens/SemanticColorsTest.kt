package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.reflect.KProperty1
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticColorsTest {
    private class Row(val name: String, val token: KProperty1<MoosclesColors, Color>, val light: Long, val dark: Long)

    /** The spec's "Tokens semânticos" table, pasted: every token with its light and dark hex. */
    private val table = listOf(
        Row("bg.base", MoosclesColors::bgBase, 0xFFEFF3F1, 0xFF111513),
        Row("bg.surface", MoosclesColors::bgSurface, 0xFFF9FDFB, 0xFF1A1E1C),
        Row("bg.raised", MoosclesColors::bgRaised, 0xFFFFFFFF, 0xFF232825),
        Row("bg.overlay", MoosclesColors::bgOverlay, 0xFFFFFFFF, 0xFF2A2F2D),
        Row("bg.inset", MoosclesColors::bgInset, 0xFFE5E9E7, 0xFF0A0E0C),
        Row("bg.inverse", MoosclesColors::bgInverse, 0xFF1E2321, 0xFFE2E5E4),
        Row("border.subtle", MoosclesColors::borderSubtle, 0xFFD6DCD9, 0xFF373C39),
        Row("border.strong", MoosclesColors::borderStrong, 0xFF737976, 0xFF737976),
        Row("text.primary", MoosclesColors::textPrimary, 0xFF151917, 0xFFECEFEE),
        Row("text.secondary", MoosclesColors::textSecondary, 0xFF494E4C, 0xFFB9BFBC),
        Row("text.tertiary", MoosclesColors::textTertiary, 0xFF5F6562, 0xFF9AA09D),
        Row("text.disabled", MoosclesColors::textDisabled, 0xFF9AA09D, 0xFF5F6562),
        Row("text.inverse", MoosclesColors::textInverse, 0xFFEFF3F1, 0xFF111513),
        Row("accent.solid", MoosclesColors::accentSolid, 0xFF00764C, 0xFF39CE8E),
        Row("accent.solidPressed", MoosclesColors::accentSolidPressed, 0xFF04623F, 0xFF2EB97F),
        Row("accent.on", MoosclesColors::accentOn, 0xFFFFFFFF, 0xFF031B10),
        Row("accent.text", MoosclesColors::accentText, 0xFF016C46, 0xFF68E3A8),
        Row("accent.subtle", MoosclesColors::accentSubtle, 0xFFCFF2DE, 0xFF143525),
        Row("pr.solid", MoosclesColors::prSolid, 0xFFFFCB77, 0xFFFFCB77),
        Row("pr.on", MoosclesColors::prOn, 0xFF251803, 0xFF251803),
        Row("pr.text", MoosclesColors::prText, 0xFF835A01, 0xFFFFCF83),
        Row("pr.subtle", MoosclesColors::prSubtle, 0xFFFEE8C6, 0xFF3F2C0B),
        Row("danger.solid", MoosclesColors::dangerSolid, 0xFFBA2B28, 0xFFED695E),
        Row("danger.on", MoosclesColors::dangerOn, 0xFFFFFFFF, 0xFF1D0C0A),
        Row("danger.text", MoosclesColors::dangerText, 0xFFB02B27, 0xFFF98478),
        Row("danger.subtle", MoosclesColors::dangerSubtle, 0xFFFFE4E0, 0xFF491F1B),
        Row("focus.ring", MoosclesColors::focusRing, 0xFF016C46, 0xFF68E3A8),
        Row("accent.inverseText", MoosclesColors::accentInverseText, 0xFF68E3A8, 0xFF016C46),
        Row("chart.line", MoosclesColors::chartLine, 0xFF00764C, 0xFF39CE8E),
        Row("chart.pr", MoosclesColors::chartPr, 0xFF604102, 0xFFFFCB77),
        Row("chart.grid", MoosclesColors::chartGrid, 0xFFD6DCD9, 0xFF373C39),
        Row("timer.track", MoosclesColors::timerTrack, 0xFFD6DCD9, 0xFF373C39),
        Row("timer.progress", MoosclesColors::timerProgress, 0xFF00764C, 0xFF39CE8E),
    )

    @Test
    fun everySemanticTokenMatchesTheSpecInBothThemes() {
        assertEquals(33, table.size)
        for (row in table) {
            assertEquals(Color(row.light), row.token.get(MoosclesColors.Light), "${row.name} light")
            assertEquals(Color(row.dark), row.token.get(MoosclesColors.Dark), "${row.name} dark")
        }
    }

    @Test
    fun scrimAndShadowMatchTheSpec() {
        // Spec: scrim rgba(0,0,0,0.56) in dark and rgba(21,25,23,0.40) in light; shadows use rgba(21,25,23).
        assertEquals(Color(0x66151917), MoosclesColors.Light.scrim)
        assertEquals(Color(0x8F000000), MoosclesColors.Dark.scrim)
        assertEquals(Color(0xFF151917), MoosclesColors.Light.shadow)
        assertEquals(Color(0xFF0A0E0C), MoosclesColors.Dark.shadow)
    }
}
