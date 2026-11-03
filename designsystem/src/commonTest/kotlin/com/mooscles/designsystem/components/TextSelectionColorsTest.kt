package com.mooscles.designsystem.components

import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.test.Test
import kotlin.test.assertEquals

class TextSelectionColorsTest {
    @Test
    fun selectionIsDrawnOnlyWithFocusInBothThemes() {
        for (colors in listOf(MoosclesColors.Light, MoosclesColors.Dark)) {
            val focused = textSelectionColors(colors, focused = true)
            assertEquals(colors.accentSubtle, focused.backgroundColor)
            assertEquals(colors.accentSolid, focused.handleColor)
            val resting = textSelectionColors(colors, focused = false)
            assertEquals(Color.Transparent, resting.backgroundColor)
            assertEquals(colors.accentSolid, resting.handleColor)
        }
    }
}
