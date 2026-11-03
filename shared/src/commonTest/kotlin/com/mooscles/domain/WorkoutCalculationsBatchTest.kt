package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.math.ulp
import kotlin.random.Random
import kotlin.test.*

/** Its two timing limits run on the JVM only, in WorkoutCalculationsBatchTimingTest (androidHostTest). */
class WorkoutCalculationsBatchTest {
    internal val conventions = listOf(LoadConvention.TOTAL_LOAD, LoadConvention.BODYWEIGHT, LoadConvention.ASSISTANCE)

    /** Nominal loads are 0.25 kg multiples; noise stays within ±1 ulp so equal loads remain one cluster. */
    private fun noisyLoad(random: Random): Double {
        val nominal = random.nextInt(0, 13) * 0.25 + if (random.nextInt(4) == 0) 100.0 else 0.0
        return when (random.nextInt(4)) {
            0 -> nominal.nextUp()
            1 -> nominal.nextDown()
            else -> nominal
        }
    }

    private fun randomHistory(random: Random, exerciseCount: Int? = null): AppData {
        val exerciseIds = List(exerciseCount ?: random.nextInt(1, 5)) { "movement-$it" }
        val workoutCount = random.nextInt(1, 41)
        // Ids are shuffled so the id tie-breaker disagrees with creation order.
        val idNumbers = (0 until workoutCount).shuffled(random)
        val activeIndex = if (random.nextInt(3) == 0) random.nextInt(workoutCount) else -1
        val workouts = List(workoutCount) { index ->
            // Few distinct start times, so startedAt repeats often.
            val startedAt = random.nextInt(0, maxOf(2, workoutCount / 3)) * 1000L
            if (index == activeIndex) {
                Workout("w-${idNumbers[index]}", "Active", startedAt, null, WorkoutStatus.ACTIVE)
            } else {
                val endedAt = when (random.nextInt(3)) {
                    0 -> null
                    1 -> startedAt + 500
                    else -> startedAt + random.nextLong(1, 3) * 500
                }
                Workout("w-${idNumbers[index]}", "Workout $index", startedAt, endedAt, WorkoutStatus.FINISHED)
            }
        }
        val entries = mutableListOf<WorkoutExercise>()
        val sets = mutableListOf<WorkoutSet>()
        workouts.forEach { workout ->
            repeat(random.nextInt(0, 5)) { position ->
                val exerciseId = exerciseIds.random(random)
                val entry = WorkoutExercise("${workout.id}-e$position", workout.id, exerciseId,
                    "$exerciseId as named in ${workout.id}", MuscleGroup.CHEST, Equipment.BARBELL,
                    conventions.random(random), position = random.nextInt(0, 4))
                entries += entry
                repeat(random.nextInt(0, 6)) { setIndex ->
                    val roll = random.nextInt(10)
                    sets += WorkoutSet("${entry.id}-s$setIndex", entry.id, position = random.nextInt(0, 4),
                        reps = if (roll == 0) 0 else random.nextInt(1, 9),
                        weightKg = when {
                            roll == 1 -> 0.0
                            roll == 2 && random.nextInt(10) == 0 -> Double.POSITIVE_INFINITY
                            else -> noisyLoad(random)
                        },
                        completed = random.nextInt(6) != 0,
                        warmup = random.nextInt(6) == 0,
                        assistance = random.nextInt(6) == 0)
                }
            }
        }
        // Storage order is arbitrary, like Room's position-ordered rows across workouts.
        return AppData(workouts = workouts.shuffled(random), workoutExercises = entries.shuffled(random),
            sets = sets.shuffled(random))
    }

