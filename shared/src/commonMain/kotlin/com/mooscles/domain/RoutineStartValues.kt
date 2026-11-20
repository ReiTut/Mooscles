package com.mooscles.domain

import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineSet
import com.mooscles.data.WorkoutSet

/** The KG and REPS a routine set starts with in a new workout. Loads are in kg, as stored. */
data class StartValues(val weightKg: Double, val reps: Int)

/**
 * Starting values for the sets of a routine exercise when the routine starts.
 *
 * Every set, manual or Follow the record, warm-up or working, copies the Previous set it pairs with,
 * using the same pairing as the Previous column (warm-ups with warm-ups, working sets with working
 * sets, then by order). A set without a Previous set keeps its routine target.
 */
object RoutineStartValues {
    /**
     * [planned] is one routine exercise's sets in order. [previous] is the exercise's most recent
     * finished session for the same load convention, in set order.
     */
    fun forPlannedSets(planned: List<RoutineSet>, previous: List<WorkoutSet>, convention: LoadConvention): List<StartValues> {
        val valid = previous.filter(WorkoutCalculations::validCompleted)
        val warmups = valid.filter { it.warmup }.iterator()
        val working = valid.filter { !it.warmup }.iterator()
        val assistance = convention == LoadConvention.ASSISTANCE
        return planned.map { row ->
            val target = StartValues(row.targetWeightKg ?: 0.0, row.targetReps)
            val paired = if (row.warmup) warmups.nextOrNull() else working.nextOrNull()
            paired?.let { copy(it, assistance) } ?: target
        }
    }

    /**
     * What a new working set added during an active workout starts with: the Previous working set at
     * [workingIndex] (0 for the first working set), or null when there is none. [assistance] is the new
     * set's assistance flag.
     */
    fun previousWorking(previous: List<WorkoutSet>, assistance: Boolean, workingIndex: Int): StartValues? =
        previous.filter { WorkoutCalculations.validCompleted(it) && !it.warmup }.getOrNull(workingIndex)?.let { copy(it, assistance) }

    // An assisted set's load is assistance; it never becomes a regular set's load, or the reverse.
    private fun copy(previous: WorkoutSet, assistance: Boolean): StartValues? =
        if ((previous.assistance || assistance) != assistance) null else StartValues(previous.weightKg, previous.reps)

    private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null
}
