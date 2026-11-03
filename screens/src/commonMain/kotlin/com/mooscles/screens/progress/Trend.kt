package com.mooscles.screens.progress

enum class Trend { UP, DOWN, FLAT }

object Trends {
    private const val MIN_POINTS = 3

    /**
     * Least-squares slope of [ys] over [xs] (days, so uneven spacing counts), projected over the
     * span of [xs] and compared with the mean: beyond 5% of it up or down, otherwise flat.
     * Null with fewer than 3 points, lists of different sizes, a value that is not finite, no span
     * (every x the same), or a mean that is not positive.
     */
    fun of(xs: List<Double>, ys: List<Double>): Trend? {
        val n = xs.size
        if (n != ys.size || n < MIN_POINTS) return null
        if (xs.any { !it.isFinite() } || ys.any { !it.isFinite() }) return null
        val span = xs.max() - xs.min()
        // Equal large xs can leave a rounding residue in the centred sum: no span is no trend.
        if (span == 0.0) return null
        val meanX = xs.sum() / n
        val meanY = ys.sum() / n
        if (meanY <= 0.0) return null
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            sxx += dx * dx
            sxy += dx * (ys[i] - meanY)
        }
        if (sxx == 0.0) return null
        val projected = sxy / sxx * span
        // |projected| > 5% of the mean, written as a multiplication by 20 to stay exact at the edge.
        return when {
            projected * 20 > meanY -> Trend.UP
            projected * 20 < -meanY -> Trend.DOWN
            else -> Trend.FLAT
        }
    }
}
