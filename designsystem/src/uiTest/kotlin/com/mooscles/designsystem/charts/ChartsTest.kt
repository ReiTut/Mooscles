package com.mooscles.designsystem.charts

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.mooscles.designsystem.assertShownWhole
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.mooscles.designsystem.INJECTS_DRAG
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.gallery.pages.ChartSamples
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.saveChartsCapture
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Phase 5A spike S1 (spec D3 and D4): the charts are one image node with a summary, the table
 * swaps in and back, a tap or drag picks the nearest point, the drawing cache is built once per
 * data and size (never for a selection), the height follows the font scale, a new content key
 * cross-fades, and captures in both themes at 1.0 and 2.0 (PNG files on the simulator).
 */
@OptIn(ExperimentalTestApi::class)
class ChartsTest {
    private val summary = "Estimated 1RM: from 80 kg to 92.5 kg; highest 95 kg; trending up; 8 workouts."

    @AfterTest
    fun restoreLanguage() {
        LocalAppLocale.restore()
    }

    private fun ComposeUiTest.screen(
        chart: ChartsTestChart,
        rows: List<ChartTableRowData> = emptyList(),
        width: Dp = 360.dp,
        counter: ChartBuildCounter? = null,
        onSelected: (Int?) -> Unit = {},
    ) {
        withTheme {
            ProvideAppLocale("en") {
                CompositionLocalProvider(LocalChartPathBuilds provides counter?.let { it::record }) {
                    Box(Modifier.width(width)) { ChartsTestScreen(chart, summary, rows, onSelected = onSelected) }
                }
            }
        }
        waitForIdle()
    }

    private val isImage = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    @Test
    fun lineChartIsOneImageNodeWithTheSummary() = runComposeUiTest {
        screen(ChartsTestChart.Line(ChartSamples.line), rowsFor(ChartSamples.line))
        onNodeWithTag(LineChartTag).assertContentDescriptionEquals(summary).assert(isImage)
        onAllNodes(isImage).assertCountEquals(1)
        // Nothing inside: the axis labels are drawn, not nodes, so the summary is all a reader gets.
        assertTrue(onNodeWithTag(LineChartTag, useUnmergedTree = true).fetchSemanticsNode().children.isEmpty())
        onAllNodesWithText("95 kg", substring = true).assertCountEquals(0)
        println("CHARTS semantics line:\n" + onRoot(useUnmergedTree = true).printToString())
    }

    @Test
    fun barChartIsOneImageNodeWithTheSummary() = runComposeUiTest {
        screen(ChartsTestChart.Bar(ChartSamples.bars))
        onNodeWithTag(BarChartTag).assertContentDescriptionEquals(summary).assert(isImage)
        onAllNodes(isImage).assertCountEquals(1)
        assertTrue(onNodeWithTag(BarChartTag, useUnmergedTree = true).fetchSemanticsNode().children.isEmpty())
        println("CHARTS semantics bar:\n" + onRoot(useUnmergedTree = true).printToString())
    }

