package com.mooscles.designsystem.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

/** Everything one bar chart draws; a new value of it is a new drawing cache. */
@Immutable
internal data class BarFrame(
    val bars: List<BarDatum>,
    val range: ChartRange,
    val tickTexts: List<String>,
    val xLabels: List<AxisLabel>,
    val summary: String,
)

/**
 * One bar per bucket (spec D3), always from zero, `radius.xs` corners on top, in `chart.line`, 4 dp
 * apart; a zero bucket draws no bar. Up to 4 round marks on the value axis (whole numbers with
 * [integerTicks], for counts), labelled by [yLabel], and [xLabels] under the bars where they fit.
 *
 * Tap or drag picks the nearest bar ([onSelect]); [selected] gets a guide and an `accent.solid`
 * ring over its top. The bars and labels are built once per data and size. For a screen reader the
 * chart is one image node read as [summary]. Height, cross-fade and reduced motion as in [MLineChart].
 */
@Composable
fun MBarChart(
    bars: List<BarDatum>,
    summary: String,
    yLabel: (Double) -> String,
    xLabels: List<AxisLabel>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    integerTicks: Boolean = false,
    contentKey: Any? = null,
) {
    val range = remember(bars, integerTicks) { ChartScale.bar(bars.maxOfOrNull { it.value } ?: 0.0, integerTicks) }
    val frame = BarFrame(bars, range, range.ticks.map(yLabel), xLabels, summary)
    ChartCrossfade(contentKey, frame, modifier.fillMaxWidth().height(chartHeight())) { shown, current ->
        BarChartFrame(shown, if (current) selected else null, onSelect, current)
    }
}

@Composable
private fun BarChartFrame(frame: BarFrame, selected: Int?, onSelect: (Int?) -> Unit, current: Boolean) {
    val look = chartLook()
    val measurer = rememberTextMeasurer()
    val probe = LocalChartPathBuilds.current
    val plot = remember { PlotPositions() }
    val cache = remember(frame, look, measurer, probe) {
        Modifier.drawWithCache {
            probe?.invoke(CHART_BUILD_BAR)
            buildBars(frame, look, measurer, plot)
        }
    }
    ChartSurface(BarChartTag, frame.summary, frame.tickTexts, current, plot, selected, onSelect, cache) { drawBarSelection(plot, it, look) }
}

/** Places the bars, builds them as one path and measures the labels; [plot] keeps the bar centers and tops. */
private fun CacheDrawScope.buildBars(frame: BarFrame, look: ChartLook, measurer: TextMeasurer, plot: PlotPositions): DrawResult {
    val sizes = look.sizes
    val gap = look.gap.toPx()
    val stroke = sizes.chartStroke.toPx()
    // Room above the tallest bar for the selection ring that sits over it.
    val topReserve = (sizes.chartSelectionRing.toPx() + stroke) * 2
    val axes = measureAxes(measurer, look.text, frame.range, frame.tickTexts, gap, topReserve, 0f, frame.xLabels.isNotEmpty())
    val count = frame.bars.size
    val slot = if (count > 0) (axes.right - axes.left) / count else 0f
    // Narrow bars keep at least half their slot.
    val barWidth = (slot - minOf(sizes.chartBarGap.toPx(), slot / 2)).coerceAtLeast(0f)
    val corner = look.corner.toPx()
    val xs = List(count) { i -> axes.left + slot * i + slot / 2 }
    val tops = frame.bars.map { ChartGeometry.toPx(it.value.coerceAtLeast(0.0), frame.range, axes.top, axes.bottom) }
    plot.xs = xs
    plot.ys = tops
    plot.top = axes.top
    plot.bottom = axes.bottom
    val path = Path()
    for (i in 0 until count) {
        val height = axes.bottom - tops[i]
        if (height <= 0f || barWidth <= 0f) continue
        val radius = CornerRadius(minOf(corner, barWidth / 2, height))
        path.addRoundRect(
            RoundRect(
                left = xs[i] - barWidth / 2,
                top = tops[i],
                right = xs[i] + barWidth / 2,
                bottom = axes.bottom,
                topLeftCornerRadius = radius,
                topRightCornerRadius = radius,
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    val xLabels = measureXLabels(measurer, look.text, frame.xLabels, xs, axes, gap)
    return onDrawBehind {
        drawAxes(axes, xLabels, look)
        drawPath(path, look.colors.line)
    }
}

/**
 * The selected bar: a guide from the top of the plot down to the bar and a ring just over its top,
 * clear of the bar (the bar and the ring are the same green, so the ring never sits on it).
 */
private fun DrawScope.drawBarSelection(plot: PlotPositions, selected: Int, look: ChartLook) {
    val x = plot.xs[selected]
    val stroke = look.sizes.chartStroke.toPx()
    val ring = look.sizes.chartSelectionRing.toPx()
    val ringY = plot.ys[selected] - ring - stroke
    if (ringY - ring > plot.top) drawLine(look.colors.grid, Offset(x, plot.top), Offset(x, ringY - ring), stroke)
    drawCircle(look.colors.selection, ring, Offset(x, ringY), style = Stroke(stroke))
}
