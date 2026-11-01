package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.math.nextUp
import kotlin.test.*

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
        assertEquals(1, summary.prExerciseCount)
        assertEquals(0, summary.beatenPrExerciseCount)
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
        val oneBitAbove = 100.0.nextUp()
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

    @Test fun `bench weight and bodyweight pullup reps both earn records in one workout`() {
        val oldBench = exercise("old")
        val newBench = exercise("new")
        val oldPullup = exercise("old", "pullup", LoadConvention.BODYWEIGHT)
        val newPullup = exercise("new", "pullup", LoadConvention.BODYWEIGHT)
        val data = AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(oldBench, newBench, oldPullup, newPullup),
            sets = listOf(set(oldBench, 100.0, 10), set(newBench, 105.0, 3),
                set(oldPullup, 0.0, 8), set(newPullup, 0.0, 12)))

        val summary = WorkoutCalculations.summary(data, "new")!!
        assertEquals(2, summary.prExerciseCount)
        assertEquals("bench", summary.prs.single().exerciseId)
        assertEquals(100.0, summary.prs.single().previousWeightKg!!, 0.0)
        assertEquals(105.0, summary.prs.single().newWeightKg, 0.0)
        val pullupPr = summary.repsPrs.single()
        assertEquals("pullup", pullupPr.exerciseId)
        assertEquals(8, pullupPr.previousReps)
        assertEquals(12, pullupPr.newReps)
        assertEquals(0.0, pullupPr.weightKg, 0.0)
        assertEquals(315.0, summary.volumeKg, 0.0)
        assertEquals(2, WorkoutCalculations.summary(data.copy(), "new")!!.prExerciseCount)
        assertEquals("new", WorkoutCalculations.repsRecords(data).first { it.exerciseId == "pullup" }.workoutId)
    }

    @Test fun `first bench weight and reps plus first wide grip pullup reps count two exercises`() {
        val bench = exercise("first").copy(name = "Barbell bench press")
        val pullup = exercise("first", "wide-grip-pullup", LoadConvention.BODYWEIGHT)
            .copy(name = "Wide-grip pull-up")
        val data = AppData(workouts = listOf(workout("first", 100)),
            workoutExercises = listOf(bench, pullup),
            sets = listOf(set(bench, 20.0, 10, suffix = "1"), set(bench, 20.0, 10, suffix = "2"),
                set(bench, 20.0, 8), set(pullup, 0.0, 10, suffix = "1"), set(pullup, 0.0, 10, suffix = "2")))

        val summary = WorkoutCalculations.summary(data, "first")!!
        assertEquals(2, summary.completedExerciseCount)
        assertEquals(5, summary.completedSetCount)
        assertEquals(560.0, summary.volumeKg, 0.0)
        assertEquals(1, summary.firstRecords.size)
        assertEquals(2, summary.firstRepsRecords.size)
        assertEquals(2, summary.prExerciseCount)
        assertEquals(2, summary.firstRecordExerciseCount)
        assertEquals(0, summary.beatenPrExerciseCount)
        assertTrue(summary.prs.isEmpty())
        assertTrue(summary.repsPrs.isEmpty())
        assertEquals(2, WorkoutCalculations.summary(data.copy(), "first")!!.prExerciseCount)
    }

    @Test fun `first and beaten records merge by exercise convention without dropping independent variations`() {
        val data = history(current = 100.0, currentReps = 11)
        val bench = data.workoutItems("new").single()
        val duplicate = exercise("new", suffix = "again")
        val pullup = exercise("new", "wide-grip-pullup", LoadConvention.BODYWEIGHT)
        val perHand = exercise("new", "bench", LoadConvention.PER_HAND, "per-hand")
        val expanded = data.copy(workoutExercises = data.workoutExercises + duplicate + pullup + perHand,
            sets = data.sets + set(bench, 90.0, 15) + set(duplicate, 110.0, 2) +
                set(pullup, 0.0, 10) + set(perHand, 20.0, 8))

        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(3, summary.prExerciseCount)
        assertEquals(1, summary.beatenPrExerciseCount)
        assertEquals(3, summary.firstRecordExerciseCount)
        assertEquals(1, summary.prs.size)
        assertEquals(1, summary.repsPrs.size)
    }

    @Test fun `one exercise can beat both types and multiple repetition loads while counting once`() {
        val data = history(current = 105.0)
        val old = data.workoutItems("old").single()
        val new = data.workoutItems("new").single()
        val expanded = data.copy(sets = data.sets + set(old, 80.0, 15) +
            set(new, 100.0, 11) + set(new, 80.0, 16))
        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(1, summary.prs.size)
        assertEquals(2, summary.repsPrs.size)
        assertEquals(1, summary.prExerciseCount)
        assertNotNull(summary.exercises.single().pr)
        assertEquals(3, summary.exercises.single().repsPrs.size)
    }

    @Test fun `first bodyweight repetition record stays separate from records beaten`() {
        val entry = exercise("first", "pullup", LoadConvention.BODYWEIGHT)
        val data = AppData(workouts = listOf(workout("first", 100)), workoutExercises = listOf(entry),
            sets = listOf(set(entry, 0.0, 12)))
        val summary = WorkoutCalculations.summary(data, "first")!!
        assertEquals(1, summary.prExerciseCount)
        assertEquals(0, summary.beatenPrExerciseCount)
        assertTrue(summary.prs.isEmpty())
        assertTrue(summary.repsPrs.isEmpty())
        assertTrue(summary.firstRecords.isEmpty())
        assertEquals(1, summary.firstRecordExerciseCount)
        assertNull(summary.firstRepsRecords.single().previousReps)
        assertEquals(12, summary.firstRepsRecords.single().newReps)
    }

    @Test fun `weighted repetitions compare at the same external load only`() {
        val comparable = WorkoutCalculations.repsPrsForWorkout(history(current = 100.0, currentReps = 11), "new").single()
        assertFalse(comparable.firstRecord)
        assertEquals(10, comparable.previousReps)
        assertEquals(11, comparable.newReps)
        assertTrue(WorkoutCalculations.repsPrsForWorkout(history(current = 100.0, currentReps = 10), "new").isEmpty())
        val differentLoad = WorkoutCalculations.repsPrsForWorkout(history(current = 95.0, currentReps = 30), "new").single()
        assertTrue(differentLoad.firstRecord)
        assertNull(differentLoad.previousReps)
        val summary = WorkoutCalculations.summary(history(current = 95.0, currentReps = 30), "new")!!
        assertEquals(1, summary.prExerciseCount)
        assertEquals(0, summary.beatenPrExerciseCount)
    }

    @Test fun `assisted and unassisted repetition records stay separate at equal displayed load`() {
        val old = exercise("old", "pullup", LoadConvention.BODYWEIGHT)
        val new = exercise("new", "pullup", LoadConvention.BODYWEIGHT)
        val data = AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(old, new), sets = listOf(set(old, 0.0, 8),
                set(old, 0.0, 20, assistance = true), set(new, 0.0, 9), set(new, 0.0, 21, assistance = true)))
        val summary = WorkoutCalculations.summary(data, "new")!!
        assertEquals(2, summary.repsPrs.size)
        assertEquals(8, summary.repsPrs.first { !it.assistance }.previousReps)
        assertEquals(20, summary.repsPrs.first { it.assistance }.previousReps)
        assertEquals(1, summary.prExerciseCount)
        assertTrue(summary.prs.isEmpty())
        assertEquals(2, WorkoutCalculations.repsRecords(data).size)

        val withoutAssistedBaseline = data.copy(sets = data.sets.filterNot { it.workoutExerciseId == old.id && it.assistance })
        assertTrue(WorkoutCalculations.repsPrsForWorkout(withoutAssistedBaseline, "new")
            .first { it.assistance }.firstRecord)
    }

    @Test fun `assistance convention repetition records compare the same assistance setting`() {
        val old = exercise("old", "assisted pullup", LoadConvention.ASSISTANCE)
        val new = exercise("new", "assisted pullup", LoadConvention.ASSISTANCE)
        val data = AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(old, new), sets = listOf(set(old, 30.0, 10),
                set(new, 30.0, 12), set(new, 40.0, 20)))
        val summary = WorkoutCalculations.summary(data, "new")!!
        assertTrue(summary.repsPrs.single().assistance)
        assertEquals(10, summary.repsPrs.single().previousReps)
        assertEquals(30.0, summary.repsPrs.single().weightKg, 0.0)
        assertTrue(summary.firstRepsRecords.single().assistance)
        assertEquals(40.0, summary.firstRepsRecords.single().weightKg, 0.0)
        assertTrue(WorkoutCalculations.records(data).isEmpty())
        assertEquals(0.0, summary.volumeKg, 0.0)
    }

    @Test fun `incomplete warmup and invalid sets cannot establish or beat repetition records`() {
        val data = history(current = 100.0, currentReps = 9)
        val old = data.workoutItems("old").single()
        val new = data.workoutItems("new").single()
        val expanded = data.copy(sets = data.sets + set(old, 100.0, 90, warmup = true) +
            set(new, 100.0, 20, warmup = true) + set(new, 100.0, 30, completed = false) +
            set(new, Double.NaN, 40) + set(new, 100.0, 0))
        assertTrue(WorkoutCalculations.repsPrsForWorkout(expanded, "new").isEmpty())
        assertEquals(10, WorkoutCalculations.repsRecords(expanded).single().reps)
        assertEquals(0, WorkoutCalculations.summary(expanded, "new")!!.prExerciseCount)
    }

    @Test fun `repetition load comparison tolerates unit round trips without merging genuine load differences`() {
        val roundTrip = WeightUnits.lbToKg(WeightUnits.kgToLb(100.0)).nextUp()
        val equivalent = WorkoutCalculations.repsPrsForWorkout(history(current = roundTrip, currentReps = 11), "new").single()
        assertFalse(equivalent.firstRecord)
        assertEquals(10, equivalent.previousReps)
        val distinct = WorkoutCalculations.repsPrsForWorkout(history(current = 100.0000001, currentReps = 11), "new").single()
        assertTrue(distinct.firstRecord)
    }

    @Test fun `rep improvements from duplicate entries collapse to one load record`() {
        val data = history(current = 100.0, currentReps = 11)
        val duplicate = exercise("new", suffix = "again")
        val expanded = data.copy(workoutExercises = data.workoutExercises + duplicate,
            sets = data.sets + set(duplicate, 100.0, 14))
        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(14, summary.repsPrs.single().newReps)
        assertEquals(1, summary.prExerciseCount)
        assertEquals(14, WorkoutCalculations.repsRecords(expanded).single().reps)
    }

    @Test fun `repetition records remain independent between variations and load conventions`() {
        val data = history(current = 100.0, currentReps = 11)
        val variation = exercise("new", "incline bench")
        val convention = exercise("new", "bench", LoadConvention.PER_HAND, "per hand")
        val expanded = data.copy(workoutExercises = data.workoutExercises + variation + convention,
            sets = data.sets + set(variation, 100.0, 30) + set(convention, 100.0, 40))
        val summary = WorkoutCalculations.summary(expanded, "new")!!
        assertEquals(1, summary.repsPrs.size)
        assertEquals(2, summary.firstRepsRecords.size)
        assertEquals(3, WorkoutCalculations.repsRecords(expanded).size)
    }

    @Test fun `historical repetition edits and deletions recompute later achievements`() {
        val data = history(current = 100.0, currentReps = 11)
        val oldSet = data.exerciseSets(data.workoutItems("old").single().id).single()
        val edited = data.copy(sets = data.sets.map { if (it.id == oldSet.id) it.copy(reps = 12) else it })
        assertTrue(WorkoutCalculations.summary(edited, "new")!!.repsPrs.isEmpty())
        assertEquals("old", WorkoutCalculations.repsRecords(edited).single().workoutId)
        val deleted = data.copy(workouts = data.workouts.filterNot { it.id == "old" })
        assertTrue(WorkoutCalculations.repsPrsForWorkout(deleted, "new").single().firstRecord)
        val summary = WorkoutCalculations.summary(deleted, "new")!!
        assertEquals(1, summary.prExerciseCount)
        assertEquals(0, summary.beatenPrExerciseCount)
    }

    @Test fun `active repetition achievements stay provisional until finished`() {
        val data = history(current = 100.0, currentReps = 11).let { original ->
            original.copy(workouts = original.workouts.map {
                if (it.id == "new") it.copy(status = WorkoutStatus.ACTIVE, endedAt = null) else it
            })
        }
        assertEquals(11, WorkoutCalculations.repsPrsForWorkout(data, "new").single().newReps)
        assertEquals(10, WorkoutCalculations.repsRecords(data).single().reps)
    }

    @Test fun `blank weight canonical zero supports rep records for any unassisted exercise`() {
        val summary = WorkoutCalculations.summary(history(previous = 0.0, current = 0.0, currentReps = 11), "new")!!
        assertEquals(11, summary.repsPrs.single().newReps)
        assertEquals(10, summary.repsPrs.single().previousReps)
        assertEquals(1, summary.prExerciseCount)
        assertTrue(summary.prs.isEmpty())
        assertTrue(summary.firstRecords.isEmpty())
        assertEquals(0.0, summary.volumeKg, 0.0)
    }

    @Test fun `rep baseline uses the historical maximum rather than the most recent workout`() {
        val data = history(current = 100.0, currentReps = 9)
        val recent = exercise("recent")
        val expanded = data.copy(workouts = data.workouts + workout("recent", 150),
            workoutExercises = data.workoutExercises + recent, sets = data.sets + set(recent, 100.0, 5))
        assertTrue(WorkoutCalculations.summary(expanded, "new")!!.repsPrs.isEmpty())
        assertEquals(10, WorkoutCalculations.repsRecords(expanded).single().reps)
    }

    @Test fun weightPrCarriesTheChosenSetId() {
        val old = exercise("old")
        val new = exercise("new")
        val again = exercise("new", suffix = "again")
        fun row(entry: WorkoutExercise, id: String, position: Int, kg: Double, reps: Int) =
            WorkoutSet(id, entry.id, position = position, reps = reps, weightKg = kg, completed = true)
        val data = AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(old, new, again.copy(position = 1)),
            sets = listOf(row(old, "old-1", 0, 80.0, 6),
                // The heaviest load, tied on reps, keeps the first set in workout order; more reps break a tie.
                row(new, "lighter", 0, 85.0, 6), row(new, "first-tie", 1, 87.5, 6),
                row(new, "fewer-reps", 2, 87.5, 5), row(again, "second-tie", 0, 87.5, 6)))
        val pr = WorkoutCalculations.prsForWorkout(data, "new").single()
        assertEquals("first-tie", pr.setId)
        assertEquals(87.5, pr.newWeightKg, 0.0)
        assertEquals(6, pr.reps)
        assertEquals(listOf(pr), WorkoutCalculations.prsByWorkout(data)["new"])
        val moreReps = data.copy(sets = data.sets + row(again, "more-reps", 1, 87.5, 7))
        assertEquals("more-reps", WorkoutCalculations.prsForWorkout(moreReps, "new").single().setId)
        // A first record carries its set too, so the field never needs a default.
        assertEquals("old-1", WorkoutCalculations.prsForWorkout(data, "old").single().setId)
    }

    @Test fun repsPrCarriesTheChosenSetId() {
        val old = exercise("old")
        val new = exercise("new")
        fun row(entry: WorkoutExercise, id: String, position: Int, kg: Double, reps: Int, assistance: Boolean = false) =
            WorkoutSet(id, entry.id, position = position, reps = reps, weightKg = kg, completed = true, assistance = assistance)
        val data = AppData(workouts = listOf(workout("old", 100), workout("new", 200)),
            workoutExercises = listOf(old, new),
            sets = listOf(row(old, "old-80", 0, 80.0, 6), row(old, "old-assisted", 1, 20.0, 5, assistance = true),
                // Only strictly more reps replace the first set at a load.
                row(new, "first-8", 0, 80.0, 8), row(new, "second-8", 1, 80.0, 8),
                row(new, "assisted-6", 2, 20.0, 6, assistance = true), row(new, "assisted-9", 3, 20.0, 9, assistance = true)))
        val prs = WorkoutCalculations.repsPrsForWorkout(data, "new")
        assertEquals(listOf("first-8" to 6, "assisted-9" to 5), prs.map { it.setId to it.previousReps })
        assertEquals(prs, WorkoutCalculations.repsPrsByWorkout(data)["new"])
    }
}
