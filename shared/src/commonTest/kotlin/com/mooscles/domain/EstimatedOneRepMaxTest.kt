package com.mooscles.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EstimatedOneRepMaxTest {
    private fun assertClose(expected: Double, actual: Double?) {
        assertNotNull(actual)
        assertEquals(expected, actual, 1e-9)
    }

    @Test
    fun oneRepIsTheLoad() {
        assertEquals(100.0, EstimatedOneRepMax.estimate(100.0, 1))
    }

    @Test
    fun epleyVectors() {
        assertClose(100.0 * (1 + 5 / 30.0), EstimatedOneRepMax.estimate(100.0, 5))
        assertClose(116.666666666667, EstimatedOneRepMax.estimate(100.0, 5))
        assertClose(93.5, EstimatedOneRepMax.estimate(85.0, 3))
        assertClose(106.666666666667, EstimatedOneRepMax.estimate(80.0, 10))
    }

    @Test
    fun tenIsTheLastAndElevenHasNone() {
        assertNotNull(EstimatedOneRepMax.estimate(60.0, 10))
        assertNull(EstimatedOneRepMax.estimate(60.0, 11))
        assertEquals(10, EstimatedOneRepMax.MAX_REPS)
    }

    @Test
    fun zeroRepsOrZeroLoadHasNone() {
        assertNull(EstimatedOneRepMax.estimate(100.0, 0))
        assertNull(EstimatedOneRepMax.estimate(100.0, -1))
        assertNull(EstimatedOneRepMax.estimate(0.0, 5))
    }

    @Test
    fun negativeOrNonFiniteLoadHasNone() {
        assertNull(EstimatedOneRepMax.estimate(-1.0, 5))
        assertNull(EstimatedOneRepMax.estimate(Double.NaN, 5))
        assertNull(EstimatedOneRepMax.estimate(Double.POSITIVE_INFINITY, 5))
    }
}
