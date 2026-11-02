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
import kotlin.math.abs

class ShareCardPainterTest {
    private suspend fun paint(inputs: ShareCardInputs, card: ShareCardData = longWorkoutCard()): List<ImageBitmap> =
        withContext(Dispatchers.Default) { ShareCardPainter(inputs.styles, inputs.wordmark).paint(card, inputs.measurer) }

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
            ShareCardPainter(inputs.styles, inputs.wordmark).layout(longWorkoutCard(), inputs.measurer)
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

    /** Every page draws the supplied image, including letter shapes, lime details and transparent gaps. */
    @Test
    fun brandIsDrawn() = runTest {
        val inputs = captureShareCardInputs()
        val painter = ShareCardPainter(inputs.styles, inputs.wordmark)
        val layouts = withContext(Dispatchers.Default) { painter.layout(longWorkoutCard(), inputs.measurer) }
        val source = inputs.wordmark.toPixelMap()
        val background = MoosclesColors.Dark.bgBase
        layouts.forEach { layout ->
            val page = withContext(Dispatchers.Default) { painter.paint(layout) }
            val band = layout.brandBounds
            val pixels = page.toPixelMap(band.left, band.top, band.width, band.height)
            var lime = 0
            var letters = 0
            var gaps = 0
            var checked = 0
            for (x in 1 until source.width - 1 step 2) for (y in 1 until source.height - 1 step 2) {
                val expected = source[x, y]
                // Interior samples avoid treating platform-specific interpolation at an edge as a changed logo.
                val interior = (-1..1).all { dx -> (-1..1).all { dy ->
                    val near = source[x + dx, y + dy]
                    abs(near.alpha - expected.alpha) < 0.01f &&
                        (expected.alpha < 0.01f || (abs(near.red - expected.red) < 0.03f &&
                            abs(near.green - expected.green) < 0.03f && abs(near.blue - expected.blue) < 0.03f))
                } }
                if (!interior || expected.alpha in 0.01f..0.99f) continue
                val actual = pixels[((x + 0.5f) * band.width / source.width).toInt(),
                    ((y + 0.5f) * band.height / source.height).toInt()]
                val color = if (expected.alpha < 0.01f) background else expected
                assertTrue(abs(actual.red - color.red) < 0.08f && abs(actual.green - color.green) < 0.08f &&
                    abs(actual.blue - color.blue) < 0.08f, "wordmark sample $x,$y differs on page ${layout.index}")
                checked++
                when {
                    expected.alpha < 0.01f -> gaps++
                    expected.red > 0.5f && expected.green > 0.7f && expected.blue < 0.3f -> lime++
                    expected.red > 0.75f && expected.green > 0.75f && expected.blue > 0.7f -> letters++
                }
            }
            assertTrue(checked > 100 && lime > 10 && letters > 20 && gaps > 10,
                "incomplete wordmark: checked=$checked lime=$lime letters=$letters gaps=$gaps")
        }
    }
}
