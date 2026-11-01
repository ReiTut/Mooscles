package com.mooscles.screens.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressNumbersTest {
    @Test
    fun estimateRoundsToHalfAKilo() {
        assertEquals(93.5, ProgressNumbers.roundEstimate(93.6, pounds = false))
        assertEquals(94.0, ProgressNumbers.roundEstimate(93.8, pounds = false))
        assertEquals(93.5, ProgressNumbers.roundEstimate(93.5, pounds = false))
    }

    @Test
    fun estimateRoundsToOnePound() {
        // 100 kg is 220.46 lb: the estimate is rounded in the unit it is shown in.
        assertEquals(220.0, ProgressNumbers.roundEstimate(100.0, pounds = true))
        assertEquals(221.0, ProgressNumbers.roundEstimate(100.3, pounds = true))
    }

    @Test
    fun displayValueConvertsOnlyLoads() {
        assertEquals(12.0, ProgressNumbers.displayValue(12.0, ProgressMetric.REPS, pounds = true))
        assertEquals(12.0, ProgressNumbers.displayValue(12.0, ProgressMetric.REPS, pounds = false))
        assertEquals(92.5, ProgressNumbers.displayValue(92.5, ProgressMetric.LOAD, pounds = false))
        assertEquals(220.0, ProgressNumbers.displayValue(100.0, ProgressMetric.E1RM, pounds = true))
        assertEquals(93.5, ProgressNumbers.displayValue(93.6, ProgressMetric.E1RM, pounds = false))
        val volumeLb = ProgressNumbers.displayValue(1000.0, ProgressMetric.VOLUME, pounds = true)
        assertTrue(volumeLb > 2204.0 && volumeLb < 2205.0, "volume converts to lb: $volumeLb")
    }

    @Test
    fun shortFormFromTenThousand() {
        assertFalse(ProgressNumbers.isShort(9_999.0))
        assertTrue(ProgressNumbers.isShort(10_000.0))
    }
}
