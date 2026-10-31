package com.mooscles.designsystem.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.tabular
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors

/** The tag of a line chart's node (the one with the summary). */
const val LineChartTag = "chart-line"

/** The tag of a bar chart's node (the one with the summary). */
const val BarChartTag = "chart-bar"

/** The tag of a chart that is fading out after its content key changed; it has no semantics. */
const val ChartFadingTag = "chart-fading"

/** What each part of a chart is drawn in (spec D3): the latão only on records. */
@Immutable
internal data class ChartColors(
    val line: Color,
    val record: Color,
    val grid: Color,
    val selection: Color,
    val label: Color,
) {
    companion object {
        fun of(colors: MoosclesColors) = ChartColors(
            line = colors.chartLine,
            record = colors.chartPr,
            grid = colors.chartGrid,
            selection = colors.accentSolid,
            label = colors.textSecondary,
        )
    }
}

/**
 * The height of a line or bar chart: the token times the font scale, from 1 up to `chartMaxScale`. Public so a screen's
 * placeholder for a chart (a skeleton while it loads) takes exactly the chart's height.
 */
@Composable
fun chartHeight(): Dp {
    val sizes = MTheme.sizes
    return sizes.chartHeight * LocalDensity.current.fontScale.coerceIn(1f, sizes.chartMaxScale)
}

/**
 * Where the last drawing cache put the points (or bar centers), for the selection layer and the
 * pointer. Written only when the cache is rebuilt (data or size changed); read when drawing the
 * selection and on touch. Not state: nothing composes from it.
 */
internal class PlotPositions {
    var xs: List<Float> = emptyList()
    var ys: List<Float> = emptyList()
    var top = 0f
    var bottom = 0f
}

/** A text measured once in the cache, and where it goes. */
internal class PlacedText(val layout: TextLayoutResult, val topLeft: Offset)

/** The plot area left after the axis labels, the grid line heights and the labels to draw. */
internal class ChartAxes(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val gridYs: List<Float>,
    val yLabels: List<PlacedText>,
    val labelHeight: Float,
)

private fun TextMeasurer.line(text: String, style: TextStyle): TextLayoutResult =
    measure(text, style, overflow = TextOverflow.Visible, softWrap = false, maxLines = 1)

/**
 * Lays out the value axis: the tick labels in a gutter at the start, each centered on its grid
 * line, the plot to their right, [topReserve] px free above the plot and, with [xLabels], a band
 * for them below it. Labels that would overlap are dropped (the table has every value).
 */
internal fun CacheDrawScope.measureAxes(
    measurer: TextMeasurer,
    style: TextStyle,
    range: ChartRange,
    tickTexts: List<String>,
    gap: Float,
    topReserve: Float,
    endReserve: Float,
    xLabels: Boolean,
): ChartAxes {
    val layouts = tickTexts.map { measurer.line(it, style) }
    val labelHeight = measurer.line("0", style).size.height.toFloat()
    val gutter = (layouts.maxOfOrNull { it.size.width } ?: 0).toFloat() + gap
    val top = maxOf(labelHeight / 2, topReserve)
    val bottom = size.height - if (xLabels) labelHeight + gap / 2 else maxOf(labelHeight / 2, topReserve / 2)
    val gridYs = range.ticks.map { ChartGeometry.toPx(it, range, top, bottom) }
    // Top to bottom, the first (highest) label always stays.
    val order = gridYs.indices.sortedBy { gridYs[it] }
    val kept = ChartScale.fitLabels(order.map { gridYs[it] }, order.map { layouts[it].size.height.toFloat() }, gap = 0f)
    val placed = kept.map { k ->
        val i = order[k]
        val layout = layouts[i]
        PlacedText(layout, Offset(gutter - gap - layout.size.width, gridYs[i] - layout.size.height / 2f))
    }
    return ChartAxes(gutter, size.width - endReserve, top, bottom, gridYs, placed, labelHeight)
}

/**
 * The labels under the plot: each centered on its point or bar ([centers], by [AxisLabel.index]),
 * kept inside the chart's width past the value gutter, and dropped when it would overlap the one
 * before it.
 */
internal fun CacheDrawScope.measureXLabels(
    measurer: TextMeasurer,
    style: TextStyle,
    labels: List<AxisLabel>,
    centers: List<Float>,
    axes: ChartAxes,
    gap: Float,
): List<PlacedText> {
    val usable = labels.filter { it.index in centers.indices }.sortedBy { centers[it.index] }
    val layouts = usable.map { measurer.line(it.text, style) }
    val lefts = usable.mapIndexed { i, label ->
        val width = layouts[i].size.width.toFloat()
        (centers[label.index] - width / 2).coerceIn(axes.left, maxOf(axes.left, size.width - width))
    }
    val kept = ChartScale.fitLabels(
        lefts.mapIndexed { i, left -> left + layouts[i].size.width / 2f },
        layouts.map { it.size.width.toFloat() },
        gap,
    )
    return kept.map { PlacedText(layouts[it], Offset(lefts[it], axes.bottom + gap / 2)) }
}

