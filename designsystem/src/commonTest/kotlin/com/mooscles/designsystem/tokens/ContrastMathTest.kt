package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContrastMathTest {
    @Test
    fun knownRatios() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.001)
        assertEquals(1.0, Contrast.ratio(Primitives.verde50, Primitives.verde50), 0.0001)
        // #767676 on white is the classic minimal AA gray.
        assertEquals(4.54, Contrast.ratio(Color(0xFF767676), Color.White), 0.01)
    }

    @Test
    fun theOrderOfTheColorsDoesNotMatter() {
        assertEquals(
            Contrast.ratio(Primitives.ferro21, Primitives.ferro96),
            Contrast.ratio(Primitives.ferro96, Primitives.ferro21),
            0.0,
        )
    }

    @Test
    fun translucentColorsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Contrast.ratio(Color(0x80000000), Color.White) }
        assertFailsWith<IllegalArgumentException> { Contrast.relativeLuminance(Color(0x80FFFFFF)) }
    }
}