    /**
     * The sparkline is decorative: its wrapper has no semantic child and nothing in the tree reads.
     * Positive control: it did build a line (one "spark"), and new values build again; no values draw nothing.
     */
    @Test
    fun sparklineHasNoSemantics() = runComposeUiTest {
        val counter = ChartBuildCounter()
        var values by mutableStateOf(ChartSamples.sparkline)
        withTheme {
            CompositionLocalProvider(LocalChartPathBuilds provides counter::record) {
                Box(Modifier.width(200.dp).testTag("spark-host")) { MSparkline(values) }
            }
        }
        waitForIdle()
        val host = onNodeWithTag("spark-host", useUnmergedTree = true)
        assertTrue(host.fetchSemanticsNode().children.isEmpty(), "the sparkline adds no node")
        assertEquals(null, host.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        onAllNodes(hasContentDescription("", substring = true)).assertCountEquals(0)
        assertEquals(1, counter.spark, "the sparkline built its line")
        assertDp(40.dp, host.getUnclippedBoundsInRoot().height, "sparkline height")
        val drawn = captureOrNull("sparkline") { host.captureToImage() }
        if (drawn != null) assertTrue(pixelsNear(drawn, MoosclesColors.Light.chartLine) > 0, "the sparkline draws its line")
        runOnIdle { values = values.map { it + 1 } }
        waitForIdle()
        assertEquals(2, counter.spark, "new values build again")
        runOnIdle { values = emptyList() }
        waitForIdle()
        assertEquals(2, counter.spark, "no values, no line")
    }

    @Test
    fun dataButtonSwapsChartAndTableAndBack() = runComposeUiTest {
        screen(ChartsTestChart.Line(ChartSamples.line), rowsFor(ChartSamples.line))
        onNodeWithTag(LineChartTag).assertExists()
        onNodeWithTag(ChartDataButtonTag).assertTextEquals("View data")
        onNodeWithTag(ChartDataButtonTag).performClick()
        waitForIdle()
        onNodeWithTag(LineChartTag).assertDoesNotExist()
        onAllNodes(isImage).assertCountEquals(0)
        for (i in ChartSamples.line.indices) onNodeWithTag(chartTableRowTag(i.toString())).assertExists()
        onNodeWithTag(ChartDataButtonTag).assertTextEquals("View chart")
        println("CHARTS semantics table:\n" + onRoot().printToString())
        onNodeWithTag(ChartDataButtonTag).performClick()
        waitForIdle()
        onNodeWithTag(LineChartTag).assertContentDescriptionEquals(summary)
        onNodeWithTag(chartTableRowTag("0")).assertDoesNotExist()
        onNodeWithTag(ChartDataButtonTag).assertTextEquals("View data")
        // A 48 dp button.
        assertTrue(onNodeWithTag(ChartDataButtonTag).getUnclippedBoundsInRoot().height >= 48.dp - 0.5.dp)
    }

    @Test
    fun tableRowIsOneNodeAndSaysRecord() = runComposeUiTest {
        val rows = listOf(
            ChartTableRowData("r", "28 Oct", "92.5 kg", "85 kg × 3", record = true),
            ChartTableRowData("p", "12 Sep", "90 kg", null),
        )
        withTheme { ProvideAppLocale("en") { Column { for (row in rows) ChartTableRow(row) } } }
        waitForIdle()
        val record = onNodeWithTag(chartTableRowTag("r")).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("28 Oct", "92.5 kg", "85 kg × 3", "Record"), record)
        val plain = onNodeWithTag(chartTableRowTag("p")).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("12 Sep", "90 kg"), plain)
        // One node each: the texts are merged into the row, none stands alone.
        onAllNodesWithText("Record").assertCountEquals(1)
        onAllNodesWithText("28 Oct").assertCountEquals(1)
        onNodeWithText("Record").assert(hasTestTag(chartTableRowTag("r")))
    }

    @Test
    fun tableRowStacksALongDateAndKeepsTheValueWhole() = runComposeUiTest {
        val long = ChartTableRowData("long", "Week of Oct 26 · this week (in progress)", "12 reps", "Bodyweight × 12")
        val short = ChartTableRowData("short", "28 Oct", "92.5 kg", "85 kg × 3")
        withTheme(fontScale = 2f) {
            Box(Modifier.width(288.dp)) { Column { ChartTableRow(long); ChartTableRow(short) } }
        }
        waitForIdle()
        fun bounds(text: String) = onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
        fun layout(text: String): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
            return results.first()
        }
        for (text in listOf("12 reps", "Bodyweight × 12", "92.5 kg", "85 kg × 3")) assertShownWhole(layout(text), text)
        // The long date takes the line and the value goes under it; the short one stays beside its value.
        assertTrue(bounds("12 reps").top >= bounds(long.label).bottom - 0.5.dp, "the value goes under a long date")
        assertTrue(bounds("92.5 kg").top < bounds(short.label).bottom, "a short date keeps its value beside it")
        // No word of the long date is broken: it wraps only at spaces.
        val label = layout(long.label)
        for (line in 0 until label.lineCount - 1) {
            val end = label.getLineEnd(line, visibleEnd = true)
            assertTrue(end >= long.label.length || long.label[end] == ' ' || long.label[end - 1] == ' ', "line $line breaks a word")
        }
    }

    @Test
    fun tapSelectsTheNearestPointAndDragMovesIt() = runComposeUiTest {
        val points = listOf(LinePoint(0.0, 80.0), LinePoint(1.0, 90.0), LinePoint(2.0, 85.0))
        screen(ChartsTestChart.Line(points), width = 360.dp)
        val chart = onNodeWithTag(LineChartTag)
        val selection = onNodeWithTag(ChartsTestSelectedTag)
        selection.assertTextEquals("none")
        chart.performTouchInput { click(Offset(width - 1f, centerY)) }
        waitForIdle()
        selection.assertTextEquals("2")
        chart.performTouchInput { click(Offset(1f, centerY)) }
        waitForIdle()
        selection.assertTextEquals("0")
        chart.performTouchInput { click(center) }
        waitForIdle()
        selection.assertTextEquals("1")
        if (INJECTS_DRAG) {
            chart.performTouchInput {
                down(Offset(1f, centerY))
                moveTo(Offset(width * 0.25f, centerY))
                moveTo(Offset(centerX, centerY))
            }
            waitForIdle()
            selection.assertTextEquals("1")
            chart.performTouchInput { moveTo(Offset(width - 1f, centerY)) }
            waitForIdle()
            selection.assertTextEquals("2")
            chart.performTouchInput { up() }
            waitForIdle()
            selection.assertTextEquals("2")
        }
    }

    /**
     * Spec S1 (c): with 300 points, 20 selections (10 taps and 10 drag moves) do not rebuild the
     * line; new data does (the positive control). The first draw is timed for the spike report only.
     */
    @Test
    fun pathIsBuiltOnceWhileTheSelectionMoves() = runComposeUiTest {
        val counter = ChartBuildCounter()
        val changes = mutableListOf<Int?>()
        var points by mutableStateOf(ChartSamples.long)
        val timer = DrawTimer()
        withTheme {
            CompositionLocalProvider(LocalChartPathBuilds provides counter::record) {
                Box(Modifier.width(360.dp)) {
                    ChartsTestScreen(
                        ChartsTestChart.Line(points),
                        summary,
                        emptyList(),
                        chartModifier = Modifier.drawWithContent {
                            val mark = TimeSource.Monotonic.markNow()
                            drawContent()
                            if (timer.firstNanos < 0) timer.firstNanos = mark.elapsedNow().inWholeNanoseconds
                        },
                        onSelected = { changes += it },
                    )
                }
            }
        }
        waitForIdle()
        // At least one build on the first draw (an extra first layout on a platform is not a defect); the deltas below are D3.
        val first = counter.line
        assertTrue(first >= 1, "the line was built on the first draw")
        println("CHARTS first draw of 300 points: ${timer.firstNanos / 1_000} us")
        val chart = onNodeWithTag(LineChartTag)
        // Every touch lands inside the plot (past the value labels), on a different point.
        for (i in 0 until 10) {
            chart.performTouchInput { click(Offset(width * (0.3f + 0.07f * i), centerY)) }
            waitForIdle()
        }
        if (INJECTS_DRAG) {
            chart.performTouchInput { down(Offset(width * 0.97f, centerY)) }
            for (i in 0 until 10) {
                chart.performTouchInput { moveTo(Offset(width * (0.9f - 0.06f * i), centerY)) }
                waitForIdle()
            }
            chart.performTouchInput { up() }
            waitForIdle()
        }
        val expected = if (INJECTS_DRAG) 20 else 10
        assertTrue(changes.size >= expected, "the selection moved ${changes.size} times")
        onNodeWithTag(ChartsTestSelectedTag).assertTextEquals(changes.last().toString())
        assertEquals(first, counter.line, "the selection never rebuilt the line")
        runOnIdle { points = points.map { it.copy(y = it.y + 1) } }
        waitForIdle()
        assertEquals(first + 1, counter.line, "new data rebuilds the line once")
    }

    /**
     * Carry from Task 1 (Minor 6): a chart inside a vertical scroll lets a vertical swipe scroll the column without
     * selecting, and takes a horizontal swipe as a selection without scrolling (horizontal slop only, D3).
     */
    @Test
    fun verticalSwipeScrollsAndHorizontalSwipeSelects() = runComposeUiTest {
        val scroll = ScrollState(0)
        val changes = mutableListOf<Int?>()
        withTheme {
            Box(Modifier.height(420.dp)) {
                Column(Modifier.verticalScroll(scroll)) {
                    ChartsTestScreen(ChartsTestChart.Line(ChartSamples.line), summary, emptyList(), onSelected = { changes += it })
                    Spacer(Modifier.height(800.dp))
                }
            }
        }
        waitForIdle()
        val chart = onNodeWithTag(LineChartTag)
        // Horizontal: the chart selects, the column stays.
        chart.performTouchInput { swipe(Offset(width * 0.9f, centerY), Offset(width * 0.3f, centerY), durationMillis = 400) }
        waitForIdle()
        assertTrue(changes.isNotEmpty(), "the horizontal swipe selected nothing")
        assertEquals(0, scroll.value, "the horizontal swipe scrolled the column")
        val picks = changes.size
        // Vertical: the column scrolls, the chart selects nothing more.
        chart.performTouchInput { swipe(Offset(centerX, bottom - 4f), Offset(centerX, top + 4f), durationMillis = 400) }
        waitForIdle()
        assertTrue(scroll.value > 0, "the vertical swipe did not scroll the column")
        assertEquals(picks, changes.size, "the vertical swipe selected a point")
    }

    @Test
    fun resizingRebuildsThePathOnce() = runComposeUiTest {
        val counter = ChartBuildCounter()
        var width by mutableStateOf(360.dp)
        withTheme {
            CompositionLocalProvider(LocalChartPathBuilds provides counter::record) {
                Box(Modifier.width(width)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChartsTestScreen(ChartsTestChart.Line(ChartSamples.line), summary, emptyList())
                        ChartsTestScreen(ChartsTestChart.Bar(ChartSamples.bars), summary, emptyList())
                    }
                }
            }
        }
        waitForIdle()
        val line = counter.line
        val bar = counter.bar
        assertTrue(line >= 1 && bar >= 1, "both charts were built on the first draw ($line, $bar)")
        runOnIdle { width = 300.dp }
        waitForIdle()
        assertEquals(line + 1, counter.line, "a new width rebuilds the line once")
        assertEquals(bar + 1, counter.bar, "a new width rebuilds the bars once")
        // The bar chart's selection does not rebuild either.
        onNodeWithTag(BarChartTag).performTouchInput { click(center) }
        waitForIdle()
        assertEquals(bar + 1, counter.bar)
    }

    @Test
    fun heightGrowsWithFontScaleUpToOneAndAHalf() {
        for ((scale, expected) in listOf(1f to 200.dp, 1.3f to 260.dp, 2f to 300.dp)) {
            runComposeUiTest {
                withTheme(fontScale = scale) {
                    Column {
                        MLineChart(ChartSamples.line, summary, { kilograms(it) }, emptyList(), null, {})
                        MBarChart(ChartSamples.bars, summary, ::kilograms, emptyList(), null, {})
                    }
                }
                waitForIdle()
                assertDp(expected, onNodeWithTag(LineChartTag).getUnclippedBoundsInRoot().height, "line at $scale")
                assertDp(expected, onNodeWithTag(BarChartTag).getUnclippedBoundsInRoot().height, "bars at $scale")
            }
        }
    }

    /**
     * Final review of phase 5A (CR-9): the line chart writes its own marks with the label it is given, a composable one
     * (a screen's reads string resources), so a screen never computes the scale a second time to match texts to ticks.
     */
    @Test
    fun lineChartWritesItsOwnTicksWithTheComposableLabel() = runComposeUiTest {
        var points by mutableStateOf(ChartSamples.line)
        withTheme {
            MLineChart(points, summary, { kilograms(it) }, emptyList(), null, {})
        }
        waitForIdle()
        fun texts() = onNodeWithTag(LineChartTag).fetchSemanticsNode().config[ChartAxisTexts]
        fun expected() = ChartScale.line(points.map { it.y }).ticks.map(::kilograms)
        assertEquals(expected(), texts())
        assertTrue(texts().size in 2..4, "${texts()}")
        points = ChartSamples.flat
        waitForIdle()
        assertEquals(expected(), texts())
    }

    @Test
    fun contentKeyCrossfadesAndReducedMotionSwapsAtOnce() {
        for (reduced in listOf(false, true)) {
            runComposeUiTest {
                var key by mutableStateOf("a")
                withTheme(reducedMotion = reduced) {
                    MLineChart(
                        if (key == "a") ChartSamples.line else ChartSamples.flat,
                        "summary $key",
                        { kilograms(it) },
                        emptyList(),
                        null,
                        {},
                        contentKey = key,
                    )
                }
                waitForIdle()
                onNodeWithTag(ChartFadingTag).assertDoesNotExist()
                mainClock.autoAdvance = false
                key = "b"
                mainClock.advanceTimeByFrame()
                onNodeWithTag(LineChartTag).assertContentDescriptionEquals("summary b")
                // The leaving chart is silent: only the new summary is read.
                onAllNodes(hasContentDescription("summary a")).assertCountEquals(0)
                if (reduced) {
                    onNodeWithTag(ChartFadingTag).assertDoesNotExist()
                } else {
                    onNodeWithTag(ChartFadingTag).assertExists("the old chart fades out")
                    mainClock.advanceTimeBy(MoosclesMotion().base.toLong() / 2)
                    onNodeWithTag(ChartFadingTag).assertExists("still fading halfway")
                }
                mainClock.advanceTimeBy(MoosclesMotion().base.toLong() + 100)
                onNodeWithTag(ChartFadingTag).assertDoesNotExist()
                onAllNodes(isImage).assertCountEquals(1)
            }
        }
    }

    /**
     * Spec S1 (a): the line, the bars and the sparkline in both themes at 1.0 and 2.0 (2.01: linear
     * on every platform). On the simulator each is saved as `charts-<theme>-<scale>.png`; where
     * `captureToImage` is not supported it says so and goes on. Whatever the platform, all three are
     * laid out at their size, and a capture that works shows the chart line's color.
     */
    @Test
    fun capturesBothThemesAtOneAndTwo() {
        for (dark in listOf(false, true)) {
            for ((label, scale) in listOf("1.0" to 1f, "2.0" to LINEAR_TWO_X_FONT_SCALE)) {
                runComposeUiTest {
                    withTheme(dark = dark, fontScale = scale) {
                        ProvideAppLocale("en") {
                            Column(
                                Modifier.width(320.dp).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MLineChart(ChartSamples.line, summary, { kilograms(it) }, ChartScale.labelIndices(8).map { AxisLabel(it, "${it + 1} Oct") }, 5, {})
                                        MChartLegendRecord()
                                    }
                                }
                                MBarChart(ChartSamples.bars, summary, ::kilograms, ChartSamples.bars.indices.map { AxisLabel(it, "W${it + 1}") }, 3, {})
                                Box(Modifier.width(160.dp).testTag("spark")) { MSparkline(ChartSamples.sparkline) }
                            }
                        }
                    }
                    waitForIdle()
                    val theme = if (dark) "dark" else "light"
                    val height = if (scale > 1.5f) 300.dp else 200.dp
                    assertDp(height, onNodeWithTag(LineChartTag).getUnclippedBoundsInRoot().height, "line $theme $label")
                    assertDp(height, onNodeWithTag(BarChartTag).getUnclippedBoundsInRoot().height, "bars $theme $label")
                    assertDp(40.dp, onNodeWithTag("spark").getUnclippedBoundsInRoot().height, "sparkline $theme $label")
                    val image = captureOrNull("charts-$theme-$label") { onRoot().captureToImage() } ?: return@runComposeUiTest
                    assertTrue(image.width > 0 && image.height > 0)
                    val line = if (dark) MoosclesColors.Dark.chartLine else MoosclesColors.Light.chartLine
                    assertTrue(pixelsNear(image, line) > 100, "charts-$theme-$label shows the chart line")
                    saveChartsCapture("charts-$theme-$label", image)
                }
            }
        }
    }

    /** The first draw's time, written on the UI thread and read by the test after it is idle. */
    private class DrawTimer {
        @Volatile var firstNanos = -1L
    }

    private fun assertDp(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs(expected.value - actual.value) <= 1f, "$what: $actual, expected $expected")
    }

    /** The capture, or null (said once) where `captureToImage` is not supported. */
    private inline fun captureOrNull(what: String, capture: () -> ImageBitmap): ImageBitmap? = try {
        capture()
    } catch (failure: Throwable) {
        println("CHARTS capture unsupported ($what): ${failure.message}")
        null
    }

    private fun pixelsNear(image: ImageBitmap, color: Color): Int {
        val pixels = image.toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val p = pixels[x, y]
                if (abs(p.red - color.red) < 0.04f && abs(p.green - color.green) < 0.04f && abs(p.blue - color.blue) < 0.04f) count++
            }
        }
        return count
    }
}
