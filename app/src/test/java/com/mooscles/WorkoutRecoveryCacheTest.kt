package com.mooscles

import com.mooscles.data.*
import com.mooscles.ui.*
import org.junit.Assert.*
import org.junit.Test

/** Retention is useful only after the saved active rows have been freshly validated. */
class WorkoutRecoveryCacheTest {
    private val movement = Exercise("recovery-bench", "Recovery bench", MuscleGroup.CHEST,
        Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
    private val workout = Workout("recovery-session", "Recovery session", 1000, sourceRoutineId = "routine")
    private val entry = WorkoutExercise("recovery-entry", workout.id, movement.id, movement.name,
        movement.muscle, movement.equipment, movement.convention, position = 0, restSeconds = 123)
    private val set = WorkoutSet("recovery-set", entry.id, position = 0,
        reps = 6, weightKg = 31.125, completed = true)
    private fun snapshot() = ActiveWorkoutSnapshot(workout, listOf(movement), listOf(entry), listOf(set))
    private fun editor(saved: ActiveWorkoutSnapshot) = WorkoutUi(saved.workout.id, saved.workout.name,
        saved.workout.startedAt, sourceRoutineId = saved.workout.sourceRoutineId,
        exercises = saved.entries.map { item -> WorkoutExerciseUi(item.id,
            ExerciseUi(item.exerciseId, item.name, item.muscle.label, item.equipment.label, item.convention.label),
            saved.sets.filter { it.workoutExerciseId == item.id }.map { row ->
                SetUi(row.id, row.reps, row.weightKg, row.completed, row.warmup, row.assistance)
            }, item.restSeconds) })

    @Test fun exactlyValidatedRowsReuseTheSameEssentialEditorWithoutRounding() {
        val cache = WorkoutRecoveryCache()
        val saved = snapshot()
        val ready = editor(saved)
        cache.put(saved, ready)
        assertSame(ready, cache.getValidated(saved.copy(sets = saved.sets.map { it.copy() })))
        val restored = requireNotNull(cache.getValidated(saved))
        assertEquals(31.125, restored.exercises.single().sets.single().weightKg, 0.0)
        assertEquals("routine", restored.sourceRoutineId)
        assertEquals(123, restored.exercises.single().restSeconds)
    }

    @Test fun changedSavedValuesFlagsOrderIdentityAndExerciseMetadataEvictOldEditor() {
        val initial = snapshot()
        val second = set.copy(id = "second-set", position = 1)
        val ordered = initial.copy(sets = listOf(set, second))
        val changed = listOf(
            ordered.copy(workout = workout.copy(id = "replacement")),
            ordered.copy(workout = workout.copy(status = WorkoutStatus.FINISHED, endedAt = 2000)),
            ordered.copy(workout = workout.copy(name = "Renamed elsewhere")),
            ordered.copy(exercises = listOf(movement.copy(equipment = Equipment.DUMBBELL))),
            ordered.copy(entries = listOf(entry.copy(restSeconds = 180))),
            ordered.copy(entries = listOf(entry.copy(exerciseId = "other-variation"))),
            ordered.copy(sets = listOf(set.copy(weightKg = 31.12500000001), second)),
            ordered.copy(sets = listOf(set.copy(reps = 7), second)),
            ordered.copy(sets = listOf(set.copy(completed = false), second)),
            ordered.copy(sets = listOf(set.copy(warmup = true), second)),
            ordered.copy(sets = listOf(set.copy(assistance = true), second)),
            ordered.copy(sets = listOf(set.copy(position = 1), second.copy(position = 0))),
            ordered.copy(sets = listOf(set)),
        )
        changed.forEach { fresh ->
            val cache = WorkoutRecoveryCache()
            cache.put(ordered, editor(ordered))
            assertNull("Fresh saved state must never use an older editor: $fresh", cache.getValidated(fresh))
            assertNull("An invalidated entry must not be resurrected", cache.getValidated(ordered))
        }
    }

    @Test fun onlyOneSessionIsKeptAndClearingAnOlderSessionCannotRemoveItsReplacement() {
        val cache = WorkoutRecoveryCache()
        val first = snapshot()
        cache.put(first, editor(first))
        val replacement = first.copy(workout = workout.copy(id = "next-session"))
        val next = editor(replacement)
        cache.put(replacement, next)
        cache.clear(first.workout.id)
        assertSame(next, cache.getValidated(replacement))
        cache.clear(replacement.workout.id)
        assertNull(cache.getValidated(replacement))
        cache.put(first, editor(first))
        cache.clear()
        assertNull(cache.getValidated(first))
    }
}
