package com.mooscles.screens.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendTest {
    private val days = listOf(0.0, 7.0, 14.0, 21.0)

    @Test
    fun risingIsUp() = assertEquals(Trend.UP, Trends.of(days, listOf(80.0, 85.0, 90.0, 95.0)))

    @Test
    fun fallingIsDown() = assertEquals(Trend.DOWN, Trends.of(days, listOf(95.0, 90.0, 85.0, 80.0)))

    @Test
    fun flatIsFlat() = assertEquals(Trend.FLAT, Trends.of(days, listOf(100.0, 101.0, 99.0, 100.0)))

    @Test
    fun fivePercentIsStillFlat() {
        // Slope 2.5 over 2 days projects exactly 5 on a mean of 100.
        val xs = listOf(0.0, 1.0, 2.0)
        assertEquals(Trend.FLAT, Trends.of(xs, listOf(97.5, 100.0, 102.5)))
        assertEquals(Trend.FLAT, Trends.of(xs, listOf(102.5, 100.0, 97.5)))
        assertEquals(Trend.UP, Trends.of(xs, listOf(97.0, 100.0, 103.0)))
    }

    @Test
    fun twoPointsHaveNoTrend() = assertNull(Trends.of(listOf(0.0, 7.0), listOf(80.0, 120.0)))

    @Test
    fun onePointHasNoTrend() = assertNull(Trends.of(listOf(0.0), listOf(80.0)))

    @Test
    fun zeroMeanHasNoTrend() = assertNull(Trends.of(listOf(0.0, 1.0, 2.0), listOf(-1.0, 0.0, 1.0)))

    @Test
    fun noSpanHasNoTrend() = assertNull(Trends.of(listOf(20391.3, 20391.3, 20391.3), listOf(90.0, 100.0, 110.0)))

    @Test
    fun aValueThatIsNotFiniteHasNoTrend() {
        assertNull(Trends.of(listOf(0.0, 1.0, 2.0), listOf(90.0, Double.NaN, 110.0)))
        assertNull(Trends.of(listOf(0.0, Double.POSITIVE_INFINITY, 2.0), listOf(90.0, 100.0, 110.0)))
    }

    @Test
    fun listsOfDifferentSizesHaveNoTrend() = assertNull(Trends.of(listOf(0.0, 1.0, 2.0), listOf(90.0, 100.0)))

    @Test
    fun unevenSpacingUsesTheRealDays() {
        val ys = listOf(100.0, 100.0, 100.0, 108.0)
        // Evenly spaced, the last jump is 7% of the mean; spread over the real days it is 3%.
        assertEquals(Trend.UP, Trends.of(listOf(0.0, 1.0, 2.0, 3.0), ys))
        assertEquals(Trend.FLAT, Trends.of(listOf(0.0, 28.0, 29.0, 30.0), ys))
    }
}
