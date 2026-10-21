package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import org.junit.Assert.*
import org.junit.Test

class WorkoutCalculationsTest {
    private fun workout(id: String, time: Long, status: WorkoutStatus = WorkoutStatus.FINISHED) =
        Workout(id, "Workout $id", time, if (status == WorkoutStatus.FINISHED) time + 1000 else null, status)

    private fun exercise(workoutId: String, variation: String = "bench", convention: LoadConvention = LoadConvention.TOTAL_LOAD, suffix: String = "") =
        WorkoutExercise("$workoutId-$variation-$suffix", workoutId, variation, variation,
            MuscleGroup.CHEST, Equipment.BARBELL, convention)

    private fun set(exercise: WorkoutExercise, kg: Double, reps: Int = 10, completed: Boolean = true,
                    warmup: Boolean = false, assistance: Boolean = false, suffix: String = "") =
        WorkoutSet("${exercise.id}-$kg-$reps-$suffix", exercise.id, weightKg = kg, reps = reps,
            completed = completed, warmup = warmup, assistance = assistance)

    private fun history(previous: Double = 100.0, current: Double = 105.0, currentReps: Int = 3): AppData {
        val old = exercise("old")
        val new = exercise("new")
        return AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(old, new), sets = listOf(set(old, previous), set(new, current, currentReps)))
    }

    @Test fun `higher weight beats record despite lower reps and lower volume`() {
        val data = history()
        val pr = WorkoutCalculations.prsForWorkout(data, "new").single()
        assertEquals(100.0, pr.previousWeightKg!!, 0.0)
        assertEquals(105.0, pr.newWeightKg, 0.0)
        assertFalse(pr.firstRecord)
        assertEquals(315.0, WorkoutCalculations.summary(data, "new")!!.volumeKg, 0.0)
    }

    @Test fun `equal weight with more reps is not a weight PR`() {
        assertTrue(WorkoutCalculations.prsForWorkout(history(current = 100.0, currentReps = 20), "new").isEmpty())
    }

    @Test fun `lower weight and higher total volume is not a weight PR`() {
        assertTrue(WorkoutCalculations.prsForWorkout(history(current = 95.0, currentReps = 30), "new").isEmpty())
    }

    @Test fun `historical maximum beats the most recent lower workout`() {
        val data = history()
        val recent = exercise("recent")
        val expanded = data.copy(workouts = data.workouts + workout("recent", 150),
            workoutExercises = data.workoutExercises + recent, sets = data.sets + set(recent, 60.0))
        assertEquals(100.0, WorkoutCalculations.prsForWorkout(expanded, "new").single().previousWeightKg!!, 0.0)
    }

    @Test fun `multiple improving sets and duplicate exercise entries count once`() {
        val data = history(current = 102.5)
        val entry = data.workoutItems("new").single()
        val duplicate = exercise("new", suffix = "again")
        val expanded = data.copy(workoutExercises = data.workoutExercises + duplicate,
            sets = data.sets + set(entry, 105.0, 2) + set(duplicate, 110.0, 1))
        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(1, summary.prs.size)
        assertEquals(110.0, summary.prs.single().newWeightKg, 0.0)
    }

    @Test fun `first positive record is separate from records beaten`() {
        val entry = exercise("first")
        val data = AppData(workouts = listOf(workout("first", 100)), workoutExercises = listOf(entry), sets = listOf(set(entry, 20.0)))
        val summary = WorkoutCalculations.summary(data, "first")!!
        assertTrue(summary.prs.isEmpty())
        assertEquals(1, summary.firstRecords.size)
        assertNull(summary.firstRecords.single().previousWeightKg)
    }

    @Test fun `zero added bodyweight is valid work but not an initial positive record`() {
        val entry = exercise("body", "pullup", LoadConvention.BODYWEIGHT)
        val data = AppData(workouts = listOf(workout("body", 100)), workoutExercises = listOf(entry), sets = listOf(set(entry, 0.0, 12)))
        val summary = WorkoutCalculations.summary(data, "body")!!
        assertEquals(1, summary.completedSetCount)
        assertEquals(0.0, summary.volumeKg, 0.0)
        assertTrue(summary.firstRecords.isEmpty())
    }

    @Test fun `incomplete warmup and assistance values never earn external PRs`() {
        val data = history(current = 90.0)
        val entry = data.workoutItems("new").single()
        val expanded = data.copy(sets = data.sets + set(entry, 120.0, completed = false) +
            set(entry, 130.0, warmup = true) + set(entry, 140.0, assistance = true))
        assertTrue(WorkoutCalculations.prsForWorkout(expanded, "new").isEmpty())
        assertEquals(100.0, WorkoutCalculations.records(expanded).single().weightKg, 0.0)
    }

    @Test fun `assistance convention is excluded even without set assistance flag`() {
        val entry = exercise("assist", "assisted pullup", LoadConvention.ASSISTANCE)
        val data = AppData(workouts = listOf(workout("assist", 100)), workoutExercises = listOf(entry), sets = listOf(set(entry, 80.0)))
        assertTrue(WorkoutCalculations.records(data).isEmpty())
        assertEquals(0.0, WorkoutCalculations.summary(data, "assist")!!.volumeKg, 0.0)
    }

    @Test fun `variations and logging conventions have independent records`() {
        val data = history()
        val variation = exercise("new", "incline bench")
        val convention = exercise("new", "bench", LoadConvention.PER_HAND, "per hand")
        val expanded = data.copy(workoutExercises = data.workoutExercises + variation + convention,
            sets = data.sets + set(variation, 50.0) + set(convention, 30.0))
        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(1, summary.prs.size)
        assertEquals(2, summary.firstRecords.size)
        assertEquals(3, WorkoutCalculations.records(expanded).size)
    }

    @Test fun `kg pound round trip preserves comparison without false PR`() {
        val pounds = WeightUnits.kgToLb(100.0)
        val restored = WeightUnits.lbToKg(pounds)
        assertEquals(100.0, restored, 0.000000001)
        assertTrue(WorkoutCalculations.prsForWorkout(history(current = restored, currentReps = 20), "new").isEmpty())
        val oneBitAbove = Math.nextUp(100.0)
        assertTrue(WorkoutCalculations.prsForWorkout(history(current = oneBitAbove), "new").isEmpty())
    }

    @Test fun `accepted decimal increases smaller than one milligram still earn PRs`() {
        val pr = WorkoutCalculations.prsForWorkout(history(current = 100.0000001), "new").single()
        assertFalse(pr.firstRecord)
        assertEquals(100.0000001, pr.newWeightKg, 0.0)
    }

    @Test fun `volume never silently doubles dumbbells or unilateral loads`() {
        val entry = exercise("one")
        val completed = set(entry, 22.5, 8)
        assertEquals(180.0, WorkoutCalculations.volume(completed, LoadConvention.PER_HAND), 0.0)
        assertEquals(180.0, WorkoutCalculations.volume(completed, LoadConvention.ONE_SIDE), 0.0)
        assertEquals(180.0, WorkoutCalculations.volume(completed, LoadConvention.TOTAL_LOAD), 0.0)
        assertEquals(0.0, WorkoutCalculations.volume(completed.copy(completed = false), LoadConvention.TOTAL_LOAD), 0.0)
    }

    @Test fun `historical edits and deletions recompute subsequent PRs`() {
        val data = history()
        val oldSet = data.exerciseSets(data.workoutItems("old").single().id).single()
        val edited = data.copy(sets = data.sets.map { if (it.id == oldSet.id) it.copy(weightKg = 110.0) else it })
        assertTrue(WorkoutCalculations.prsForWorkout(edited, "new").isEmpty())
        val deleted = data.copy(workouts = data.workouts.filterNot { it.id == "old" })
        assertTrue(WorkoutCalculations.prsForWorkout(deleted, "new").single().firstRecord)
    }

    @Test fun `active workout PR is provisional and cannot change saved records`() {
        val data = history().let { original -> original.copy(workouts = original.workouts.map {
            if (it.id == "new") it.copy(status = WorkoutStatus.ACTIVE, endedAt = null) else it }) }
        assertEquals(105.0, WorkoutCalculations.prsForWorkout(data, "new").single().newWeightKg, 0.0)
        assertEquals(100.0, WorkoutCalculations.records(data).single().weightKg, 0.0)
    }

    @Test fun `invalid values cannot contribute to completed statistics`() {
        val entry = exercise("invalid")
        listOf(set(entry, -1.0), set(entry, Double.NaN), set(entry, Double.POSITIVE_INFINITY), set(entry, 20.0, reps = 0)).forEach {
            assertFalse(WorkoutCalculations.validCompleted(it))
            assertEquals(0.0, WorkoutCalculations.volume(it, LoadConvention.TOTAL_LOAD), 0.0)
        }
    }

    @Test fun `duration includes background elapsed time and finished duration remains fixed`() {
        val data = history()
        assertEquals(1000L, WorkoutCalculations.summary(data, "new", now = 1000000)!!.durationMillis)
        val active = data.copy(workouts = data.workouts.map { if (it.id == "new") it.copy(status = WorkoutStatus.ACTIVE, endedAt = null) else it })
        assertEquals(99800L, WorkoutCalculations.summary(active, "new", now = 100000)!!.durationMillis)
    }

    @Test fun `performance history contains finished completed sets for the exact variation`() {
        val data = history()
        val active = exercise("active", "bench")
        val expanded = data.copy(workouts = data.workouts + workout("active", 300, WorkoutStatus.ACTIVE),
            workoutExercises = data.workoutExercises + active, sets = data.sets + set(active, 200.0))
        val history = WorkoutCalculations.performanceHistory(expanded, "bench")
        assertEquals(listOf("new", "old"), history.map { it.workout.id })
        assertTrue(WorkoutCalculations.performanceHistory(expanded, "other").isEmpty())
    }
}
