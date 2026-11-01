package com.mooscles.screens.exercise

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals

/** The detail's "Your history" card, from the history and the records in memory (plan decision 19). */
class ExerciseHistorySummariesTest {
    private val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", "Total load")
    private val squat = ExerciseUi("catalog_barbell_back_squat_high_bar", "Back squat", "Quadriceps", "Barbell", "Total load")

    private var nextSet = 0

    private fun set(kg: Double, reps: Int, completed: Boolean = true, warmup: Boolean = false, assistance: Boolean = false) =
        SetUi("s${nextSet++}", reps, kg, completed, warmup, assistance)

    private fun workout(id: String, startedAt: Long, vararg entries: Pair<ExerciseUi, List<SetUi>>, finished: Boolean = true) =
        WorkoutUi(
            id, "Workout $id", startedAt, endedAt = if (finished) startedAt + 60_000 else null,
            exercises = entries.mapIndexed { i, (exercise, sets) -> WorkoutExerciseUi("$id-e$i", exercise, sets) },
        )

    private fun record(exercise: ExerciseUi, kg: Double, reps: Int, date: Long, kind: RecordKind = RecordKind.WEIGHT) =
        RecordUi(exercise, kg, reps, workoutId = "w$date", date = date, kind = kind)

    @Test
    fun noHistory() {
        assertEquals(
            HistorySummary(sessions = 0, lastDate = null, lastWeightKg = null, lastReps = null, bestWeightKg = null, bestReps = null, bestIsRecord = false),
            ExerciseHistorySummaries.of(bench.id, emptyList(), emptyList()),
        )
    }

    @Test
    fun lastSessionBestSetSkipsWarmupsAndAssistance() {
        val older = workout("w1", 1_000, bench to listOf(set(85.0, 5)))
        val newest = workout(
            "w2", 2_000,
            bench to listOf(
                set(100.0, 5, warmup = true), set(90.0, 5, assistance = true), set(80.0, 6), set(80.0, 8), set(70.0, 10),
                set(120.0, 1, completed = false),
            ),
        )
        // Oldest first on purpose: the most recent is found by its date, not by the list's order.
        val summary = ExerciseHistorySummaries.of(bench.id, listOf(older, newest), emptyList())
        assertEquals(2_000L, summary.lastDate)
        assertEquals(80.0, summary.lastWeightKg)
        assertEquals(8, summary.lastReps)
        assertEquals(2, summary.sessions)
    }

    @Test
    fun sessionsCountOnlyWorkoutsWithCompletedSets() {
        val done = workout("w1", 1_000, bench to listOf(set(60.0, 10)))
        val nothingDone = workout("w2", 2_000, bench to listOf(set(60.0, 10, completed = false)))
        val inProgress = workout("w3", 3_000, bench to listOf(set(60.0, 10)), finished = false)
        // A warm-up is a completed set too: the session counts, with no best set to show.
        val warmupOnly = workout("w4", 4_000, bench to listOf(set(40.0, 12, warmup = true)))
        val summary = ExerciseHistorySummaries.of(bench.id, listOf(warmupOnly, inProgress, nothingDone, done), emptyList())
        assertEquals(2, summary.sessions)
        assertEquals(4_000L, summary.lastDate)
        assertEquals(null, summary.lastWeightKg)
        assertEquals(null, summary.lastReps)
        // The best of all history still comes from the working sets.
        assertEquals(60.0, summary.bestWeightKg)
        assertEquals(10, summary.bestReps)
    }

    @Test
    fun aWeightRecordIsTheBest() {
        val history = listOf(workout("w1", 1_000, bench to listOf(set(95.0, 5))))
        val records = listOf(
            record(bench, 100.0, 3, date = 1_500),
            // The same load later: the most recent of the heaviest is the record shown.
            record(bench, 100.0, 2, date = 1_800),
            record(bench, 80.0, 12, date = 1_900),
            // A repetitions record and another exercise's record are not this card's.
            record(bench, 60.0, 20, date = 2_000, kind = RecordKind.REPS),
            record(squat, 200.0, 1, date = 2_100),
        )
        val summary = ExerciseHistorySummaries.of(bench.id, history, records)
        assertEquals(100.0, summary.bestWeightKg)
        assertEquals(2, summary.bestReps)
        assertEquals(true, summary.bestIsRecord)
    }

    @Test
    fun withoutARecordTheBestSetOfAllHistory() {
        val history = listOf(
            workout("w3", 3_000, bench to listOf(set(85.0, 10), set(130.0, 3, warmup = true))),
            workout("w2", 2_000, bench to listOf(set(90.0, 7))),
            workout("w1", 1_000, bench to listOf(set(90.0, 5), set(140.0, 2, assistance = true))),
        )
        val summary = ExerciseHistorySummaries.of(bench.id, history, emptyList())
        // The heaviest working set; among equal loads, the most repetitions.
        assertEquals(90.0, summary.bestWeightKg)
        assertEquals(7, summary.bestReps)
        assertEquals(false, summary.bestIsRecord)
        assertEquals(85.0, summary.lastWeightKg)
        assertEquals(3, summary.sessions)
    }

    @Test
    fun otherExercisesDoNotCount() {
        val history = listOf(
            workout("w2", 2_000, squat to listOf(set(200.0, 1))),
            workout("w1", 1_000, squat to listOf(set(150.0, 5)), bench to listOf(set(70.0, 8))),
        )
        val summary = ExerciseHistorySummaries.of(bench.id, history, listOf(record(squat, 200.0, 1, date = 2_000)))
        assertEquals(
            HistorySummary(sessions = 1, lastDate = 1_000, lastWeightKg = 70.0, lastReps = 8, bestWeightKg = 70.0, bestReps = 8, bestIsRecord = false),
            summary,
        )
        assertEquals(0, ExerciseHistorySummaries.of("custom-x", history, emptyList()).sessions)
    }
}
