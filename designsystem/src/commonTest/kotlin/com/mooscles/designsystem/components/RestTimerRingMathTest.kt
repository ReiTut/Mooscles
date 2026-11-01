package com.mooscles.designsystem.components

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestTimerRingMathTest {
    @Test
    fun progressIsLinear() {
        assertEquals(0.5f, ringProgress(30_000, 60_000, reduced = false))
        assertEquals(1f, ringProgress(60_000, 60_000, reduced = false))
        assertEquals(0f, ringProgress(0, 60_000, reduced = false))
        assertEquals(0.25f, ringProgress(15_000, 60_000, reduced = false))
        // Clamped: more time left than the total (after +15 s) is a full ring, and no total is none.
        assertEquals(1f, ringProgress(70_000, 60_000, reduced = false))
        assertEquals(1f, ringProgress(70_000, 60_000, reduced = true))
        assertEquals(0f, ringProgress(30_000, 0, reduced = false))
        assertEquals(0f, ringProgress(30_000, 0, reduced = true))
    }

    @Test
    fun reducedMotionStepsBySecond() {
        // 59.4 s left shows 1:00 on the clock, so the ring is at exactly 60/120.
        assertEquals(60f / 120f, ringProgress(59_400, 120_000, reduced = true))
        assertEquals(59_400f / 120_000f, ringProgress(59_400, 120_000, reduced = false))
        assertEquals(ringProgress(59_001, 120_000, reduced = true), ringProgress(60_000, 120_000, reduced = true))
    }

    @Test
    fun sixtyTicksSixDegreesApart() {
        val angles = tickAngles()
        assertEquals(60, angles.size)
        assertEquals(0f, angles.first())
        for (i in 1 until angles.size) assertEquals(6f, angles[i] - angles[i - 1])
        assertEquals(354f, angles.last())
    }

    @Test
    fun stackFitsInCircleChecksEveryCorner() {
        // One row centered on the circle: its corner is at (30, 40), 50 from the center.
        assertTrue(stackFitsInCircle(listOf(Size(60f, 80f)), 50f))
        assertFalse(stackFitsInCircle(listOf(Size(62f, 80f)), 50f))
        // Stacked, the narrow top row sits farther out: (30, 40) fits 50, (30, 41) does not.
        assertTrue(stackFitsInCircle(listOf(Size(60f, 20f), Size(80f, 40f), Size(60f, 20f)), 50f))
        assertFalse(stackFitsInCircle(listOf(Size(60f, 21f), Size(80f, 40f), Size(60f, 21f)), 50f))
        // A wide middle row fails on its own corners, whatever the rows around it: (45, 25) is 51.5 out.
        assertFalse(stackFitsInCircle(listOf(Size(10f, 10f), Size(90f, 50f), Size(10f, 10f)), 50f))
        assertTrue(stackFitsInCircle(emptyList(), 0f))
    }
}
