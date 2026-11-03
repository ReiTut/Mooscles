package com.mooscles.screens.exercise

import com.mooscles.data.LoadConvention
import com.mooscles.screens.format.BestSets
import com.mooscles.screens.summary.SummaryStats
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutUi

/**
 * What the detail's "Your history" card shows of one exercise (spec "Seu histórico"): how many
 * [sessions], the date of the last one and its best set ([lastWeightKg] × [lastReps], null when
 * that session had no working set), and the best set ever ([bestIsRecord] when it is the current
 * weight record).
 */
internal data class HistorySummary(
    val sessions: Int,
    val lastDate: Long?,
    val lastWeightKg: Double?,
    val lastReps: Int?,
    val bestWeightKg: Double?,
    val bestReps: Int?,
    val bestIsRecord: Boolean,
)

/** [HistorySummary] from the state already in memory (plan decision 19): no query of its own. */
internal object ExerciseHistorySummaries {
    /**
     * The summary of [exerciseId] over [history] and [records]. A session is a finished workout
     * with at least one completed set of the exercise (in any of its entries); "last time" is the
     * best working set ([BestSets], warm-ups and assistance left out) of the most recent session;
     * the best is the exercise's current weight record in [records] (the heaviest, the most recent
     * of equal loads) or, without one, the best working set of the whole history.
     */
    fun of(exerciseId: String, history: List<WorkoutUi>, records: List<RecordUi>): HistorySummary {
        // Each session's completed sets, and its working sets, of this exercise.
        val sessions = history.mapNotNull { workout ->
            if (workout.endedAt == null) return@mapNotNull null
            val entries = workout.exercises.filter { it.exercise.id == exerciseId }
            val completed = entries.flatMap { SummaryStats.completedSets(it) }
            if (completed.isEmpty()) return@mapNotNull null
            val working = entries.flatMap { entry ->
                val assistance = entry.exercise.convention == LoadConvention.ASSISTANCE.label
                SummaryStats.completedSets(entry).filter { BestSets.eligible(it.warmup, it.assistance, assistance) }
            }
            Session(workout.startedAt, working)
        }
        val last = sessions.maxByOrNull { it.date }
        val lastBest = last?.let { best(it.working) }
        val record = records
            .filter { it.kind == RecordKind.WEIGHT && it.exercise.id == exerciseId }
            .maxWithOrNull(compareBy<RecordUi> { it.weightKg }.thenBy { it.date })
        val bestSet = if (record == null) best(sessions.flatMap { it.working }) else null
        return HistorySummary(
            sessions = sessions.size,
            lastDate = last?.date,
            lastWeightKg = lastBest?.weightKg,
            lastReps = lastBest?.reps,
            bestWeightKg = record?.weightKg ?: bestSet?.weightKg,
            bestReps = record?.reps ?: bestSet?.reps,
            bestIsRecord = record != null,
        )
    }

    private class Session(val date: Long, val working: List<SetUi>)

    private fun best(sets: List<SetUi>): SetUi? = BestSets.best(sets, { it.weightKg }, { it.reps })
}
