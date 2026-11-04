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

    @Test
    fun everyPageRepeatsTheWorkoutWidePrStatisticAndDrawsIt() = runTest {
        val inputs = captureShareCardInputs()
        val card = longWorkoutCard()
        val painter = ShareCardPainter(inputs.styles, inputs.wordmark)
        val layouts = withContext(Dispatchers.Default) { painter.layout(card, inputs.measurer) }
        assertTrue(layouts.size > 1)
        layouts.forEach { layout ->
            assertEquals(listOf(card.durationText, card.setsText, card.prsText, card.volumeText),
                layout.header.stats.map { it.value.layoutInput.text.text })
            assertEquals(listOf("DURATION", "SETS", "PRS", "VOLUME"),
                layout.header.stats.map { it.label.layoutInput.text.text })
            val bounds = layout.statisticBounds[2]
            val image = withContext(Dispatchers.Default) { painter.paint(layout) }
            val pixels = image.toPixelMap(bounds.left, bounds.top, bounds.width, bounds.height)
            var ink = 0
            for (y in 0 until bounds.height) for (x in 0 until bounds.width) {
                if (pixels[x, y] != MoosclesColors.Dark.bgBase) ink++
            }
            assertTrue(ink > 100, "The PR value and label must actually be drawn on page ${layout.index}")
        }
    }

    @Test
    fun typicalFourStatisticsStayInOneRowWithSetsBesidePrs() = runTest {
        val inputs = captureShareCardInputs()
        val card = longWorkoutCard().copy(durationText = "1:24:25", setsText = "12", prCount = 11, prsText = "11",
            volumeText = "6,561\u00A0kg")
        val page = withContext(Dispatchers.Default) {
            ShareCardPainter(inputs.styles, inputs.wordmark).layout(card, inputs.measurer).first()
        }
        assertEquals(1, page.statisticBounds.map { it.top }.distinct().size)
        assertTrue(page.statisticBounds.zipWithNext().all { (left, right) -> left.right <= right.left })
        assertWholeStatistics(page)
    }

    @Test
    fun wideValuesAndLargerStylesReflowWithoutShrinkingOrClipping() = runTest {
        val inputs = captureShareCardInputs()
        val larger = inputs.styles.let { style -> ShareCardStyles(style.brand, style.title, style.date,
            style.statValue.copy(fontSize = style.statValue.fontSize * 2f), style.statLabel.copy(fontSize = style.statLabel.fontSize * 2f),
            style.section, style.rowName, style.rowValue, style.rowCaption, style.badge, style.footer) }
        val card = longWorkoutCard().copy(durationText = "123:45:56", setsText = "12", prCount = 11, prsText = "11",
            volumeText = "1.234.567,89\u00A0kg", labels = longWorkoutCard().labels.copy(duration = "Duração", sets = "Séries"))
        val page = withContext(Dispatchers.Default) {
            ShareCardPainter(larger, inputs.wordmark).layout(card, inputs.measurer).first()
        }
        val (duration, sets, prs, volume) = page.statisticBounds
        assertTrue(duration.bottom <= sets.top)
        assertEquals(sets.top, prs.top)
        assertTrue(sets.right <= prs.left)
        assertTrue(maxOf(sets.bottom, prs.bottom) <= volume.top)
        assertTrue(page.header.stats.all { it.value.layoutInput.style.fontSize == larger.statValue.fontSize },
            "Wide statistics reflow instead of shrinking")
        assertWholeStatistics(page)
        val extreme = card.copy(setsText = Int.MAX_VALUE.toString(), prCount = Int.MAX_VALUE,
            prsText = Int.MAX_VALUE.toString())
        val extremePages = withContext(Dispatchers.Default) {
            ShareCardPainter(larger, inputs.wordmark).layout(extreme, inputs.measurer)
        }
        extremePages.forEach { part ->
            assertEquals(4, part.statisticBounds.map { it.top }.distinct().size,
                "Extremely wide set and PR counts stack in reading order")
            assertTrue(part.height <= ShareCardPainter.MAX_PAGE_HEIGHT)
            assertWholeStatistics(part)
        }
    }

    private fun assertWholeStatistics(page: ShareCardPage) {
        page.header.stats.forEach { stat ->
            assertTrue(!stat.value.hasVisualOverflow, "Value ${stat.value.layoutInput.text.text} is clipped")
            assertTrue(!stat.label.hasVisualOverflow, "Label ${stat.label.layoutInput.text.text} is clipped")
            assertTrue(stat.left >= 0 && stat.left + stat.width <= ShareCardPainter.WIDTH)
            assertTrue(stat.labelTop + stat.label.size.height < page.bodyTop)
        }
    }
}
