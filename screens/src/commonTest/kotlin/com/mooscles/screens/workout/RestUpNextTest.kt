package com.mooscles.screens.workout

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the expanded timer says comes next (spec "Expandido": "A seguir: série 5 · 85 × 6"). */
class RestUpNextTest {
    private fun entry(id: String, vararg sets: SetUi) =
        WorkoutExerciseUi(id, ExerciseUi("x-$id", "Exercise $id", "Chest", "Barbell"), sets = sets.toList())

    private fun set(id: String, weightKg: Double, reps: Int, done: Boolean) = SetUi(id, reps = reps, weightKg = weightKg, completed = done)

    private val workout = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = 0L,
        exercises = listOf(
            entry("e1", set("e1s1", 60.0, 10, done = true), set("e1s2", 60.0, 8, done = false)),
            entry(
                "e2",
                set("e2s1", 85.0, 6, done = true),
                set("e2s2", 85.0, 6, done = true),
                set("e2s3", 85.0, 6, done = true),
                set("e2s4", 85.0, 6, done = true),
                set("e2s5", 85.0, 6, done = false),
            ),
            entry("e3", set("e3s1", 20.0, 12, done = true)),
        ),
    )

    @Test
    fun nextPendingOfTheTimerExercise() {
        // The timer's exercise comes first, even when an earlier one still has a pending set.
        assertEquals(UpNext(setId = "e2s5", setNumber = 5, weightKg = 85.0, reps = 6), RestUpNext.of(workout, "e2"))
        assertEquals(UpNext(setId = "e1s2", setNumber = 2, weightKg = 60.0, reps = 8), RestUpNext.of(workout, "e1"))
    }

    @Test
    fun fallsBackToTheFirstPendingOfTheWorkout() {
        val first = UpNext(setId = "e1s2", setNumber = 2, weightKg = 60.0, reps = 8)
        // The timer's exercise is done, it left the workout, or the rest came from the rest sheet (no entry).
        assertEquals(first, RestUpNext.of(workout, "e3"))
        assertEquals(first, RestUpNext.of(workout, "gone"))
        assertEquals(first, RestUpNext.of(workout, null))
    }

    @Test
    fun noneWhenEverythingIsDone() {
        val done = workout.copy(exercises = workout.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(completed = true) }) })
        assertNull(RestUpNext.of(done, "e2"))
        assertNull(RestUpNext.of(done, null))
        assertNull(RestUpNext.of(workout.copy(exercises = emptyList()), null))
    }

    /** Review M3: a 0 the set row leaves blank is never named ("série 2 · 0 × 12", "série 2 · 0 × 0"). */
    @Test
    fun blankPartsAreLeftOut() {
        fun parts(weightKg: Double, reps: Int) = UpNext("s", 2, weightKg, reps).parts
        assertEquals(UpNextParts.LoadAndReps, parts(85.0, 6))
        // Added weight without a load: only the reps.
        assertEquals(UpNextParts.Reps, parts(0.0, 12))
        // No reps yet (a set just added, before a suggestion): only the set, with or without a load.
        assertEquals(UpNextParts.SetOnly, parts(0.0, 0))
        assertEquals(UpNextParts.SetOnly, parts(85.0, 0))
    }
}
