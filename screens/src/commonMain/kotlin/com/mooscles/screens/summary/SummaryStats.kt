package com.mooscles.screens.summary

import com.mooscles.data.LoadConvention
import com.mooscles.screens.format.BestSets
import com.mooscles.screens.format.NoBestSet
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi

/** The summary's numbers per exercise, read from the UI contract. */
object SummaryStats {
    /**
     * The completed sets that count, in set order: done, with repetitions and a finite load not
     * below zero (the domain's `WorkoutCalculations.validCompleted`, which the share card reads).
     */
    fun completedSets(entry: WorkoutExerciseUi): List<SetUi> =
        entry.sets.filter { it.completed && it.reps > 0 && it.weightKg.isFinite() && it.weightKg >= 0.0 }

    /**
     * The best set of [entry], by the share card's rule ([BestSets]): the heaviest completed
     * working set (not a warm-up, not assisted), ties by the most repetitions. Null without a
     * working set; [noBestSet] then says why.
     */
    fun bestSet(entry: WorkoutExerciseUi): SetUi? {
        val assistance = entry.assistanceConvention()
        val eligible = completedSets(entry).filter { BestSets.eligible(it.warmup, it.assistance, assistance) }
        return BestSets.best(eligible, { it.weightKg }, { it.reps })
    }

    /** Why [entry] has no [bestSet]: only warm-ups, only assistance, or no working set. */
    fun noBestSet(entry: WorkoutExerciseUi): NoBestSet =
        BestSets.whyNone(completedSets(entry), { it.warmup }, { it.assistance }, entry.assistanceConvention())

    private fun WorkoutExerciseUi.assistanceConvention() = exercise.convention == LoadConvention.ASSISTANCE.label
}
