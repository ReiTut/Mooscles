package com.mooscles.screens.workout

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ExercisePickerTest {
    private fun exercise(id: String, name: String = id, muscle: String = "Chest", equipment: String = "Barbell") =
        ExerciseUi(id, name, muscle, equipment)

    private fun workout(id: String, startedAt: Long, vararg exercises: ExerciseUi) =
        WorkoutUi(id, "Workout $id", startedAt, endedAt = startedAt + 1, exercises = exercises.mapIndexed { i, e -> WorkoutExerciseUi("$id-$i", e) })

    @Test
    fun recentsAreTheLastWorkoutsExercisesUpToEightWithoutRepeats() {
        val (a, b, c, d, e) = listOf("a", "b", "c", "d", "e").map { exercise(it) }
        val (f, g, h, i, j) = listOf("f", "g", "h", "i", "j").map { exercise(it) }
        // Newest first, whatever order the list comes in; in a workout, in the workout's order.
        val history = listOf(
            workout("w1", startedAt = 100, a, b),
            workout("w3", startedAt = 300, c, a, d),
            workout("w2", startedAt = 200, e, c, f, g, h, i, j),
        )
        assertEquals(listOf("c", "a", "d", "e", "f", "g", "h", "i"), ExercisePicker.recents(history).map { it.id })
        assertEquals(listOf("c", "a"), ExercisePicker.recents(history, limit = 2).map { it.id })
        // The same exercise twice in one workout counts once.
        assertEquals(listOf("b"), ExercisePicker.recents(listOf(workout("w", 1, b, b))).map { it.id })
        assertEquals(emptyList(), ExercisePicker.recents(emptyList()))
    }
}
