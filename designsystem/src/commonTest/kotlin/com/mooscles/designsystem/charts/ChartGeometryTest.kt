package com.mooscles.designsystem.charts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phase 5A (plan Task 1): picking the touched point and placing values in the drawing area. */
class ChartGeometryTest {
    @Test
    fun nearestPicksTheClosestX() {
        val xs = listOf(10f, 40f, 100f, 101f)
        assertEquals(0, ChartGeometry.nearest(xs, 0f))
        assertEquals(1, ChartGeometry.nearest(xs, 50f))
        assertEquals(2, ChartGeometry.nearest(xs, 99f))
        assertEquals(3, ChartGeometry.nearest(xs, 500f))
        // Halfway between two points: the left one.
        assertEquals(0, ChartGeometry.nearest(xs, 25f))
        assertEquals(2, ChartGeometry.nearest(xs, 100.5f))
        assertEquals(0, ChartGeometry.nearest(listOf(30f), -1_000f))
    }

    /** Critique of phase 5A (carry Task 1, Minor 4): dense records stay apart instead of piling into a band. */
    @Test
    fun ringsThatWouldTouchAreSkippedKeepingTheNewest() {
        // Records every 4 px on a flat line, rings 14 px apart at least: the newest (index 9) and every fourth before it.
        val xs = List(10) { it * 4f }
        val ys = List(10) { 50f }
        assertEquals(listOf(1, 5, 9), ChartGeometry.ringIndices(xs, ys, List(10) { true }, 14f))
        // Far apart, every record keeps its ring; a point that is not a record never gets one.
        val wide = List(4) { it * 100f }
        assertEquals(listOf(0, 2, 3), ChartGeometry.ringIndices(wide, List(4) { 50f }, listOf(true, false, true, true), 14f))
        // Close in x but far in y (a jump) is far enough.
        assertEquals(listOf(0, 1), ChartGeometry.ringIndices(listOf(0f, 2f), listOf(0f, 40f), listOf(true, true), 14f))
        assertEquals(emptyList(), ChartGeometry.ringIndices(emptyList(), emptyList(), emptyList(), 14f))
    }

    @Test
    fun nearestOfNothingIsNull() {
        assertNull(ChartGeometry.nearest(emptyList(), 12f))
    }

    @Test
    fun valuesMapInsideTheDrawingArea() {
        val range = ChartRange(min = 70.0, max = 100.0, ticks = listOf(70.0, 80.0, 90.0, 100.0))
        val top = 12f
        val bottom = 212f
        assertEquals(bottom, ChartGeometry.toPx(70.0, range, top, bottom))
        assertEquals(top, ChartGeometry.toPx(100.0, range, top, bottom))
        assertEquals(112f, ChartGeometry.toPx(85.0, range, top, bottom))
        // Larger values are higher up (smaller y), and every value of the range stays between top and bottom.
        var previous = Float.MAX_VALUE
        for (value in listOf(70.0, 72.5, 80.0, 92.5, 99.9, 100.0)) {
            val y = ChartGeometry.toPx(value, range, top, bottom)
            assertTrue(y in top..bottom, "$value at $y")
            assertTrue(y < previous || value == 70.0, "$value is not above the smaller one")
            previous = y
        }
    }
}
