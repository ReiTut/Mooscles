package com.mooscles.screens.progress

import com.mooscles.data.LoadConvention
import com.mooscles.ui.SetUi

/**
 * The set rules of `WorkoutCalculations` (`validCompleted`, `workingSet`, `repetitionSet` and `volume`), on
 * [SetUi] (plan decision 4). The domain keeps its own, private, on the stored rows; this is the only copy, and
 * `ProgressIndexDomainHostTest` (`:app`) proves it against the real domain on the large base.
 *
 * Where it differs from `WorkoutUi.volumeKg`: that getter takes every completed set (`completed && !assistance`)
 * and skips only the exact label "Assistance", while [volumeKg] takes only [valid] sets, as the domain's `volume`
 * does, and reads the convention through `LoadConvention.fromLabelOrName` (so the enum name `ASSISTANCE` is skipped
 * too). The two agree on everything the presentation builds (it passes labels, and a completed set with reps of 0
 * adds 0 to both); they part only for a completed set with negative reps or a load that is negative or not finite.
 */
internal object WorkingSets {
    /** Completed, with reps, and a finite load of 0 or more (the domain's `validCompleted`). */
    fun valid(set: SetUi): Boolean = set.completed && set.reps > 0 && set.weightKg.isFinite() && set.weightKg >= 0.0

    /** A set that can hold the load (the domain's `workingSet`): no warm-up, no assistance, a load above 0. */
    fun forLoad(set: SetUi, convention: LoadConvention): Boolean = valid(set) && !set.warmup && !set.assistance &&
        convention != LoadConvention.ASSISTANCE && set.weightKg > 0.0

    /**
     * A set of the repetition group [assistanceGroup] (the domain's `repetitionSet`, in its records' groups): no
     * warm-up, and assisted (the set's flag or the Assistance convention) exactly when the group is.
     */
    fun forReps(set: SetUi, assistanceGroup: Boolean, convention: LoadConvention): Boolean = valid(set) && !set.warmup &&
        (set.assistance || convention == LoadConvention.ASSISTANCE) == assistanceGroup

    /** The domain's `volume`: logged load × reps, warm-ups included, assistance and the Assistance convention out. */
    fun volumeKg(set: SetUi, convention: LoadConvention): Double =
        if (valid(set) && !set.assistance && convention != LoadConvention.ASSISTANCE) set.weightKg * set.reps else 0.0

    /** The rules of a convention label (`ExerciseUi.convention`); one the enum does not know follows Total load. */
    fun convention(label: String): LoadConvention = LoadConvention.fromLabelOrName(label) ?: LoadConvention.TOTAL_LOAD
}
