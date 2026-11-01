package com.mooscles.domain

import com.mooscles.data.*
import kotlin.test.*

class WorkoutCalculationIndexTest {
    private class CountedRows(val rows: MutableList<WorkoutSet>) : AbstractList<WorkoutSet>() {
        var reads = 0
        override val size get() = rows.size
        override fun get(index: Int): WorkoutSet = rows[index].also { reads++ }
    }

    @Test fun eachCalculationIndexesSavedRowsOnceInsteadOfFilteringArchiveInsideExerciseLoops() {
        val workouts = (0 until 40).map { index ->
            Workout(id = "workout-$index", name = "Session", startedAt = index * 1000L,
                endedAt = index * 1000L + 500, status = WorkoutStatus.FINISHED)
        }
        val entries = workouts.flatMap { workout ->
            List(4) { position -> WorkoutExercise(id = "${workout.id}-entry-$position",
                workoutId = workout.id, exerciseId = "movement-${position % 2}", name = "Movement",
                muscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL,
                convention = if (position % 2 == 0) LoadConvention.TOTAL_LOAD else LoadConvention.BODYWEIGHT,
                position = position)
            }
        }
        // Reverse storage order deliberately: indexes must retain the old per-instance sort order.
        val counted = CountedRows(entries.flatMap { entry ->
            List(3) { position -> WorkoutSet(id = "${entry.id}-set-$position", workoutExerciseId = entry.id,
                position = position, reps = position + 6,
                weightKg = if (entry.convention == LoadConvention.BODYWEIGHT) 0.0 else position + 20.0,
                completed = position > 0, warmup = position == 1)
            }
        }.reversed().toMutableList())
        val data = AppData(workouts = workouts, workoutExercises = entries.reversed(), sets = counted)
        fun checkSinglePass(calculation: () -> Any?) {
            counted.reads = 0
            calculation()
            assertEquals(counted.size, counted.reads, "Each calculation must visit the raw archive only once")
        }
        val target = workouts.last().id
        checkSinglePass { WorkoutCalculations.prsForWorkout(data, target) }
        checkSinglePass { WorkoutCalculations.repsPrsForWorkout(data, target) }
        checkSinglePass { WorkoutCalculations.records(data) }
        checkSinglePass { WorkoutCalculations.repsRecords(data) }
        checkSinglePass { WorkoutCalculations.performanceHistory(data, "movement-0") }
        checkSinglePass { WorkoutCalculations.performanceHistories(data) }
        checkSinglePass { WorkoutCalculations.historyRecords(data) }
        checkSinglePass { WorkoutCalculations.summary(data, target) }

        val summary = WorkoutCalculations.summary(data, target)!!
        assertEquals(listOf(0, 1, 2, 3), summary.exercises.map { it.exercise.position })
        assertTrue(summary.exercises.all { it.completedSets.map { row -> row.position } == listOf(1, 2) })
        assertEquals(4, summary.completedExerciseCount)
        assertEquals(8, summary.completedSetCount)
        assertEquals(646.0, summary.volumeKg, 0.0)
    }

    @Test fun localIndexNeverRetainsMutableCallerListsAcrossCalls() {
        val workout = Workout(id = "history", name = "Saved", startedAt = 1000,
            endedAt = 2000, status = WorkoutStatus.FINISHED)
        val entry = WorkoutExercise(id = "instance", workoutId = workout.id, exerciseId = "bench",
            name = "Bench", muscle = MuscleGroup.CHEST, equipment = Equipment.BARBELL,
            convention = LoadConvention.TOTAL_LOAD)
        val rows = mutableListOf(WorkoutSet(id = "set", workoutExerciseId = entry.id,
            reps = 7, weightKg = 20.0, completed = true))
        val data = AppData(workouts = listOf(workout), workoutExercises = listOf(entry), sets = rows)
        assertEquals(20.0, WorkoutCalculations.records(data).single().weightKg, 0.0)
        rows[0] = rows[0].copy(weightKg = 22.0, reps = 6)
        assertEquals(22.0, WorkoutCalculations.records(data).single().weightKg, 0.0)
        assertEquals(6, WorkoutCalculations.performanceHistory(data, entry.exerciseId).single().sets.single().reps)
        rows.clear()
        assertTrue(WorkoutCalculations.records(data).isEmpty())
        assertTrue(WorkoutCalculations.performanceHistory(data, entry.exerciseId).isEmpty())
    }
}
