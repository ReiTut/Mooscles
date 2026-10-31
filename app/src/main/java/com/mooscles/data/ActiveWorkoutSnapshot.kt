package com.mooscles.data

/** Atomic editable state returned by Start/resume without reading the workout archive. */
data class ActiveWorkoutSnapshot(
    val workout: Workout,
    val exercises: List<Exercise>,
    val entries: List<WorkoutExercise>,
    val sets: List<WorkoutSet>,
) {
    /** A full read may enrich this editor only after it acknowledges its latest saved rows. */
    fun matches(data: AppData): Boolean {
        if (data.activeWorkout != workout || data.workoutItems(workout.id) != entries) return false
        val entryIds = entries.mapTo(hashSetOf()) { it.id }
        val actual = data.sets.filter { it.workoutExerciseId in entryIds }.associateBy { it.id }
        return actual.size == sets.size && sets.all { expected ->
            actual[expected.id]?.let { row ->
                row.id == expected.id && row.workoutExerciseId == expected.workoutExerciseId &&
                    row.position == expected.position && row.reps == expected.reps &&
                    row.weightKg == expected.weightKg && row.completed == expected.completed &&
                    row.warmup == expected.warmup && row.assistance == expected.assistance
            } == true
        }
    }

    fun withSet(saved: WorkoutSet): ActiveWorkoutSnapshot =
        if (sets.none { it.id == saved.id }) this
        else copy(sets = sets.map { if (it.id == saved.id) saved else it })

    fun withRest(entryId: String, seconds: Int): ActiveWorkoutSnapshot =
        copy(entries = entries.map { if (it.id == entryId) it.copy(restSeconds = seconds) else it })
}
