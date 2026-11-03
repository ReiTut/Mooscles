package com.mooscles.screens.share

import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.tokens.Contrast
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.test.Test
import kotlin.test.assertTrue

/** The pairs `ShareCardPainter` draws, always on the dark palette: text 4.5:1, icons and the plates 3:1. */
class ShareCardColorsTest {
    private val c = MoosclesColors.Dark

    private fun atLeast(min: Double, fg: Color, bg: Color, what: String) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue(ratio >= min, "$what is $ratio:1, below $min:1")
    }

    @Test
    fun cardPairsAreLegibleOnTheGraphite() {
        atLeast(4.5, c.textPrimary, c.bgBase, "brand, name, statistics and exercises")
        atLeast(4.5, c.textSecondary, c.bgBase, "date, labels, sets and page number")
        atLeast(4.5, c.prText, c.bgBase, "record section title, record loads and record best sets")
        atLeast(4.5, c.prOn, c.prSolid, "badge label and icon")
        atLeast(3.0, c.prSolid, c.bgBase, "record icon and badge")
        atLeast(3.0, c.accentSolid, c.bgBase, "the plates of the brand")
    }
}
