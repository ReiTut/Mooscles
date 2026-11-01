package com.mooscles.designsystem.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The four control points of a cubic-bezier curve, kept so tests can read them back. */
data class BezierPoints(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    fun easing(): CubicBezierEasing = CubicBezierEasing(x1, y1, x2, y2)
}

/** A spring: bounce only in the three key moments (set done, record, workout end). */
data class SpringTokens(val dampingRatio: Float, val stiffness: Float)

/** Durations (ms), curves, springs and the scale and offset constants of the motion spec. */
data class MoosclesMotion(
    val instant: Int = 0,
    val press: Int = 120,
    val fast: Int = 150,
    val base: Int = 220,
    val slow: Int = 300,
    val slowExit: Int = 200,
    val easeOutPoints: BezierPoints = BezierPoints(0.23f, 1f, 0.32f, 1f),
    val easeInOutPoints: BezierPoints = BezierPoints(0.77f, 0f, 0.175f, 1f),
    val easeDrawerPoints: BezierPoints = BezierPoints(0.32f, 0.72f, 0f, 1f),
    val springSnappy: SpringTokens = SpringTokens(dampingRatio = 1.0f, stiffness = 700f),
    val springSet: SpringTokens = SpringTokens(dampingRatio = 0.6f, stiffness = 600f),
    val springPr: SpringTokens = SpringTokens(dampingRatio = 0.5f, stiffness = 400f),
    val springFinish: SpringTokens = SpringTokens(dampingRatio = 0.7f, stiffness = 300f),
    val pressScale: Float = 0.96f,
    val setCheckFrom: Float = 0.92f,
    val prFrom: Float = 0.9f,
    val prRise: Dp = 8.dp,
    /** Phase 4B (spec D15): the scale of an item lifted to be reordered (none with reduced motion). */
    val dragLiftScale: Float = 1.02f,
) {
    val easeOut: CubicBezierEasing get() = easeOutPoints.easing()
    val easeInOut: CubicBezierEasing get() = easeInOutPoints.easing()
    val easeDrawer: CubicBezierEasing get() = easeDrawerPoints.easing()
}

/** Durations in ms that are not animation curves: repeat rates, toast and hold times. */
data class MoosclesTiming(
    val stepperRepeatDelay: Long = 400,
    val stepperRepeatInterval: Long = 80,
    val toastInfo: Long = 4_000,
    val toastAction: Long = 6_000,
    val prToast: Long = 4_000,
    val timerDoneHold: Long = 3_000,
    /**
     * How long a live region keeps its text after an announcement (the timer's, and the reordering's
     * "moved to position"); empty after, so it is not a screen-reader stop.
     */
    val timerAnnounceHold: Long = 1_500,
    val skeletonPulse: Long = 1_200,
    val illustrationFrame: Long = 900,
    val hapticDoubleGap: Long = 80,
    /** Phase 3 (D9): after the last pending set of an exercise is checked, the wait before its card collapses and the next opens. */
    val cardAdvance: Long = 300,
    /**
     * Phase 4B (plan Task 4): how long a list keeps showing the order just dropped while it waits for
     * its keys to change (the write's echo). A write that never echoes (it failed) gives the keys back.
     */
    val reorderEchoHold: Long = 2_000,
    /**
     * Phase 4B (plan Task 10): the longest a form stays open and busy for the echo of its save. The echo comes in
     * milliseconds; this only frees a form whose command was lost (a queue that stopped), so it is well past a slow write.
     */
    val saveEchoWait: Long = 15_000,
    /**
     * Phase 4B (Task 19): the longest a saved form stays over the screen that opened it while the next one (the new
     * exercise's detail) comes in. The screen normally leaves first, taking the form with it; this frees a host that
     * does not move on.
     */
    val formHandoff: Long = 1_500,
    /**
     * Phase 5A (plan decision 9): how long after a new history the app waits before it builds the progress index in the
     * background (the prewarm); a newer history within it replaces the pending build.
     */
    val progressPrewarmDelay: Long = 500,
)
