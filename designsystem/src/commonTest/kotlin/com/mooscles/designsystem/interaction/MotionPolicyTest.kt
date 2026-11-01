package com.mooscles.designsystem.interaction

import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.tokens.MoosclesMotion
import kotlin.test.Test
import kotlin.test.assertEquals

class MotionPolicyTest {
    private val motion = MoosclesMotion()

    @Test
    fun keyMomentsUseTheirSprings() {
        assertEquals(
            MotionChoice.Spring(motion.springSet, 0.92f, 0.dp),
            MotionPolicy.keyMoment(KeyMoment.SetCompleted, reduced = false),
        )
        assertEquals(
            MotionChoice.Spring(motion.springPr, 0.9f, 8.dp),
            MotionPolicy.keyMoment(KeyMoment.PersonalRecord, reduced = false),
        )
        val finish = MotionPolicy.keyMoment(KeyMoment.WorkoutFinished, reduced = false) as MotionChoice.Spring
        assertEquals(motion.springFinish, finish.tokens)
    }

    @Test
    fun reducedMotionTurnsEverythingIntoAFastFade() {
        for (moment in KeyMoment.entries) {
            assertEquals(MotionChoice.Fade(motion.fast), MotionPolicy.keyMoment(moment, reduced = true), moment.name)
        }
        assertEquals(MotionChoice.Fade(motion.fast), MotionPolicy.enter(reduced = true, durationMs = motion.slow))
        assertEquals(MotionChoice.Fade(motion.slow), MotionPolicy.enter(reduced = false, durationMs = motion.slow))
    }

    @Test
    fun disabledNeverScalesOnPress() {
        assertEquals(1f, MotionPolicy.pressScale(pressed = true, enabled = false, reduced = false))
        assertEquals(0.96f, MotionPolicy.pressScale(pressed = true, enabled = true, reduced = false))
        assertEquals(1f, MotionPolicy.pressScale(pressed = true, enabled = true, reduced = true))
        assertEquals(1f, MotionPolicy.pressScale(pressed = false, enabled = true, reduced = false))
    }
}