/** The grid lines, the value labels and the [xLabels]. */
internal fun DrawScope.drawAxes(axes: ChartAxes, xLabels: List<PlacedText>, look: ChartLook) {
    val gridWidth = look.sizes.border.toPx()
    for (y in axes.gridYs) drawLine(look.colors.grid, Offset(axes.left, y), Offset(axes.right, y), gridWidth)
    for (label in axes.yLabels) drawText(label.layout, look.colors.label, label.topLeft)
    for (label in xLabels) drawText(label.layout, look.colors.label, label.topLeft)
}

/**
 * The last selection the chart knows of: the one composed (set after each composition) or, between
 * two compositions, the last one it reported. Several touch events can arrive before the next
 * composition (a drag that crosses two points in one frame); comparing them with the composed
 * selection would drop a pick that goes back to it. Not state.
 */
internal class SelectionTracker {
    var last: Int? = null
}

/**
 * Tap or horizontal drag picks the point or bar nearest the finger (spec D3). Only the index is
 * reported, and only when it changes; nothing is announced (the table is the screen reader's way).
 */
internal fun Modifier.chartSelection(
    plot: PlotPositions,
    tracker: SelectionTracker,
    onSelect: State<(Int?) -> Unit>,
): Modifier {
    fun pick(x: Float) {
        val index = ChartGeometry.nearest(plot.xs, x) ?: return
        if (index == tracker.last) return
        tracker.last = index
        onSelect.value(index)
    }
    return pointerInput(plot) { detectTapGestures(onTap = { pick(it.x) }) }
        .pointerInput(plot) {
            detectHorizontalDragGestures(onDragStart = { pick(it.x) }) { change, _ ->
                change.consume()
                pick(change.position.x)
            }
        }
}

/** The tokens a chart draws with, read once per composition. */
@Immutable
internal data class ChartLook(
    val colors: ChartColors,
    val sizes: MoosclesSizes,
    val text: TextStyle,
    val gap: Dp,
    val corner: Dp,
)

@Composable
internal fun chartLook(): ChartLook = ChartLook(
    colors = ChartColors.of(MTheme.colors),
    sizes = MTheme.sizes,
    text = MTheme.type.caption.tabular(),
    gap = MTheme.space.s2,
    corner = MTheme.radius.xs,
)

/**
 * The node a chart draws in: [cache] (built only when the data or the size change), then the
 * selection on top ([drawSelection], redrawn alone when [selected] moves). The current chart is one
 * image node read as [summary] under [tag], and takes taps and drags; a chart that is fading out
 * ([current] false) is silent and takes nothing.
 */
@Composable
internal fun ChartSurface(
    tag: String,
    summary: String,
    current: Boolean,
    plot: PlotPositions,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    cache: Modifier,
    drawSelection: DrawScope.(index: Int) -> Unit,
) {
    val tracker = remember { SelectionTracker() }
    SideEffect { tracker.last = selected }
    val onSelectState = rememberUpdatedState(onSelect)
    val node = if (current) {
        Modifier.testTag(tag).clearAndSetSemantics {
            contentDescription = summary
            role = Role.Image
        }.chartSelection(plot, tracker, onSelectState)
    } else {
        Modifier.testTag(ChartFadingTag).clearAndSetSemantics {}
    }
    Spacer(
        Modifier
            .fillMaxSize()
            .then(node)
            .drawWithContent {
                drawContent()
                if (selected != null && selected in plot.xs.indices) drawSelection(selected)
            }
            .then(cache),
    )
}

/** The key and frame last composed, so the next key knows what to fade out. Not state. */
private class ShownFrame<F>(var key: Any?, var frame: F)

/**
 * Shows [content] for [frame]. When [contentKey] changes (another period or metric), the frame
 * that was shown fades out over `motion.base` while the new one fades in; with reduced motion the
 * new one replaces it at once. A new frame under the same key (data that moved) just redraws.
 * [content] gets `current = false` for the frame that is leaving.
 */
@Composable
internal fun <F> ChartCrossfade(
    contentKey: Any?,
    frame: F,
    modifier: Modifier,
    content: @Composable (frame: F, current: Boolean) -> Unit,
) {
    val reduced = MTheme.reducedMotion
    val duration = MTheme.motion.base
    val shown = remember { ShownFrame(contentKey, frame) }
    // Taken when the key changes, before the side effect below records the new key.
    val leaving = remember(contentKey) { if (shown.key != contentKey) ShownFrame(shown.key, shown.frame) else null }
    val fade = remember(contentKey) { Animatable(if (leaving != null && !reduced) 0f else 1f) }
    val fading by remember(fade) { derivedStateOf { fade.value < 1f } }
    LaunchedEffect(fade) { if (fade.value < 1f) fade.animateTo(1f, tween(duration)) }
    SideEffect {
        shown.key = contentKey
        shown.frame = frame
    }
    Box(modifier) {
        if (leaving != null && fading) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - fade.value }) {
                key(leaving.key) { content(leaving.frame, false) }
            }
        }
        Box(Modifier.matchParentSize().graphicsLayer { alpha = fade.value }) {
            key(contentKey) { content(frame, true) }
        }
    }
}
