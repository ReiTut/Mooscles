package com.mooscles.designsystem.charts

import kotlin.math.abs

/** The pure geometry of the charts: picking what a finger is on and placing a value on the axis. */
internal object ChartGeometry {
    /**
     * The index of the x position in [xs] closest to [touchX]; on a tie, the leftmost one. Null when
     * there is nothing to pick.
     */
    fun nearest(xs: List<Float>, touchX: Float): Int? {
        var best: Int? = null
        var bestDistance = Float.MAX_VALUE
        for ((i, x) in xs.withIndex()) {
            val distance = abs(x - touchX)
            val closer = distance < bestDistance
            val tieToTheLeft = distance == bestDistance && best != null && x < xs[best]
            if (closer || tieToTheLeft) {
                best = i
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * The indices of the record rings to draw, oldest first: from the newest record back, a ring whose centre is closer
     * than [minDistance] to the last ring kept is skipped. Dense records (a beginner's run of them, or many points on a
     * narrow chart) then stay separate rings instead of piling into one band over the line; the newest record, the one
     * that still holds, always has its ring, and the data table lists every record.
     */
    fun ringIndices(xs: List<Float>, ys: List<Float>, records: List<Boolean>, minDistance: Float): List<Int> {
        val kept = ArrayList<Int>()
        var lastX = 0f
        var lastY = 0f
        for (i in xs.indices.reversed()) {
            if (!records[i]) continue
            val dx = xs[i] - lastX
            val dy = ys[i] - lastY
            if (kept.isEmpty() || dx * dx + dy * dy >= minDistance * minDistance) {
                kept += i
                lastX = xs[i]
                lastY = ys[i]
            }
        }
        kept.reverse()
        return kept
    }

    /** The y pixel of [value] when [range] runs from [bottom] (its min) up to [top] (its max). */
    fun toPx(value: Double, range: ChartRange, top: Float, bottom: Float): Float {
        val span = range.max - range.min
        if (span <= 0.0) return (top + bottom) / 2
        val fraction = ((value - range.min) / span).toFloat()
        return bottom - fraction * (bottom - top)
    }
}
