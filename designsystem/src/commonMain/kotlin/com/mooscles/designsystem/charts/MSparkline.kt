package com.mooscles.designsystem.charts

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mooscles.designsystem.theme.MTheme

/**
 * A line of [values] without axes, 40 dp tall and as wide as it is given, in `chart.line` (spec D3).
 * Decorative: it has no semantics, so the text beside it is what a screen reader reads. The values
 * are spread evenly from the first to the last; one value is a flat line, none draws nothing.
 */
@Composable
fun MSparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val color = MTheme.colors.chartLine
    val stroke = MTheme.sizes.chartStroke
    val probe = LocalChartPathBuilds.current
    val cache = remember(values, color, stroke, probe) {
        Modifier.drawWithCache {
            val finite = values.filter { it.isFinite() }
            if (finite.isEmpty()) return@drawWithCache onDrawBehind {}
            probe?.invoke(CHART_BUILD_SPARK)
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val low = finite.min()
            val high = finite.max()
            val top = inset
            val bottom = size.height - inset
            val left = inset
            val right = size.width - inset
            val path = Path()
            if (finite.size == 1 || high == low) {
                val y = (top + bottom) / 2
                path.moveTo(left, y)
                path.lineTo(right, y)
            } else {
                val step = (right - left) / (finite.size - 1)
                for ((i, value) in finite.withIndex()) {
                    val x = left + step * i
                    val y = bottom - ((value - low) / (high - low)).toFloat() * (bottom - top)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            }
            val line = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind { drawPath(path, color, style = line) }
        }
    }
    Spacer(modifier.fillMaxWidth().height(MTheme.sizes.sparklineHeight).then(cache))
}
