package com.mooscles.domain

import kotlin.math.abs
import kotlin.math.floor

/** Fixed-point decimal text in pure Kotlin, without the JVM's `Formatter`. */
object DecimalText {
    /** [fixed2] is proven equal to the JVM below this; [roundsUpPast] needs a spacing of 2^-9 or less (below 2^44). */
    private const val EXACT_LIMIT = 1e13

    /**
     * The text the JVM's `Formatter` writes for `"%.2f"` in `Locale.US`, for NaN,
     * ±Infinity and every finite [value] with `|value| < 1e13`.
     *
     * The JVM's formatter rounds the shortest decimal digits of the double HALF_UP, so
     * 2.675 (really 2.67499…) gives "2.68" and 0.125 gives "0.13". Here a magnitude rounds up
     * past cent `k` when it is above the double nearest the decimal tie `(2k + 1) / 200`, or
     * is that double and its shortest digits are not below the tie (see [roundsUpPast]).
     * The sign is kept, so -0.0 and negatives that round to zero give "-0.00".
     *
     * From 1e13 up this returns `value.toString()` instead, a documented divergence that
     * never happens in the app: `EditRules` limits a load to 100,000 kg.
     */
    fun fixed2(value: Double): String {
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0.0) "Infinity" else "-Infinity"
        val magnitude = abs(value)
        if (magnitude >= EXACT_LIMIT) return value.toString()

        // a * 100 is not exact, so start just below its floor and settle on the first
        // cent the magnitude does not round up past. Both loops use the same rule.
        var cents = maxOf(0L, floor(magnitude * 100.0).toLong() - 1)
        while (cents > 0 && !roundsUpPast(magnitude, cents - 1)) cents--
        while (roundsUpPast(magnitude, cents)) cents++

        val fraction = cents % 100
        return buildString {
            if (value.toRawBits() < 0L) append('-')
            append(cents / 100)
            append('.')
            if (fraction < 10) append('0')
            append(fraction)
        }
    }

    /**
     * Whether the shortest digits of [magnitude] are at or above the tie between cents [k] and
     * k + 1. Above or below the tie's double, the digits are on the same side, because digits
     * that read back as a larger double are larger. On the tie's double itself, the digits are
     * the tie `k.xx5` while a double's spacing is at most 0.001. From 2^43 up the spacing is
     * 2^-9, so the shortest digits are whichever of `k.xx4`, `k.xx5` and `k.xx6` lies nearest
     * the exact value, and only `k.xx4` rounds down.
     */
    private fun roundsUpPast(magnitude: Double, k: Long): Boolean {
        val tie = tie(k)
        if (magnitude != tie) return magnitude > tie
        if (magnitude < TWO_TO_43) return true
        // magnitude = units / 512 exactly, and k.xx4 is nearer than k.xx5 when magnitude is below
        // their midpoint (20k + 9) / 2000. Both sides times 64000, in Long (units < 2^53).
        val units = (magnitude * 512.0).toLong()
        return units * 125 >= (20 * k + 9) * 32
    }

    private const val TWO_TO_43 = 8_796_093_022_208.0

    /** The double nearest the decimal tie between cents [k] and k + 1; IEEE division rounds correctly. */
    private fun tie(k: Long): Double = (2 * k + 1).toDouble() / 200.0
}
