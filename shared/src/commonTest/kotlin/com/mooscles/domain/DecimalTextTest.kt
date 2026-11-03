package com.mooscles.domain

import kotlin.math.nextDown
import kotlin.math.ulp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fixed tables that run on every platform, the iOS simulator included. Every expected
 * value was printed by the JVM (Temurin 21): `String.format(Locale.US, "%.2f", v)` and
 * `Double.doubleToRawLongBits(Math.ulp(v))`.
 */
class DecimalTextTest {
    @Test
    fun fixed2MatchesTheJavaFormatterTable() {
        val table = listOf(
            0.0 to "0.00",
            -0.0 to "-0.00",
            0.005 to "0.01",
            0.015 to "0.02",
            1.005 to "1.01",
            2.675 to "2.68",
            999.995 to "1000.00",
            -0.001 to "-0.00",
            1e-7 to "0.00",
            45.00000000000001 to "45.00",
            44.99999999999999 to "45.00",
            // WeightUnits.kgToLb(100000.0), the heaviest load EditRules accepts, shown in pounds.
            100000.0 * 2.2046226218487757 to "220462.26",
            Double.NaN to "NaN",
            Double.POSITIVE_INFINITY to "Infinity",
            Double.NEGATIVE_INFINITY to "-Infinity",
            0.125 to "0.13",
            0.375 to "0.38",
            -2.675 to "-2.68",
            -0.005 to "-0.01",
            1.115 to "1.12",
            1.125 to "1.13",
            0.995 to "1.00",
            -0.995 to "-1.00",
            0.045 to "0.05",
            0.055 to "0.06",
            0.565 to "0.57",
            12.345 to "12.35",
            1234.5 to "1234.50",
            45.359237 to "45.36",
            102.05828325 to "102.06",
            Double.MIN_VALUE to "0.00",
            1.000000000000005E12 to "1000000000000.01",
            9999999999999.99 to "9999999999999.99",
            9.999999999999994E12 to "9999999999999.99",
        )
        for ((value, expected) in table) {
            assertEquals(expected, DecimalText.fixed2(value), "fixed2($value)")
        }
    }

    @Test
    fun ulpMatchesTheJavaTable() {
        val table = listOf(
            0.0 to 0x1L,
            -0.0 to 0x1L,
            Double.MIN_VALUE to 0x1L,
            2.2250738585072014E-308 to 0x1L,
            1.0 to 0x3cb0000000000000L,
            20.0 to 0x3cf0000000000000L,
            100.0 to 0x3d10000000000000L,
            45.359237 to 0x3d00000000000000L,
            102.05828325 to 0x3d10000000000000L,
            100000.0 to 0x3db0000000000000L,
            -100000.0 to 0x3db0000000000000L,
            1.0.nextDown() to 0x3ca0000000000000L,
            2.0.nextDown() to 0x3cb0000000000000L,
            // The largest subnormal, just below Double's smallest normal 2.2250738585072014E-308.
            Double.fromBits(0x000fffffffffffffL) to 0x1L,
            Double.MAX_VALUE to 0x7ca0000000000000L,
            Double.POSITIVE_INFINITY to 0x7ff0000000000000L,
            Double.NaN to 0x7ff8000000000000L,
        )
        for ((value, bits) in table) {
            assertEquals(bits, value.ulp.toRawBits(), "ulp($value)")
        }
    }
}
