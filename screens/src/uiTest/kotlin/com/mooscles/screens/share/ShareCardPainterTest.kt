package com.mooscles.screens.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareCardPainterTest {
    private suspend fun paint(inputs: ShareCardInputs, card: ShareCardData = longWorkoutCard()): List<ImageBitmap> =
        withContext(Dispatchers.Default) { ShareCardPainter(inputs.styles).paint(card, inputs.measurer) }

    @Test
    fun everyPageIs1080Wide() = runTest {
        val pages = paint(captureShareCardInputs())
        assertTrue(pages.isNotEmpty())
        pages.forEach { page ->
            assertEquals(ShareCardPainter.WIDTH, page.width)
            assertTrue(page.height in 1..ShareCardPainter.MAX_PAGE_HEIGHT, "height ${page.height}")
        }
    }

    @Test
    fun longWorkoutUsesTheSamePageCountAsToday() = runTest {
        val inputs = captureShareCardInputs()
        val pages = withContext(Dispatchers.Default) {
            ShareCardPainter(inputs.styles).layout(longWorkoutCard(), inputs.measurer)
        }
        println("SHARE layout pages=${pages.size} heights=${pages.map { it.height }} rows=${pages.map { it.slices.size }}")
        assertEquals(TODAY_LONG_WORKOUT_PAGES, pages.size)
    }

    /** The card is a brand piece: graphite whatever theme the app is in when it is drawn. */
    @Test
    fun backgroundIsDarkInBothThemes() = runTest {
        val background = MoosclesColors.Dark.bgBase
        for (dark in listOf(false, true)) {
            val pages = paint(captureShareCardInputs(dark))
            pages.forEach { page ->
                val pixels = page.toPixelMap()
                for ((x, y) in listOf(0 to 0, page.width - 1 to 0, 0 to page.height - 1, page.width - 1 to page.height - 1)) {
                    assertEquals(background, pixels[x, y], "dark=$dark at $x,$y")
                }
            }
        }
    }

    /** "MOOSCLES" with the two plates: plate green and text pixels in the brand band of every page. */
    @Test
    fun brandIsDrawn() = runTest {
        val inputs = captureShareCardInputs()
        val painter = ShareCardPainter(inputs.styles)
        val layouts = withContext(Dispatchers.Default) { painter.layout(longWorkoutCard(), inputs.measurer) }
        val colors = MoosclesColors.Dark
        layouts.forEach { layout ->
            val page = withContext(Dispatchers.Default) { painter.paint(layout) }
            val band = layout.brandBounds
            val pixels = page.toPixelMap(band.left, band.top, band.width, band.height)
            var plate = 0
            var text = 0
            for (x in 0 until band.width) for (y in 0 until band.height) {
                when (pixels[x, y]) {
                    colors.accentSolid -> plate++
                    colors.textPrimary -> text++
                }
            }
            println("SHARE brand plate=$plate text=$text band=$band")
            assertTrue(plate > 0, "no plate pixels")
            assertTrue(text > 0, "no letter pixels")
        }
    }
}
