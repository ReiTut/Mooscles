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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer

/** Everything one line chart draws; a new value of it is a new drawing cache. */
@Immutable
internal data class LineFrame(
    val points: List<LinePoint>,
    val range: ChartRange,
    val tickTexts: List<String>,
    val xLabels: List<AxisLabel>,
    val summary: String,
)

/**
 * A progression line (spec D3 and D4): [points] placed by their `x` across the width and by their
 * `y` on an axis that does not start at zero (10% padding, up to 4 round marks, each written by
 * [yLabel] in composition, so it may read string resources), with [xLabels] under the plot where
 * they fit. A record point gets a `chart.pr` ring. The marks' texts are on the chart's node as
 * [ChartAxisTexts].
 *
 * Tap or drag picks the nearest point ([onSelect]); [selected] gets an `accent.solid` ring and a
 * guide. The line and labels are built once per data and size; moving the selection only redraws
 * the selection. For a screen reader the chart is one image node read as [summary]; the data
 * itself is in the table [ChartDataButton] swaps in. 200 dp tall, growing with the font scale up to
 * 1.5 times. A new [contentKey] (another period or metric) cross-fades, or swaps at once with
 * reduced motion.
 *
 * Keep [selected] as local UI state and write it from [onSelect] synchronously (a `remember`ed state): a selection
 * that round-trips through a view model can lag the finger, and a pick can be dropped. The chart never calls
 * `onSelect(null)`; the caller clears the selection when the period or the metric changes (D3). [xLabels] are drawn
 * as given: up to 5 of them is the caller's choice (`ChartScale.labelIndices`). Dense record rings are thinned so they
 * never touch (`ChartGeometry.ringIndices`); the table lists every record.
 */
@Composable
fun MLineChart(
    points: List<LinePoint>,
    summary: String,
    yLabel: @Composable (Double) -> String,
    xLabels: List<AxisLabel>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    contentKey: Any? = null,
) {
    val range = remember(points) { ChartScale.line(points.map { it.y }) }
    val frame = LineFrame(points, range, range.ticks.map { yLabel(it) }, xLabels, summary)
    ChartCrossfade(contentKey, frame, modifier.fillMaxWidth().height(chartHeight())) { shown, current ->
        LineChartFrame(shown, if (current) selected else null, onSelect, current)
    }
}

@Composable
private fun LineChartFrame(frame: LineFrame, selected: Int?, onSelect: (Int?) -> Unit, current: Boolean) {
    val look = chartLook()
    val measurer = rememberTextMeasurer()
    val probe = LocalChartPathBuilds.current
    val plot = remember { PlotPositions() }
    val cache = remember(frame, look, measurer, probe) {
        Modifier.drawWithCache {
            probe?.invoke(CHART_BUILD_LINE)
            buildLine(frame, look, measurer, plot)
        }
    }
    ChartSurface(LineChartTag, frame.summary, frame.tickTexts, current, plot, selected, onSelect, cache) { drawLineSelection(plot, it, look) }
}

/** Places the points, builds the line and measures the labels; [plot] keeps the positions for the selection. */
private fun CacheDrawScope.buildLine(frame: LineFrame, look: ChartLook, measurer: TextMeasurer, plot: PlotPositions): DrawResult {
    val sizes = look.sizes
    val colors = look.colors
    val gap = look.gap.toPx()
    val reserve = sizes.chartSelectionRing.toPx() + sizes.chartStroke.toPx()
    val axes = measureAxes(measurer, look.text, frame.range, frame.tickTexts, gap, reserve, reserve, frame.xLabels.isNotEmpty())
    val points = frame.points
    val left = axes.left + reserve
    val right = maxOf(left, axes.right)
    val minX = points.minOfOrNull { it.x } ?: 0.0
    val maxX = points.maxOfOrNull { it.x } ?: 0.0
    val xs = points.map { p ->
        if (maxX > minX) left + ((p.x - minX) / (maxX - minX)).toFloat() * (right - left) else (left + right) / 2
    }
    val ys = points.map { ChartGeometry.toPx(it.y, frame.range, axes.top, axes.bottom) }
    plot.xs = xs
    plot.ys = ys
    plot.top = axes.top
    plot.bottom = axes.bottom
    val path = Path()
    for (i in xs.indices) if (i == 0) path.moveTo(xs[i], ys[i]) else path.lineTo(xs[i], ys[i])
    val xLabels = measureXLabels(measurer, look.text, frame.xLabels, xs, axes, gap)
    val strokePx = sizes.chartStroke.toPx()
    val line = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val ring = Stroke(strokePx)
    val pointRadius = sizes.chartPoint.toPx()
    val recordRadius = sizes.chartRecordRing.toPx()
    // Dots only where they stay apart; 300 points on a phone are a line.
    val dots = xs.size == 1 || (right - left) / (xs.size - 1) >= pointRadius * sizes.chartDotSpacing
    // Rings that would touch the last one kept are skipped, so a run of records never becomes a band.
    val rings = ChartGeometry.ringIndices(xs, ys, points.map { it.record }, 2 * recordRadius + strokePx)
    return onDrawBehind {
        drawAxes(axes, xLabels, look)
        if (xs.size > 1) drawPath(path, colors.line, style = line)
        if (dots) for (i in xs.indices) drawCircle(colors.line, pointRadius, Offset(xs[i], ys[i]))
        for (i in rings) drawCircle(colors.record, recordRadius, Offset(xs[i], ys[i]), style = ring)
    }
}

/** The guide and ring of the selected point, over the cached drawing. */
private fun DrawScope.drawLineSelection(plot: PlotPositions, selected: Int, look: ChartLook) {
    val stroke = look.sizes.chartStroke.toPx()
    val x = plot.xs[selected]
    drawLine(look.colors.grid, Offset(x, plot.top), Offset(x, plot.bottom), stroke)
    drawCircle(look.colors.selection, look.sizes.chartSelectionRing.toPx(), Offset(x, plot.ys[selected]), style = Stroke(stroke))
}
