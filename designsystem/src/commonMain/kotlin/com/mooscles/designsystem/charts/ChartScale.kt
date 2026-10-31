package com.mooscles.designsystem.charts

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Axis ranges, tick marks and label choices of the charts (spec D3). The values are in the unit
 * the screen shows (plan decision 15), so a pound axis gets round pound ticks.
 */
object ChartScale {
    /** At most this many marks on the value axis. */
    private const val MAX_TICKS = 4

    /** The share of the data's span added below and above a line. */
    private const val PADDING = 0.1

    /** The round steps: these times a power of ten. */
    private val MANTISSAS = doubleArrayOf(1.0, 2.0, 2.5, 5.0)

    /** Tolerance for a value that lands on a tick up to floating point noise. */
    private const val EPSILON = 1e-9

    /**
     * A span this small next to the values is unit noise, not a change: 100 lb and 100.00000000000001 lb (a kg to lb
     * round trip) are one value. Far under what a label can show (two decimals) and over the domain's 4 ulp.
     */
    private const val FLAT_RELATIVE = 1e-9

    /**
     * The range of a progression line: the data's own span, never forced to zero, with 10% of it
     * added on each side, and up to 4 round ticks (1, 2, 2.5 or 5 times a power of ten) inside it.
     * One value (or all equal, up to floating point noise: [FLAT_RELATIVE]) still gets a range around it. Values
     * that are all zero or more never get a range below zero.
     */
    fun line(values: List<Double>): ChartRange {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return ChartRange(0.0, 1.0, ticksInside(0.0, 1.0))
        val low = finite.min()
        var high = finite.max()
        // Noise between equal values would be drawn as a full-height change with repeated "100" ticks.
        if (high - low <= max(abs(low), abs(high)) * FLAT_RELATIVE) high = low
        val span = high - low
        val padding = if (span > 0) span * PADDING else max(abs(low) * PADDING, 1.0)
        var min = low - padding
        val max = high + padding
        if (low >= 0 && min < 0) min = 0.0
        return ChartRange(min, max, ticksInside(min, max))
    }

    /**
     * The range of a bar chart: from zero up to the first round mark at or over [maxValue], with up
     * to 4 marks. With [integerTicks] (counts, such as workouts a week) every mark is a whole number.
     */
    fun bar(maxValue: Double, integerTicks: Boolean = false): ChartRange {
        val top = if (maxValue.isFinite() && maxValue > 0) maxValue else 0.0
        if (top == 0.0) return ChartRange(0.0, 1.0, listOf(0.0, 1.0))
        var exponent = floor(log10(top / (MAX_TICKS - 1))).toInt() - 1
        if (integerTicks) exponent = max(exponent, 0)
        while (true) {
            for (mantissa in MANTISSAS) {
                // 2.5 is not a whole step; 25, 250... are.
                if (integerTicks && exponent == 0 && mantissa == 2.5) continue
                val step = tick(1, mantissa, exponent)
                val count = ceil(top / step - EPSILON).toLong()
                if (count <= MAX_TICKS - 1) {
                    val ticks = (0..count).map { tick(it, mantissa, exponent) }
                    return ChartRange(0.0, ticks.last(), ticks)
                }
            }
            exponent++
        }
    }

    /**
     * Up to [max] indices out of [count], spread evenly, always with the first and the last
     * (count 300 gives 0, 75, 150, 224, 299).
     */
    fun labelIndices(count: Int, max: Int = 5): List<Int> {
        if (count <= 0 || max <= 0) return emptyList()
        if (count <= max) return List(count) { it }
        if (max == 1) return listOf(0)
        val last = count - 1
        val parts = max - 1
        return List(max) { i -> (i * last + parts / 2) / parts }.distinct()
    }

    /**
     * Which labels fit side by side: label i spans [centers] i ± half its [widths]; going from the
     * first, a label stays when it starts at least [gap] after the end of the last one kept. The
     * first always stays. Returns the indices kept, ascending.
     */
    fun fitLabels(centers: List<Float>, widths: List<Float>, gap: Float): List<Int> {
        if (centers.isEmpty()) return emptyList()
        val kept = mutableListOf(0)
        var end = centers[0] + widths[0] / 2
        for (i in 1 until centers.size) {
            val start = centers[i] - widths[i] / 2
            if (start >= end + gap) {
                kept += i
                end = centers[i] + widths[i] / 2
            }
        }
        return kept
    }

    /** Up to 4 round ticks inside [min]..[max]: the densest round step that gives no more than 4. */
    private fun ticksInside(min: Double, max: Double): List<Double> {
        val span = max - min
        var exponent = floor(log10(span / 10)).toInt()
        while (true) {
            for (mantissa in MANTISSAS) {
                val step = tick(1, mantissa, exponent)
                val first = ceil(min / step - EPSILON).toLong()
                val last = floor(max / step + EPSILON).toLong()
                if (last - first + 1 <= MAX_TICKS) return (first..last).map { tick(it, mantissa, exponent) }
            }
            exponent++
        }
    }

    /**
     * [k] steps of mantissa × 10^exponent, computed so the value is as round as a double gets:
     * a negative exponent divides by an exact power of ten instead of multiplying by 0.1.
     */
    private fun tick(k: Long, mantissa: Double, exponent: Int): Double =
        if (exponent >= 0) k * mantissa * 10.0.pow(exponent) else k * mantissa / 10.0.pow(-exponent)

    private fun tick(k: Int, mantissa: Double, exponent: Int): Double = tick(k.toLong(), mantissa, exponent)
}
