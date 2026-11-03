package com.mooscles.designsystem.charts

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.ulp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Phase 5A (spec D3, plan Task 1): the axis ranges and tick marks of the charts. */
class ChartScaleTest {
    private fun step(range: ChartRange): Double {
        assertTrue(range.ticks.size >= 2, "a range needs two ticks to have a step: $range")
        return range.ticks[1] - range.ticks[0]
    }

    /** The step is 1, 2, 2.5 or 5 times a power of ten. */
    private fun assertRoundStep(step: Double, what: String) {
        val magnitude = 10.0.pow(floor(log10(step)))
        val mantissa = step / magnitude
        assertTrue(listOf(1.0, 2.0, 2.5, 5.0).any { abs(it - mantissa) < 1e-9 }, "$what: step $step is not round")
    }

    /** Every tick is a whole multiple of the step, evenly spaced and inside the range. */
    private fun assertTicksOnTheStep(range: ChartRange, what: String) {
        val step = step(range)
        assertRoundStep(step, what)
        for ((i, tick) in range.ticks.withIndex()) {
            val multiple = tick / step
            assertTrue(abs(multiple - round(multiple)) < 1e-9, "$what: tick $tick is not a multiple of $step")
            if (i > 0) assertTrue(abs(tick - range.ticks[i - 1] - step) < 1e-9, "$what: ticks are not evenly spaced: ${range.ticks}")
            assertTrue(tick >= range.min - 1e-9 && tick <= range.max + 1e-9, "$what: tick $tick outside ${range.min}..${range.max}")
        }
    }

    @Test
    fun lineRangePadsTenPercentAndRoundsTicks() {
        val range = ChartScale.line(listOf(80.0, 92.5, 95.0))
        assertTicksOnTheStep(range, "80..95")
        assertTrue(range.ticks.size <= 4, "at most 4 ticks: ${range.ticks}")
        assertTrue(range.min < 80.0, "the low end is padded: ${range.min}")
        assertTrue(range.max > 95.0, "the high end is padded: ${range.max}")
        // At least 10% of the data's span on each side (1.5 here), so the extreme points never sit on the edge.
        assertTrue(range.min <= 80.0 - 1.5 + 1e-9 && range.max >= 95.0 + 1.5 - 1e-9, "10% padding: $range")
    }

    @Test
    fun lineRangeOfOneValueStillHasARange() {
        val range = ChartScale.line(listOf(100.0))
        assertTrue(range.max > range.min, "$range")
        assertTrue(100.0 > range.min && 100.0 < range.max, "the value is inside: $range")
        assertTicksOnTheStep(range, "one value")
        assertTrue(range.ticks.size <= 4)
    }

    /**
     * Final review of phase 5A (CR-2): values equal up to floating point noise (a kg to lb round trip) are one value, not
     * a full-height change with repeated "100" ticks.
     */
    @Test
    fun floatNoiseIsAFlatLine() {
        val noisy = 100.0 + 100.0.ulp
        assertTrue(noisy > 100.0)
        val range = ChartScale.line(listOf(100.0, noisy, 100.0))
        assertEquals(ChartScale.line(listOf(100.0)), range)
        assertEquals(range.ticks.distinct(), range.ticks, "no repeated tick")
        assertTicksOnTheStep(range, "noise")
        // A real change, however small next to the value, is still a range of its own.
        val real = ChartScale.line(listOf(100.0, 100.5))
        assertTrue(real.min > 99.0 && real.max < 101.5, "$real")
    }

    /** Plan decision 15: the charts get display units, so a pound axis gets round pound ticks. */
    @Test
    fun poundTicksAreRound() {
        val kilograms = listOf(36.29, 38.56, 40.82, 43.09, 45.36)
        val pounds = kilograms.map { it * 2.20462262185 }
        val range = ChartScale.line(pounds)
        assertTicksOnTheStep(range, "pounds")
        assertTrue(range.ticks.size in 2..4, "${range.ticks}")
        assertTrue(range.min < pounds.min() && range.max > pounds.max(), "$range")
    }

    @Test
    fun barRangeStartsAtZero() {
        for (max in listOf(14_200.0, 9_800.0, 1.0, 37.5)) {
            val range = ChartScale.bar(max)
            assertEquals(0.0, range.min, "max $max")
            assertEquals(0.0, range.ticks.first(), "max $max")
            assertTrue(range.max >= max, "the tallest bar fits: $range")
            assertTrue(range.ticks.size <= 4, "${range.ticks}")
            assertTicksOnTheStep(range, "bar $max")
        }
        // Nothing to show still gives a range to draw the baseline in.
        val empty = ChartScale.bar(0.0)
        assertEquals(0.0, empty.min)
        assertTrue(empty.max > 0.0, "$empty")
    }

    @Test
    fun integerTicksForCounts() {
        val range = ChartScale.bar(3.0, integerTicks = true)
        assertEquals(0.0, range.min)
        assertTrue(range.ticks.all { it == round(it) }, "whole counts: ${range.ticks}")
        assertTrue(range.ticks.last() >= 3.0, "${range.ticks}")
        assertTrue(range.ticks.size <= 4)
        // One workout a week: no half workouts on the axis.
        val one = ChartScale.bar(1.0, integerTicks = true)
        assertTrue(one.ticks.all { it == round(it) }, "${one.ticks}")
        assertTrue(one.ticks.last() >= 1.0)
    }

    @Test
    fun labelIndicesKeepFirstAndLastAndAtMostFive() {
        val many = ChartScale.labelIndices(300)
        assertEquals(5, many.size, "$many")
        assertEquals(0, many.first())
        assertEquals(299, many.last())
        assertEquals(many.sorted().distinct(), many, "ascending, no repeats")
        assertEquals(listOf(0, 1), ChartScale.labelIndices(2))
        assertEquals(listOf(0), ChartScale.labelIndices(1))
        assertEquals(emptyList(), ChartScale.labelIndices(0))
        assertEquals(listOf(0, 1, 2), ChartScale.labelIndices(3))
        assertEquals(listOf(0, 6, 12), ChartScale.labelIndices(13, max = 3))
    }

    @Test
    fun fitLabelsDropsOverlapsAndKeepsTheFirst() {
        // Four labels 40 px wide centered 30 px apart: only every other one fits with a 4 px gap.
        assertEquals(listOf(0, 2), ChartScale.fitLabels(listOf(20f, 50f, 80f, 110f), listOf(40f, 40f, 40f, 40f), gap = 4f))
        // Wide apart, all fit.
        assertEquals(listOf(0, 1, 2), ChartScale.fitLabels(listOf(20f, 100f, 180f), listOf(40f, 40f, 40f), gap = 4f))
        // A label too wide for anything: the first one stays.
        assertEquals(listOf(0), ChartScale.fitLabels(listOf(100f, 110f), listOf(400f, 40f), gap = 4f))
        assertEquals(emptyList(), ChartScale.fitLabels(emptyList(), emptyList(), gap = 4f))
        // Touching after the gap fits exactly.
        assertEquals(listOf(0, 1), ChartScale.fitLabels(listOf(20f, 64f), listOf(40f, 40f), gap = 4f))
    }
}
