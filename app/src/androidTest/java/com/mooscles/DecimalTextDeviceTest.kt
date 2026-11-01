package com.mooscles

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mooscles.domain.DecimalText
import java.util.Locale
import kotlin.math.nextDown
import kotlin.math.nextUp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The common `DecimalText.fixed2` against ART's `java.util.Formatter`, which the app used before. */
@RunWith(AndroidJUnit4::class)
class DecimalTextDeviceTest {
    @Test
    fun fixed2EqualsStringFormatOnArt() {
        val thousandths = (0..200_000).asSequence().map { it / 1000.0 }
        // Every tie up to 1000, every 997th tie up to the heaviest load in pounds (220,462.26), and
        // the eighth-kilogram loads x.125, x.375, x.625 and x.875 (exact ties) for every 13th n below 100,000.
        val ks = (0L..100_000L).asSequence() + (0L..22_046_300L step 997).asSequence() +
            (0L until 100_000L step 13).asSequence().flatMap { n -> sequenceOf(100 * n + 12, 100 * n + 37, 100 * n + 62, 100 * n + 87) }
        val ties = ks.flatMap { k ->
            val tie = (2 * k + 1).toDouble() / 200.0
            sequenceOf(tie, tie.nextUp(), tie.nextDown(), -tie, -tie.nextUp(), -tie.nextDown())
        }
        val special = sequenceOf(
            0.0, -0.0, -0.001, 1e-7, Double.MIN_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            2.675, 999.995, 45.00000000000001, 44.99999999999999, 100000.0 * 2.2046226218487757,
        )
        var checked = 0
        val mismatches = mutableListOf<String>()
        for (value in thousandths + ties + special) {
            val expected = String.format(Locale.US, "%.2f", value)
            val actual = DecimalText.fixed2(value)
            if (actual != expected) mismatches += "$value: fixed2 \"$actual\", ART \"$expected\""
            checked++
        }
        assertEquals(200_001 + 6 * (100_001 + 22_113 + 4 * 7_693) + 13, checked)
        assertEquals("fixed2 differs from ART's String.format:\n" + mismatches.take(20).joinToString("\n"), 0, mismatches.size)
    }
}