    @Test fun batchMatchesPerWorkoutOnRandomHistories() {
        val random = Random(42)
        var beatenWeight = 0
        var firstWeight = 0
        var beatenReps = 0
        var firstReps = 0
        var repeatedStarts = 0
        var activeHistories = 0
        repeat(200) { history ->
            val data = randomHistory(random)
            val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
            val weight = WorkoutCalculations.prsByWorkout(data)
            val reps = WorkoutCalculations.repsPrsByWorkout(data)
            assertEquals(HistoryRecords(weight, reps), WorkoutCalculations.historyRecords(data), "history $history shared pass")
            assertEquals(finished.map { it.id }.toSet(), weight.keys, "history $history weight keys")
            assertEquals(finished.map { it.id }.toSet(), reps.keys, "history $history reps keys")
            finished.forEach { workout ->
                val expectedWeight = WorkoutCalculations.prsForWorkout(data, workout.id)
                val expectedReps = WorkoutCalculations.repsPrsForWorkout(data, workout.id)
                assertEquals(expectedWeight, weight[workout.id], "history $history workout ${workout.id} weight")
                assertEquals(expectedReps, reps[workout.id], "history $history workout ${workout.id} reps")
                beatenWeight += expectedWeight.count { !it.firstRecord }
                firstWeight += expectedWeight.count { it.firstRecord }
                beatenReps += expectedReps.count { !it.firstRecord }
                firstReps += expectedReps.count { it.firstRecord }
            }
            repeatedStarts += finished.size - finished.map { it.startedAt }.distinct().size
            if (data.workouts.any { it.status == WorkoutStatus.ACTIVE }) activeHistories++
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue(beatenWeight > 50, "beaten weight records: $beatenWeight")
        assertTrue(firstWeight > 50, "first weight records: $firstWeight")
        assertTrue(beatenReps > 50, "beaten reps records: $beatenReps")
        assertTrue(firstReps > 50, "first reps records: $firstReps")
        assertTrue(repeatedStarts > 50, "repeated startedAt: $repeatedStarts")
        assertTrue(activeHistories > 10, "histories with an active workout: $activeHistories")
    }

    /** Each record names one completed set of its own workout, exercise and convention, with the record's values. */
    @Test fun batchAndPerWorkoutRecordsAgreeOnSetIds() {
        val random = Random(5150)
        var weightIds = 0
        var repsIds = 0
        repeat(200) { history ->
            val data = randomHistory(random)
            val entries = data.workoutExercises.associateBy { it.id }
            val sets = data.sets.associateBy { it.id }
            val batch = WorkoutCalculations.historyRecords(data)
            data.workouts.filter { it.status == WorkoutStatus.FINISHED }.forEach { workout ->
                val label = "history $history workout ${workout.id}"
                val weight = WorkoutCalculations.prsForWorkout(data, workout.id)
                val reps = WorkoutCalculations.repsPrsForWorkout(data, workout.id)
                assertEquals(weight.map { it.setId }, batch.weight.getValue(workout.id).map { it.setId }, "$label weight ids")
                assertEquals(reps.map { it.setId }, batch.reps.getValue(workout.id).map { it.setId }, "$label reps ids")
                weight.forEach { pr ->
                    val set = sets.getValue(pr.setId)
                    val entry = entries.getValue(set.workoutExerciseId)
                    assertEquals(Triple(workout.id, pr.exerciseId, pr.convention), Triple(entry.workoutId, entry.exerciseId, entry.convention), label)
                    assertEquals(pr.newWeightKg to pr.reps, set.weightKg to set.reps, label)
                    assertTrue(set.completed && !set.warmup && !set.assistance, label)
                    weightIds++
                }
                reps.forEach { pr ->
                    val set = sets.getValue(pr.setId)
                    val entry = entries.getValue(set.workoutExerciseId)
                    assertEquals(Triple(workout.id, pr.exerciseId, pr.convention), Triple(entry.workoutId, entry.exerciseId, entry.convention), label)
                    assertEquals(pr.weightKg to pr.newReps, set.weightKg to set.reps, label)
                    assertTrue(set.completed && !set.warmup, label)
                    repsIds++
                }
            }
        }
        assertTrue(weightIds > 100, "weight records checked: $weightIds")
        assertTrue(repsIds > 100, "reps records checked: $repsIds")
    }

    @Test fun performanceHistoriesMatchPerExerciseHistories() {
        val random = Random(7)
        var compared = 0
        var varied = 0
        repeat(60) { history ->
            val data = randomHistory(random, exerciseCount = 40)
            val all = WorkoutCalculations.performanceHistories(data)
            val keys = data.workoutExercises.map { it.exerciseId to it.convention }.distinct()
            assertTrue(keys.containsAll(all.keys), "history $history keys")
            keys.forEach { (exerciseId, convention) ->
                val expected = WorkoutCalculations.performanceHistory(data, exerciseId, convention)
                assertEquals(expected, all[exerciseId to convention].orEmpty(), "history $history $exerciseId $convention")
                if (expected.size > 1) compared++
            }
            if (all.keys.map { it.first }.distinct().size >= 30) varied++
        }
        assertTrue(compared > 100, "keys with several performances: $compared")
        assertTrue(varied >= 5, "histories with at least 30 distinct exercises: $varied")
    }

    /** The linear scan [WorkoutCalculations.repsRecords] used before its per-group slots, kept as an oracle. */
    private fun linearRepsRecords(data: AppData): List<ExerciseRepsRecord> {
        fun sameLoad(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(a.ulp, b.ulp) * 4
        val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
        val setsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        val result = mutableListOf<ExerciseRepsRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            val best = mutableListOf<Pair<WorkoutExercise, WorkoutSet>>()
            fun assisted(c: Pair<WorkoutExercise, WorkoutSet>) = c.second.assistance || c.first.convention == LoadConvention.ASSISTANCE
            data.workoutItems(workout.id).forEach { exercise ->
                setsByEntry[exercise.id].orEmpty().filter { WorkoutCalculations.validCompleted(it) && !it.warmup }.forEach { set ->
                    val candidate = exercise to set
                    val index = best.indexOfFirst { it.first.exerciseId == exercise.exerciseId && it.first.convention == exercise.convention &&
                        assisted(it) == assisted(candidate) && sameLoad(it.second.weightKg, set.weightKg) }
                    if (index < 0) best += candidate else if (set.reps > best[index].second.reps) best[index] = candidate
                }
            }
            best.forEach { candidate ->
                val (exercise, set) = candidate
                val index = result.indexOfFirst { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention &&
                    it.assistance == assisted(candidate) && sameLoad(it.weightKg, set.weightKg) }
                if (index < 0 || set.reps > result[index].reps) {
                    val record = ExerciseRepsRecord(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg,
                        assisted(candidate), set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (index < 0) result += record else result[index] = record
                }
            }
        }
        return result.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
            .thenBy { it.assistance }.thenBy { it.weightKg })
    }

    @Test fun repsRecordsMatchLinearScan() {
        val random = Random(99)
        var records = 0
        repeat(200) { history ->
            val data = randomHistory(random, exerciseCount = if (history % 2 == 0) null else 12)
            val expected = linearRepsRecords(data)
            assertEquals(expected, WorkoutCalculations.repsRecords(data), "history $history")
            records += expected.size
        }
        assertTrue(records > 500, "reps records: $records")
    }

    /**
     * The flat scan [WorkoutCalculations.repsPrsForWorkout] used before its per-group baseline, kept as an oracle:
     * one slot list for every exercise, convention, assistance mode and load, searched from the front.
     */
    internal fun linearRepsPrsForWorkout(data: AppData, workoutId: String): List<RepsPr> {
        fun sameLoad(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(a.ulp, b.ulp) * 4
        fun assisted(c: Pair<WorkoutExercise, WorkoutSet>) = c.second.assistance || c.first.convention == LoadConvention.ASSISTANCE
        fun comparable(a: Pair<WorkoutExercise, WorkoutSet>, b: Pair<WorkoutExercise, WorkoutSet>) =
            a.first.exerciseId == b.first.exerciseId && a.first.convention == b.first.convention &&
                assisted(a) == assisted(b) && sameLoad(a.second.weightKg, b.second.weightKg)
        val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
        val setsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        fun best(entries: List<WorkoutExercise>): List<Pair<WorkoutExercise, WorkoutSet>> {
            val best = mutableListOf<Pair<WorkoutExercise, WorkoutSet>>()
            entries.forEach { exercise ->
                setsByEntry[exercise.id].orEmpty().filter { WorkoutCalculations.validCompleted(it) && !it.warmup }.forEach { set ->
                    val candidate = exercise to set
                    val index = best.indexOfFirst { comparable(it, candidate) }
                    if (index < 0) best += candidate else if (set.reps > best[index].second.reps) best[index] = candidate
                }
            }
            return best
        }
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = data.workouts.filter {
            it.status == WorkoutStatus.FINISHED && it.id != target.id && chronology.compare(it, target) < 0
        }.map { it.id }.toSet()
        val baseline = best(data.workoutExercises.filter { it.workoutId in earlier })
        return best(data.workoutItems(workoutId)).mapNotNull { candidate ->
            val (exercise, set) = candidate
            val previous = baseline.firstOrNull { comparable(it, candidate) }?.second?.reps
            if (previous != null && set.reps <= previous) return@mapNotNull null
            RepsPr(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg, assisted(candidate),
                previous, set.reps, previous == null, set.id)
        }
    }

    @Test fun repsPrsForWorkoutMatchesFlatScan() {
        val random = Random(2027)
        var compared = 0
        var activeTargets = 0
        var beaten = 0
        var first = 0
        repeat(200) { history ->
            val data = randomHistory(random, exerciseCount = if (history % 2 == 0) null else 12)
            data.workouts.forEach { workout ->
                val expected = linearRepsPrsForWorkout(data, workout.id)
                val label = "history $history workout ${workout.id}"
                assertEquals(expected, WorkoutCalculations.repsPrsForWorkout(data, workout.id), "$label reps")
                val summary = WorkoutCalculations.summary(data, workout.id, now = 0L)!!
                assertEquals(expected.filterNot { it.firstRecord }, summary.repsPrs, "$label summary beaten")
                assertEquals(expected.filter { it.firstRecord }, summary.firstRepsRecords, "$label summary first")
                summary.exercises.forEach { exercise ->
                    assertEquals(expected.filter {
                        it.exerciseId == exercise.exercise.exerciseId && it.convention == exercise.exercise.convention
                    }, exercise.repsPrs, "$label summary ${exercise.exercise.id}")
                }
                compared++
                if (workout.status == WorkoutStatus.ACTIVE) activeTargets++
                beaten += expected.count { !it.firstRecord }
                first += expected.count { it.firstRecord }
            }
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue(compared > 1000, "compared workouts: $compared")
        assertTrue(activeTargets > 30, "active targets: $activeTargets")
        assertTrue(beaten > 200, "beaten reps records: $beaten")
        assertTrue(first > 200, "first reps records: $first")
    }

    @Test fun batchIgnoresActiveWorkoutsAsBaseline() {
        val active = Workout("active", "Active", 100, null, WorkoutStatus.ACTIVE)
        val done = Workout("done", "Done", 200, 300, WorkoutStatus.FINISHED)
        val activeEntry = WorkoutExercise("active-bench", active.id, "bench", "Bench",
            MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val doneEntry = activeEntry.copy(id = "done-bench", workoutId = done.id)
        val data = AppData(workouts = listOf(active, done), workoutExercises = listOf(activeEntry, doneEntry),
            sets = listOf(
                // Same load with more reps: a leak into either baseline would hide the finished first records.
                WorkoutSet("a", activeEntry.id, reps = 12, weightKg = 60.0, completed = true),
                WorkoutSet("d", doneEntry.id, reps = 5, weightKg = 60.0, completed = true)))

        val weight = WorkoutCalculations.prsByWorkout(data)
        val reps = WorkoutCalculations.repsPrsByWorkout(data)

        assertEquals(setOf("done"), weight.keys)
        assertEquals(setOf("done"), reps.keys)
        assertEquals(listOf(WeightPr("bench", "Bench", LoadConvention.TOTAL_LOAD, null, 60.0, 5, true, "d")), weight["done"])
        assertEquals(listOf(RepsPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 60.0, false, null, 5, true, "d")), reps["done"])
        assertEquals(WorkoutCalculations.prsForWorkout(data, "done"), weight["done"])
        assertEquals(WorkoutCalculations.repsPrsForWorkout(data, "done"), reps["done"])
    }
}
