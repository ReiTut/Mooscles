package com.mooscles.domain

import java.util.Locale
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.pow
import kotlin.math.ulp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The common `DecimalText.fixed2` and `kotlin.math.ulp` against the JVM APIs they replace,
 * `String.format(Locale.US, "%.2f", v)` and `Math.ulp(v)`, over every value set the app can
 * produce plus wide random sweeps.
 */
class DecimalTextJvmEquivalenceTest {
    @Test
    fun fixed2EqualsStringFormatOnEveryThousandthUpTo2000() {
        assertFixed2(thousandths(), expectedCount = 2_000_001)
    }

    /**
     * Every tie up to 4000, every 97th tie up to the heaviest load in pounds (220,462.26), and
     * the eighth-kilogram loads (x.125, x.375, x.625, x.875, all exact ties) up to 100,000.
     */
    @Test
    fun fixed2EqualsStringFormatAroundEveryTie() {
        val ks = (0L..400_000L).asSequence() + (0L..22_046_300L step 97).asSequence() + eighthLoadTies()
        assertFixed2(ties(ks), expectedCount = 6 * (400_001 + 227_282 + 400_000))
    }

    /** From 2^43 to 1e13 a double's spacing is 2^-9, so the tie's own double can print as k.xx4. */
    @Test
    fun fixed2EqualsStringFormatAroundTiesFrom2To43Up() {
        val firstAbove = 879_609_302_220_800L // cents at 2^43
        val lastBelow = 999_999_999_999_999L // 9999999999999.99
        val ks = (firstAbove - 100_000..firstAbove + 100_000).asSequence() + (lastBelow - 200_000..lastBelow).asSequence()
        assertFixed2(ties(ks), expectedCount = 6 * (200_001 + 200_001))
    }

    @Test
    fun fixed2EqualsStringFormatOnUnitConversions() {
        assertFixed2(unitConversions(), expectedCount = 400_001 + 880_001)
    }

    @Test
    fun fixed2EqualsStringFormatOnRandomDoubles() {
        assertFixed2(randomDoubles(), expectedCount = 200_000)
    }

    @Test
    fun fixed2EqualsStringFormatOnSpecialValues() {
        assertFixed2(specialValues(), expectedCount = specialValues().count())
    }

    @Test
    fun ulpEqualsMathUlp() {
        val random = Random(1027)
        val values = thousandths() + ties() + unitConversions() + randomDoubles() + specialValues() +
            sequenceOf(Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE, -Double.MIN_VALUE, 2.2250738585072014E-308) +
            generateSequence { Double.fromBits(random.nextLong()) }.take(100_000)
        var checked = 0
        for (value in values) {
            val expected = Math.ulp(value)
            val actual = value.ulp
            if (expected.isNaN()) assertTrue(actual.isNaN(), "ulp($value) = $actual, Math.ulp = NaN")
            else assertEquals(expected.toRawBits(), actual.toRawBits(), "ulp($value) = $actual, Math.ulp = $expected")
            checked++
        }
        assertTrue(checked > 5_000_000, "Only $checked values checked")
    }

    private fun assertFixed2(values: Sequence<Double>, expectedCount: Int) {
        var checked = 0
        var mismatchCount = 0
        val mismatches = mutableListOf<String>()
        for (value in values) {
            val expected = String.format(Locale.US, "%.2f", value)
            val actual = DecimalText.fixed2(value)
            if (actual != expected) {
                mismatchCount++
                if (mismatches.size < 20) {
                    mismatches += "0x${value.toRawBits().toULong().toString(16)} ($value): fixed2 \"$actual\", JVM \"$expected\""
                }
            }
            checked++
        }
        assertEquals(expectedCount, checked, "values checked")
        assertEquals(0, mismatchCount, "fixed2 differs from String.format on $mismatchCount values:\n" + mismatches.joinToString("\n"))
    }

    private companion object {
        // Mirrors WeightUnits in :app (it moves into :shared later); the const is the same.
        const val POUNDS_PER_KG = 2.2046226218487757

        fun thousandths(): Sequence<Double> = (0..2_000_000).asSequence().map { it / 1000.0 }

        /** The double nearest each tie (2k + 1) / 200 and its two neighbours, with both signs. */
        fun ties(ks: Sequence<Long> = (0L..400_000L).asSequence()): Sequence<Double> = ks.flatMap { k ->
            val tie = (2 * k + 1).toDouble() / 200.0
            sequenceOf(tie, tie.nextUp(), tie.nextDown(), -tie, -tie.nextUp(), -tie.nextDown())
        }

        /** The cents k whose tie (2k + 1) / 200 is n.125, n.375, n.625 or n.875, for n below 100,000. */
        fun eighthLoadTies(): Sequence<Long> =
            (0L until 100_000L).asSequence().flatMap { n -> sequenceOf(100 * n + 12, 100 * n + 37, 100 * n + 62, 100 * n + 87) }

        /** Kilograms shown in pounds, and pound entries stored in kilograms and shown again. */
        fun unitConversions(): Sequence<Double> =
            (0..400_000).asSequence().map { it * 0.25 * POUNDS_PER_KG } +
                (0..880_000).asSequence().map { (it * 0.25 / POUNDS_PER_KG) * POUNDS_PER_KG }

        /** Log-uniform magnitudes from 1e-9 to just below 1e13, with both signs. */
        fun randomDoubles(): Sequence<Double> {
            val random = Random(1027)
            return generateSequence {
                val magnitude = 10.0.pow(random.nextDouble(-9.0, 13.0)).coerceAtMost(1e13.nextDown())
                if (random.nextBoolean()) magnitude else -magnitude
            }.take(200_000)
        }

        fun specialValues(): Sequence<Double> = sequenceOf(
            0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE, -Double.MIN_VALUE, 2.2250738585072014E-308, 1e-300, 0.004999999999999999,
            0.005, 0.0050000000000000001, 0.125, 2.675, 999.995, 1e12 + 0.005, 1e13.nextDown(), -(1e13.nextDown()),
            9999999999999.995, 45.00000000000001, 44.99999999999999, 100000.0, 100000.0 * POUNDS_PER_KG,
        )
    }
}
