package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineSet
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

    private fun best(candidates: List<WorkoutSet>): WorkoutSet? {
        val highestLoad = candidates.maxOfOrNull { it.weightKg } ?: return null
        // Keep the actual set's values together, including equivalent kg/lb round trips.
        return candidates.filter { sameLoad(it.weightKg, highestLoad) }
            .maxWithOrNull(compareBy<WorkoutSet> { it.reps }.thenBy { it.weightKg })
    }

    /** A match requires provenance from the workout start. Positions can change on either side. */
    fun latestByPlannedSet(data: AppData, plannedSets: List<RoutineSet> = data.routineSets): Map<String, RoutineTargetRecord> {
        val plansById = data.routineExercises.associateBy { it.id }
        val exercisesById = data.exercises.associateBy { it.id }
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }
        val entries = data.workoutExercises.associateBy { it.id }
        val rows = plannedSets.associateBy { it.id }
        val candidates = data.sets.filter { set ->
            val row = rows[set.sourceRoutineSetId] ?: return@filter false
            val plan = plansById[row.routineExerciseId] ?: return@filter false
            val entry = entries[set.workoutExerciseId] ?: return@filter false
            val workout = finished[entry.workoutId] ?: return@filter false
            workout.sourceRoutineId == plan.routineId && entry.exerciseId == plan.exerciseId &&
                entry.convention == exercisesById[plan.exerciseId]?.convention && eligible(set, entry.convention)
        }.groupBy { requireNotNull(it.sourceRoutineSetId) }
        return candidates.mapValues { (_, sets) ->
            val set = requireNotNull(best(sets))
            val entry = entries.getValue(set.workoutExerciseId)
            RoutineTargetRecord(entry.exerciseId, entry.convention, set.weightKg.takeUnless { it == 0.0 }, set.reps)
        }
    }

    /** The record for one exact exercise and load convention, using only sets from finished workouts. */
    fun bestForExercise(exerciseId: String, convention: LoadConvention, finishedSets: List<WorkoutSet>): RoutineTargetRecord? {
        val winner = best(finishedSets.filter { eligible(it, convention) }) ?: return null
        return RoutineTargetRecord(exerciseId, convention, winner.weightKg.takeUnless { it == 0.0 }, winner.reps)
    }

    /** Exercise-wide records for legacy backup checkpoints. Planned targets use [latestByPlannedSet]. */
    fun latest(data: AppData): List<RoutineTargetRecord> {
        val finishedIds = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.map { it.id }.toSet()
        val setsByExercise = data.sets.groupBy { it.workoutExerciseId }
        return data.workoutExercises.filter { it.workoutId in finishedIds }
            .groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, exercises) ->
                bestForExercise(key.exerciseId, key.convention, exercises.flatMap { setsByExercise[it.id].orEmpty() })
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
