package com.mooscles

import com.mooscles.data.ActiveWorkoutSnapshot
import com.mooscles.ui.WorkoutUi

/** One process-local editor snapshot, reused only after a fresh atomic storage read matches it. */
internal class WorkoutRecoveryCache {
    private data class Entry(val snapshot: ActiveWorkoutSnapshot, val workout: WorkoutUi)
    private var entry: Entry? = null

    @Synchronized
    fun getValidated(snapshot: ActiveWorkoutSnapshot): WorkoutUi? {
        val saved = entry ?: return null
        if (saved.snapshot != snapshot) {
            entry = null
            return null
        }
        return saved.workout
    }

    @Synchronized
    fun put(snapshot: ActiveWorkoutSnapshot, workout: WorkoutUi) {
        require(workout.id == snapshot.workout.id)
        // Secondary history and PR information belongs to the regular presenter,
        // never this small cache or the Activity that happened to create it.
        require(workout.records.isEmpty() && workout.exercises.all {
            it.previous == null && it.provisionalRecordKg == null && !it.firstRecord && it.records.isEmpty()
        })
        entry = Entry(snapshot, workout)
    }

    @Synchronized
    fun clear(workoutId: String? = null) {
        if (workoutId == null || entry?.snapshot?.workout?.id == workoutId) entry = null
    }
}
