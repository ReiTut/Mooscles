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
        var beatenReps = 0
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
                beatenWeight += expectedWeight.size
                beatenReps += expectedReps.size
            }
            repeatedStarts += finished.size - finished.map { it.startedAt }.distinct().size
            if (data.workouts.any { it.status == WorkoutStatus.ACTIVE }) activeHistories++
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue(beatenWeight > 50, "beaten weight records: $beatenWeight")
        assertTrue(beatenReps > 50, "beaten reps records: $beatenReps")
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
        val beaten = mutableListOf<Boolean>()
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
            // Groups this workout starts, and their hardest load, when that first session is a reps record.
            fun key(c: Pair<WorkoutExercise, WorkoutSet>) = Triple(c.first.exerciseId, c.first.convention, assisted(c))
            val firstTop = best.groupBy(::key).filterKeys { k -> result.none { Triple(it.exerciseId, it.convention, it.assistance) == k } }
                .mapValues { (k, sets) -> sets.map { it.second.weightKg }.let { if (k.third) it.min() else it.max() } }
                .filter { (k, top) -> k.third || top == 0.0 }
            best.forEach { candidate ->
                val (exercise, set) = candidate
                val index = result.indexOfFirst { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention &&
                    it.assistance == assisted(candidate) && sameLoad(it.weightKg, set.weightKg) }
                if (index < 0 || set.reps > result[index].reps) {
                    val record = ExerciseRepsRecord(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg,
                        assisted(candidate), set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (index < 0) {
                        result += record
                        beaten += firstTop[key(candidate)]?.let { sameLoad(it, set.weightKg) } == true
                    } else {
                        result[index] = record
                        beaten[index] = true
                    }
                }
            }
        }
        // Per exercise, convention and assistance mode: the slot at the hardest load, once it beat an earlier value.
        val current = result.indices.groupBy { Triple(result[it].exerciseId, result[it].convention, result[it].assistance) }.values
            .mapNotNull { slots ->
                slots.maxBy { if (result[it].assistance) -result[it].weightKg else result[it].weightKg }.takeIf { beaten[it] }
            }.map { result[it] }
        return current.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
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
        // One current reps record per exercise and mode at most, once beaten (about 90 here).
        assertTrue(records > 50, "reps records: $records")
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
        val current = best(data.workoutItems(workoutId))
        // The hardest load of the candidate's exercise, convention and assistance mode, this workout included.
        fun hardest(c: Pair<WorkoutExercise, WorkoutSet>) = (baseline + current).filter {
            it.first.exerciseId == c.first.exerciseId && it.first.convention == c.first.convention && assisted(it) == assisted(c)
        }.map { it.second.weightKg }.let { if (assisted(c)) it.min() else it.max() }
        return current.mapNotNull { candidate ->
            val (exercise, set) = candidate
            if (!sameLoad(set.weightKg, hardest(candidate))) return@mapNotNull null
            val previous = baseline.firstOrNull { comparable(it, candidate) }?.second?.reps
            val groupSeen = baseline.any {
                it.first.exerciseId == exercise.exerciseId && it.first.convention == exercise.convention && assisted(it) == assisted(candidate)
            }
            // A group's first session counts only when it gives no load record: assisted, or no added load.
            if (previous == null && (groupSeen || !(assisted(candidate) || hardest(candidate) == 0.0))) return@mapNotNull null
            if (previous != null && set.reps <= previous) return@mapNotNull null
            RepsPr(exercise.exerciseId, exercise.name, exercise.convention, set.weightKg, assisted(candidate),
                previous, set.reps, set.id)
        }
    }

    @Test fun repsPrsForWorkoutMatchesFlatScan() {
        val random = Random(2027)
        var compared = 0
        var activeTargets = 0
        var beaten = 0
        repeat(200) { history ->
            val data = randomHistory(random, exerciseCount = if (history % 2 == 0) null else 12)
            data.workouts.forEach { workout ->
                val expected = linearRepsPrsForWorkout(data, workout.id)
                val label = "history $history workout ${workout.id}"
                assertEquals(expected, WorkoutCalculations.repsPrsForWorkout(data, workout.id), "$label reps")
                val summary = WorkoutCalculations.summary(data, workout.id, now = 0L)!!
                assertEquals(expected, summary.repsPrs, "$label summary")
                summary.exercises.forEach { exercise ->
                    assertEquals(expected.filter {
                        it.exerciseId == exercise.exercise.exerciseId && it.convention == exercise.exercise.convention
                    }, exercise.repsPrs, "$label summary ${exercise.exercise.id}")
                }
                compared++
                if (workout.status == WorkoutStatus.ACTIVE) activeTargets++
                beaten += expected.size
            }
        }
        // The generator must exercise the interesting paths, or the equality above proves nothing.
        assertTrue(compared > 1000, "compared workouts: $compared")
        assertTrue(activeTargets > 30, "active targets: $activeTargets")
        // Only the heaviest load gives reps records (about 100 of these histories' records), so the floor is lower than before.
        assertTrue(beaten > 50, "beaten reps records: $beaten")
    }

    @Test fun batchIgnoresActiveWorkoutsAsBaseline() {
        val before = Workout("before", "Before", 50, 60, WorkoutStatus.FINISHED)
        val active = Workout("active", "Active", 100, null, WorkoutStatus.ACTIVE)
        val done = Workout("done", "Done", 200, 300, WorkoutStatus.FINISHED)
        fun entry(workout: Workout, exercise: String, name: String) = WorkoutExercise("${workout.id}-$exercise", workout.id,
            exercise, name, MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val entries = listOf(before, active, done).flatMap { listOf(entry(it, "bench", "Bench"), entry(it, "row", "Row")) }
        fun set(id: String, workout: Workout, exercise: String, kg: Double, reps: Int) =
            WorkoutSet(id, "${workout.id}-$exercise", reps = reps, weightKg = kg, completed = true)
        val data = AppData(workouts = listOf(before, active, done), workoutExercises = entries,
            sets = listOf(
                set("b-bench", before, "bench", 60.0, 3), set("b-row", before, "row", 40.0, 5),
                // Heavier and more reps: a leak into either baseline would hide the finished workout's records.
                set("a-bench", active, "bench", 70.0, 1), set("a-row", active, "row", 40.0, 20),
                set("d-bench", done, "bench", 65.0, 2), set("d-row", done, "row", 40.0, 8)))

        val weight = WorkoutCalculations.prsByWorkout(data)
        val reps = WorkoutCalculations.repsPrsByWorkout(data)

        assertEquals(setOf("before", "done"), weight.keys)
        assertEquals(setOf("before", "done"), reps.keys)
        assertEquals(listOf(WeightPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 60.0, 65.0, 2, "d-bench")), weight["done"])
        assertEquals(listOf(RepsPr("row", "Row", LoadConvention.TOTAL_LOAD, 40.0, false, 5, 8, "d-row")), reps["done"])
        assertEquals(WorkoutCalculations.prsForWorkout(data, "done"), weight["done"])
        assertEquals(WorkoutCalculations.repsPrsForWorkout(data, "done"), reps["done"])
    }
}
