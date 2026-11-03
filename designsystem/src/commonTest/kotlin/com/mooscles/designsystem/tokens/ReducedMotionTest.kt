package com.mooscles.designsystem.tokens

import com.mooscles.designsystem.theme.isReducedMotion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducedMotionTest {
    @Test
    fun zeroAnimatorScaleMeansReduced() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(0.5f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(10f))
    }
}
