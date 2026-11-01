package com.mooscles.screens.workout

import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi

/**
 * A set still to do, as the expanded timer names it: its id (where the screen hands back to after
 * the rest), its number in its exercise, its load (exact kg) and reps.
 */
internal data class UpNext(val setId: String, val setNumber: Int, val weightKg: Double, val reps: Int) {
    /**
     * What "Up next" names (review M3): never a 0 the set row leaves blank ([blankLoad],
     * [blankReps]). Without reps only the set ("set 2"); with reps but no load, the set and
     * its reps ("set 2 · 12 reps"); otherwise "set 5 · 85 × 6".
     */
    val parts: UpNextParts
        get() = when {
            blankReps(reps) -> UpNextParts.SetOnly
            blankLoad(weightKg) -> UpNextParts.Reps
            else -> UpNextParts.LoadAndReps
        }
}

/** The parts of a set that "Up next" shows ([UpNext.parts]). */
internal enum class UpNextParts { LoadAndReps, Reps, SetOnly }

/**
 * What comes after a rest (spec "Expandido", "A seguir: série 5 · 85 × 6"): the first pending set
 * of the timer's exercise ([of]'s `timerEntryId`, `TimerUi.workoutExerciseId`); when that exercise
 * has none, left the workout or the rest has no exercise (started from the rest sheet), the first
 * pending set of the workout, in its order; with nothing pending, nothing.
 */
internal object RestUpNext {
    fun of(workout: WorkoutUi, timerEntryId: String?): UpNext? {
        val own = timerEntryId?.let { id -> workout.exercises.firstOrNull { it.id == id } }?.let(::firstPending)
        return own ?: workout.exercises.firstNotNullOfOrNull(::firstPending)
    }

    private fun firstPending(entry: WorkoutExerciseUi): UpNext? {
        val index = entry.sets.indexOfFirst { !it.completed }
        if (index < 0) return null
        val set = entry.sets[index]
        return UpNext(set.id, index + 1, set.weightKg, set.reps)
    }
}
