package com.mooscles.designsystem.interaction

import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.SpringTokens

/** The three moments allowed to bounce (spec "Movimento"). */
enum class KeyMoment { SetCompleted, PersonalRecord, WorkoutFinished }

/** How something should move: a spring from [fromScale] and [riseDp] below, or a plain fade. */
sealed interface MotionChoice {
    data class Spring(val tokens: SpringTokens, val fromScale: Float, val riseDp: Dp) : MotionChoice
    data class Fade(val durationMs: Int) : MotionChoice
}

/** Turns the motion tokens and the reduced-motion setting into a decision. Pure, so it is testable. */
object MotionPolicy {
    private val motion = MoosclesMotion()

    /** Springs for the key moments; with [reduced], a fade of `motion.fast`. */
    fun keyMoment(moment: KeyMoment, reduced: Boolean): MotionChoice {
        if (reduced) return MotionChoice.Fade(motion.fast)
        return when (moment) {
            KeyMoment.SetCompleted -> MotionChoice.Spring(motion.springSet, motion.setCheckFrom, Dp(0f))
            KeyMoment.PersonalRecord -> MotionChoice.Spring(motion.springPr, motion.prFrom, motion.prRise)
            // The spec gives no start scale for the summary card; it grows from the check's.
            KeyMoment.WorkoutFinished -> MotionChoice.Spring(motion.springFinish, motion.setCheckFrom, Dp(0f))
        }
    }

    /** An entrance: a fade of [durationMs], or of `motion.fast` with [reduced]. */
    fun enter(reduced: Boolean, durationMs: Int): MotionChoice =
        MotionChoice.Fade(if (reduced) motion.fast else durationMs)

    /** The scale of a pressed control: disabled and reduced motion never scale. */
    fun pressScale(pressed: Boolean, enabled: Boolean, reduced: Boolean): Float =
        if (pressed && enabled && !reduced) motion.pressScale else 1f
}
