package com.mooscles.domain

import com.mooscles.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HistoricalPrAnalysisTest {
    private fun workout(id: String, start: Long, finished: Boolean = true) = Workout(id, id, start,
        if (finished) start + 1 else null, if (finished) WorkoutStatus.FINISHED else WorkoutStatus.ACTIVE)
    private fun entry(id: String, workout: String, variation: String = "bench",
        convention: LoadConvention = LoadConvention.TOTAL_LOAD, position: Int = 0) =
        WorkoutExercise(id, workout, variation, "Name $id", MuscleGroup.CHEST, Equipment.BARBELL,
            convention, position)
    private fun set(id: String, entry: String, kg: Double, reps: Int = 8, position: Int = 0) =
        WorkoutSet(id, entry, position, reps, kg, completed = true)
    private fun history(): AppData {
        val older = entry("older-e", "older")
        val newer = entry("newer-e", "newer")
        return AppData(workouts = listOf(workout("newer", 20), workout("older", 10)),
            workoutExercises = listOf(newer, older),
            sets = listOf(set("newer-s", newer.id, 22.0, 6), set("older-s", older.id, 20.0, 7)))
    }

    private fun assertLegacyPrs(data: AppData, workoutId: String, result: WorkoutCalculations.WorkoutPrs) {
        val weights = LegacyPrOracle.weights(data, workoutId)
        val reps = LegacyPrOracle.repetitions(data, workoutId)
        assertEquals("Legacy weight PRs for $workoutId", weights, result.weights)
        assertEquals("Legacy repetition PRs for $workoutId", reps, result.repetitions)
        assertEquals(weights, WorkoutCalculations.prsForWorkout(data, workoutId))
        assertEquals(reps, WorkoutCalculations.repsPrsForWorkout(data, workoutId))
        val summary = WorkoutCalculations.summary(data, workoutId)!!
        assertEquals(weights.filterNot { it.firstRecord }, summary.prs)
        assertEquals(weights.filter { it.firstRecord }, summary.firstRecords)
        assertEquals(reps.filterNot { it.firstRecord }, summary.repsPrs)
        assertEquals(reps.filter { it.firstRecord }, summary.firstRepsRecords)
    }

    private fun assertLegacyRecordsAndHistory(data: AppData, analysis: WorkoutCalculations.HistoricalAnalysis) {
        val weights = LegacyPrOracle.weightRecords(data)
        val repetitions = LegacyPrOracle.repetitionRecords(data)
        assertEquals(weights, analysis.records())
        assertEquals(repetitions, analysis.repsRecords())
        assertEquals(weights, WorkoutCalculations.records(data))
        assertEquals(repetitions, WorkoutCalculations.repsRecords(data))
        data.workoutExercises.map { it.exerciseId to it.convention }.distinct().forEach { (id, convention) ->
            val expected = LegacyPrOracle.history(data, id, convention)
            assertEquals(expected, analysis.performanceHistory(id, convention))
            assertEquals(expected, WorkoutCalculations.performanceHistory(data, id, convention))
        }
    }

    @Test fun emptyUnfinishedWarmupAndInvalidActiveSetsNeverBuildHistoricalBaselines() {
        val history = WorkoutCalculations.historicalAnalysis(history())
        val active = workout("active", 30, false)
        val entry = entry("active-e", active.id)
        val rejected = listOf(
            set("unfinished", entry.id, 999.0).copy(completed = false),
            set("warmup", entry.id, 999.0).copy(warmup = true),
            set("zero-reps", entry.id, 999.0, 0),
            set("negative", entry.id, -1.0),
            set("nan", entry.id, Double.NaN),
            set("infinite", entry.id, Double.POSITIVE_INFINITY)
        )
        (listOf(emptyList<WorkoutSet>()) + rejected.map { listOf(it) } + listOf(rejected)).forEach { rows ->
            val prs = history.prsForActive(active, listOf(entry), rows)
            assertTrue(prs.weights.isEmpty())
            assertTrue(prs.repetitions.isEmpty())
        }
        assertEquals("Rejected candidates must return before scanning historical baselines", 0, history.baselineBuildCount)
    }

    @Test fun oneBaselineServesWeightRepsAndSubsequentActiveEditsIncludingUnitRoundTrips() {
        val analysis = WorkoutCalculations.historicalAnalysis(history())
        val active = workout("active", 30, false)
        val e = entry("active-e", active.id)
        val first = analysis.prsForActive(active, listOf(e), listOf(set("active-s", e.id, 23.0, 4)))
        assertEquals(22.0, first.weights.single().previousWeightKg!!, 0.0)
        assertEquals(4, first.repetitions.single().newReps)
        val roundTrip = Math.nextUp(WeightUnits.lbToKg(WeightUnits.kgToLb(22.0)))
        val edited = analysis.prsForActive(active, listOf(e), listOf(set("active-s", e.id, roundTrip, 7)))
        assertTrue(edited.weights.isEmpty())
        assertEquals(6, edited.repetitions.single().previousReps)
        assertEquals(1, analysis.baselineBuildCount)
        analysis.prsForActive(active, listOf(e), emptyList())
        assertEquals(1, analysis.baselineBuildCount)
    }

    @Test fun bodyweightAndAssistanceAreRepetitionCandidatesRatherThanEmptyWorkingSessions() {
        val analysis = WorkoutCalculations.historicalAnalysis(history())
        val active = workout("active", 30, false)
        val body = entry("body", active.id, "pull", LoadConvention.BODYWEIGHT)
        val assistance = entry("assisted", active.id, "pull", LoadConvention.ASSISTANCE)
        val result = analysis.prsForActive(active, listOf(body, assistance),
            listOf(set("body-s", body.id, 0.0, 12), set("assisted-s", assistance.id, 30.0, 10)))
        assertTrue(result.weights.isEmpty())
        assertEquals(2, result.repetitions.size)
        assertFalse(result.repetitions.first().assistance)
        assertTrue(result.repetitions.last().assistance)
        assertEquals(1, analysis.baselineBuildCount)
    }

    @Test fun finishedAnalysisUsesOneBaselinePassAndReusesItForLaterActiveWorkout() {
        val data = history()
        val analysis = WorkoutCalculations.historicalAnalysis(data)
        data.workouts.forEach { w ->
            val result = analysis.prsForWorkout(w.id)
            assertLegacyPrs(data, w.id, result)
        }
        assertEquals(1, analysis.baselineBuildCount)
        val active = workout("active", 30, false)
        val e = entry("active-e", active.id)
        val result = analysis.prsForActive(active, listOf(e), listOf(set("active-s", e.id, 22.0, 7)))
        assertTrue(result.weights.isEmpty())
        assertEquals(6, result.repetitions.single().previousReps)
        assertEquals("Active calculations must reuse the completed-history pass", 1, analysis.baselineBuildCount)
        assertSame(analysis.records(), analysis.records())
        assertSame(analysis.repsRecords(), analysis.repsRecords())
        assertSame(analysis.performanceHistory("bench"), analysis.performanceHistory("bench"))
    }

    @Test fun backdatedActiveWorkoutUsesOnlyEarlierFinishedChronology() {
        val data = history()
        val analysis = WorkoutCalculations.historicalAnalysis(data)
        analysis.prsForWorkout("newer")
        val active = workout("active", 15, false)
        val e = entry("active-e", active.id)
        val result = analysis.prsForActive(active, listOf(e), listOf(set("active-s", e.id, 21.0, 6)))
        assertEquals(20.0, result.weights.single().previousWeightKg!!, 0.0)
        assertEquals(2, analysis.baselineBuildCount)
    }

    @Test fun analysisOwnsDefensiveCopiesAndIgnoresDiscardedOrActiveArchiveRows() {
        val data = history()
        val rows = data.sets.toMutableList()
        val entries = data.workoutExercises.toMutableList()
        val workouts = data.workouts.toMutableList()
        val analysis = WorkoutCalculations.historicalAnalysis(data.copy(workouts = workouts,
            workoutExercises = entries, sets = rows))
        rows.clear(); entries.clear(); workouts.clear()
        assertEquals(22.0, analysis.records().single().weightKg, 0.0)
        assertEquals(6, analysis.performanceHistory("bench").first().sets.single().reps)
        val active = workout("active", 30, false)
        val e = entry("active-e", active.id)
        val contaminated = data.copy(workouts = data.workouts + active,
            workoutExercises = data.workoutExercises + e + entry("orphan", "deleted"),
            sets = data.sets + set("active-s", e.id, 500.0) + set("orphan-s", "orphan", 999.0))
        assertEquals(analysis.records(), WorkoutCalculations.historicalAnalysis(contaminated).records())
    }

    @Test fun sharedHistoryMatchesUncachedCalculationsWithChronologyTiesVariationsAndStoragePermutations() {
        repeat(20) { seed ->
            val random = Random(seed)
            val workouts = List(15) { workout("w$it", (it / 3).toLong()) }
            val entries = workouts.flatMap { w -> List(3) { i -> entry("${w.id}-e$i", w.id,
                if (i == 2) "pull" else "bench",
                if (i == 2) LoadConvention.BODYWEIGHT else if (i == 1) LoadConvention.PER_HAND else LoadConvention.TOTAL_LOAD,
                i) } }.shuffled(random)
            val rows = entries.flatMap { e -> List(3) { i -> set("${e.id}-$i", e.id,
                if (e.convention == LoadConvention.BODYWEIGHT) 0.0 else (random.nextInt(4) + 20).toDouble(),
                random.nextInt(1, 15), i).copy(completed = random.nextBoolean(), warmup = i == 0,
                assistance = e.convention == LoadConvention.BODYWEIGHT && random.nextBoolean()) } }.shuffled(random)
            val data = AppData(workouts = workouts.shuffled(random), workoutExercises = entries, sets = rows)
            val analysis = WorkoutCalculations.historicalAnalysis(data)
            workouts.forEach { w ->
                val result = analysis.prsForWorkout(w.id)
                assertLegacyPrs(data, w.id, result)
            }
            assertLegacyRecordsAndHistory(data, analysis)
            assertEquals(1, analysis.baselineBuildCount)
        }
    }

    @Test fun nontransitiveUlpChainsKeepLegacyOrderSensitiveBaselines() {
        val base = 100.0
        val middle = generateSequence(base) { Math.nextUp(it) }.drop(4).first()
        val high = generateSequence(base) { Math.nextUp(it) }.drop(8).first()
        val workouts = listOf(workout("oldest", 0), workout("middle", 1), workout("newest", 2), workout("target", 3))
        val entries = workouts.map { entry("${it.id}-e", it.id) }.reversed()
        val load = mapOf("oldest" to base, "middle" to middle, "newest" to high, "target" to middle)
        val data = AppData(workouts = workouts.reversed(), workoutExercises = entries,
            sets = entries.mapIndexed { i, e -> set("${e.id}-s", e.id, load.getValue(e.workoutId), 10 - i) })
        val analysis = WorkoutCalculations.historicalAnalysis(data)
        workouts.forEach { w ->
            val result = analysis.prsForWorkout(w.id)
            assertLegacyPrs(data, w.id, result)
        }
        assertLegacyRecordsAndHistory(data, analysis)
        assertTrue("Ambiguous equivalence must take the correctness fallback", analysis.baselineBuildCount > 1)
    }

    @Test fun nearLoadRepresentativesStorageTiesAndBridgingActiveLoadsMatchUncachedBaseline() {
        fun shifted(steps: Int) = generateSequence(100.0) { Math.nextUp(it) }.drop(steps).first()
        repeat(30) { seed ->
            val random = Random(seed)
            val workouts = List(6) { workout("w$it", it.toLong()) }
            val entries = workouts.map { entry("${it.id}-e", it.id) }.shuffled(random)
            val rows = entries.map { e -> set("${e.id}-s", e.id,
                shifted(if (e.workoutId.substring(1).toInt() % 2 == 0) random.nextInt(0, 3) else random.nextInt(9, 12)),
                random.nextInt(5, 8)) }
            val data = AppData(workouts = workouts.shuffled(random), workoutExercises = entries, sets = rows)
            val analysis = WorkoutCalculations.historicalAnalysis(data)
            workouts.forEach { w ->
                val result = analysis.prsForWorkout(w.id)
                assertLegacyPrs(data, w.id, result)
            }
            assertLegacyRecordsAndHistory(data, analysis)
            val active = workout("active", 10, false)
            val e = entry("active-e", active.id)
            val activeSets = listOf(set("active-s", e.id, shifted(6), 9))
            val combined = data.copy(workouts = data.workouts + active,
                workoutExercises = entries + e, sets = rows + activeSets)
            val result = analysis.prsForActive(active, listOf(e), activeSets)
            assertLegacyPrs(combined, active.id, result)
            assertEquals(1, analysis.baselineBuildCount)
        }
    }

    @Test fun duplicateEntryWithNoCompletedSetsRetainsOriginalNameAndRecordOrdering() {
        val w = workout("w", 10)
        val empty = entry("empty-first", w.id)
        val other = entry("other", w.id, "other", position = 1)
        val completed = entry("completed-later", w.id, position = 2)
        val data = AppData(workouts = listOf(w), workoutExercises = listOf(empty, other, completed),
            sets = listOf(set("other-s", other.id, 10.0), set("completed-s", completed.id, 20.0)))
        val records = WorkoutCalculations.prsForWorkout(data, w.id)
        assertEquals(listOf("bench", "other"), records.map { it.exerciseId })
        assertEquals(empty.name, records.first().exerciseName)
        assertEquals(records, WorkoutCalculations.historicalAnalysis(data).prsForWorkout(w.id).weights)
    }
}
