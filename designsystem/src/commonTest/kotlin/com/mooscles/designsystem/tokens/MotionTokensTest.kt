package com.mooscles.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTokensTest {
    private val motion = MoosclesMotion()

    @Test
    fun valuesMatchTheSpec() {
        // Spec "Movimento": durations in ms.
        assertEquals(listOf(0, 120, 150, 220, 300, 200), listOf(motion.instant, motion.press, motion.fast, motion.base, motion.slow, motion.slowExit))
        // The four points of each curve.
        assertEquals(BezierPoints(0.23f, 1f, 0.32f, 1f), motion.easeOutPoints)
        assertEquals(BezierPoints(0.77f, 0f, 0.175f, 1f), motion.easeInOutPoints)
        assertEquals(BezierPoints(0.32f, 0.72f, 0f, 1f), motion.easeDrawerPoints)
        assertEquals(CubicBezierEasing(0.23f, 1f, 0.32f, 1f).transform(0.3f), motion.easeOut.transform(0.3f))
        assertEquals(CubicBezierEasing(0.32f, 0.72f, 0f, 1f).transform(0.6f), motion.easeDrawer.transform(0.6f))
        // The four springs.
        assertEquals(SpringTokens(1.0f, 700f), motion.springSnappy)
        assertEquals(SpringTokens(0.6f, 600f), motion.springSet)
        assertEquals(SpringTokens(0.5f, 400f), motion.springPr)
        assertEquals(SpringTokens(0.7f, 300f), motion.springFinish)
        assertEquals(0.96f, motion.pressScale)
        assertEquals(0.92f, motion.setCheckFrom)
        assertEquals(0.9f, motion.prFrom)
        assertEquals(8.dp, motion.prRise)
    }

    /** Phase 4B (spec D15): a lifted item grows to 102%. */
    @Test
    fun dragLiftScale() {
        assertEquals(1.02f, motion.dragLiftScale)
    }

    @Test
    fun exitIsShorterThanEntry() {
        assertTrue(motion.slowExit < motion.slow)
    }

    @Test
    fun onlyKeyMomentSpringsBounce() {
        val bouncing = mapOf(
            "snappy" to motion.springSnappy,
            "set" to motion.springSet,
            "pr" to motion.springPr,
            "finish" to motion.springFinish,
        ).filterValues { it.dampingRatio < 1f }.keys
        assertEquals(setOf("set", "pr", "finish"), bouncing)
    }
}
