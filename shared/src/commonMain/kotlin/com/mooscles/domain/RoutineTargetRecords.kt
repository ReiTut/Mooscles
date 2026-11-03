package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.math.abs
import kotlin.math.ulp

/** A routine target is always copied from one eligible, completed working set. */
data class RoutineTargetRecord(
    val exerciseId: String,
    val convention: LoadConvention,
    val weightKg: Double?,
    val reps: Int
)

/** Selects the weight-first record used by the repository to persist routine targets. */
object RoutineTargetRecords {
    private data class Key(val exerciseId: String, val convention: LoadConvention)

    // Match PR comparisons: ignore unit-conversion noise without rounding real load increases away.
    private fun sameLoad(first: Double, second: Double): Boolean =
        abs(first - second) <= maxOf(first.ulp, second.ulp) * 4

    private fun heavier(value: Double, baseline: Double): Boolean = value > baseline &&
        value - baseline > maxOf(value.ulp, baseline.ulp) * 4

    private fun eligible(set: WorkoutSet, convention: LoadConvention): Boolean =
        WorkoutCalculations.validCompleted(set) && !set.warmup && !set.assistance &&
            convention != LoadConvention.ASSISTANCE &&
            (set.weightKg > 0.0 || convention == LoadConvention.BODYWEIGHT)

    /**
     * Existing history is included for the feature's first synchronization. Active and discarded
     * workouts cannot establish saved targets; an orphaned snapshot is ignored as well.
     */
    fun latest(data: AppData): List<RoutineTargetRecord> {
        val finishedIds = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.map { it.id }.toSet()
        val setsByExercise = data.sets.groupBy { it.workoutExerciseId }
        return data.workoutExercises.filter { it.workoutId in finishedIds }
            .groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, exercises) ->
                val candidates = exercises.flatMap { setsByExercise[it.id].orEmpty() }
                    .filter { eligible(it, key.convention) }
                val highestLoad = candidates.maxOfOrNull { it.weightKg } ?: return@mapNotNull null
                // Pick the repetitions and load together, including equivalent kg/lb round trips.
                val best = candidates.filter { sameLoad(it.weightKg, highestLoad) }
                    .maxWithOrNull(compareBy<WorkoutSet> { it.reps }.thenBy { it.weightKg })!!
                RoutineTargetRecord(key.exerciseId, key.convention,
                    best.weightKg.takeUnless { it == 0.0 }, best.reps)
            }.sortedWith(compareBy<RoutineTargetRecord> { it.exerciseId }.thenBy { it.convention.name })
    }

    /** A new load, or more repetitions at the record load, advances the consumed PR marker. */
    fun improves(candidate: RoutineTargetRecord, previous: RoutineTargetRecord): Boolean {
        if (candidate.exerciseId != previous.exerciseId || candidate.convention != previous.convention) return false
        val weight = candidate.weightKg ?: 0.0
        val baseline = previous.weightKg ?: 0.0
        return heavier(weight, baseline) || (sameLoad(weight, baseline) && candidate.reps > previous.reps)
    }
}
