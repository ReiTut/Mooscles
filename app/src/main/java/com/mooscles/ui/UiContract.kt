package com.mooscles.ui

import com.mooscles.domain.RestDuration

/** Presentation snapshots use kg internally; only text entry and display use the selected unit. */
data class ExerciseUi(
    val id: String, val name: String, val muscle: String, val equipment: String,
    val convention: String = "External load as entered", val custom: Boolean = false,
)
data class RoutineSetUi(
    val id: String, val reps: Int = 0, val weightKg: Double? = null, val inherited: Boolean = true,
)

data class PlannedExerciseUi(
    val id: String, val exercise: ExerciseUi, val sets: Int = 2, val reps: Int = 0,
    val weightKg: Double? = null, val restSeconds: Int = RestDuration.DEFAULT_SECONDS,
    val setTargets: List<RoutineSetUi> = emptyList(),
)
data class RoutineUi(val id: String, val name: String, val exercises: List<PlannedExerciseUi> = emptyList())
data class SetUi(
    val id: String, val reps: Int = 0, val weightKg: Double = 0.0,
    val completed: Boolean = false, val warmup: Boolean = false, val assistance: Boolean = false,
)
data class WorkoutExerciseUi(
    val id: String, val exercise: ExerciseUi, val sets: List<SetUi> = emptyList(),
    val restSeconds: Int = RestDuration.DEFAULT_SECONDS, val previous: String? = null,
    val provisionalRecordKg: Double? = null, val firstRecord: Boolean = false,
    val records: List<RecordUi> = emptyList(),
)
enum class RecordKind { WEIGHT, REPS }
data class RecordUi(
    val exercise: ExerciseUi, val weightKg: Double, val reps: Int,
    val previousKg: Double? = null, val workoutId: String = "", val date: Long = 0,
    val kind: RecordKind = RecordKind.WEIGHT, val previousReps: Int? = null, val assistance: Boolean = false,
) {
    val firstRecord get() = if (kind == RecordKind.WEIGHT) previousKg == null else previousReps == null
    val label get() = if (kind == RecordKind.WEIGHT) "Weight PR" else "Reps PR"
}
data class WorkoutUi(
    val id: String, val name: String, val startedAt: Long, val endedAt: Long? = null,
    val exercises: List<WorkoutExerciseUi> = emptyList(), val records: List<RecordUi> = emptyList(),
) {
    /** First records and records beaten both qualify; multiple badges still count once. */
    val prExerciseCount get() = records.map { it.exercise.id to it.exercise.convention }.distinct().size
    val beatenPrExerciseCount get() = records.filterNot { it.firstRecord }.map { it.exercise.id to it.exercise.convention }.distinct().size
    val firstRecordExerciseCount get() = records.filter { it.firstRecord }.map { it.exercise.id to it.exercise.convention }.distinct().size
    val completedSets get() = exercises.sumOf { e -> e.sets.count { it.completed } }
    val completedExercises get() = exercises.count { e -> e.sets.any { it.completed } }
    val volumeKg get() = exercises.filter { it.exercise.convention != "Assistance" }.sumOf { e -> e.sets.filter { it.completed && !it.assistance }.sumOf { it.weightKg * it.reps } }
}
data class TimerUi(
    val exerciseName: String = "Rest timer", val remainingSeconds: Int = 0,
    val durationSeconds: Int = RestDuration.DEFAULT_SECONDS, val running: Boolean = false, val paused: Boolean = false,
    val finished: Boolean = false,
)
data class PreferencesUi(
    val pounds: Boolean = false, val theme: String = "system", val autoRest: Boolean = true,
    val defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS,
)
data class UiState(
    val loading: Boolean = true, val exercises: List<ExerciseUi> = emptyList(),
    val routines: List<RoutineUi> = emptyList(), val history: List<WorkoutUi> = emptyList(),
    val active: WorkoutUi? = null, val records: List<RecordUi> = emptyList(),
    val timer: TimerUi = TimerUi(), val preferences: PreferencesUi = PreferencesUi(),
    val error: String? = null,
    val updates: UpdateUi = UpdateUi(), val updateSettingsRequest: Long = 0,
)

/** All changes are persisted by the implementation. move* takes -1 or +1. */
interface MoosclesActions : UpdateActions {
    fun clearError()
    fun createRoutine(name: String)
    fun renameRoutine(id: String, name: String)
    fun duplicateRoutine(id: String)
    fun deleteRoutine(id: String)
    fun moveRoutine(id: String, direction: Int)
    fun addRoutineExercise(routineId: String, exerciseId: String)
    fun replaceRoutineExercise(entryId: String, exerciseId: String)
    fun removeRoutineExercise(entryId: String)
    fun moveRoutineExercise(entryId: String, direction: Int)
    fun updateRoutineExercise(entryId: String, sets: Int, reps: Int, weightKg: Double?, restSeconds: Int)
    fun addRoutineSet(entryId: String)
    fun removeRoutineSet(setId: String)
    fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?)
    fun inheritRoutineSet(setId: String)
    fun updateRoutineExerciseRest(entryId: String, seconds: Int)
    fun saveCustomExercise(id: String?, name: String, muscle: String, equipment: String, convention: String)
    fun startWorkout(routineId: String?)
    fun renameWorkout(id: String, name: String)
    fun addWorkoutExercise(workoutId: String, exerciseId: String)
    fun replaceWorkoutExercise(entryId: String, exerciseId: String)
    fun removeWorkoutExercise(entryId: String)
    fun moveWorkoutExercise(entryId: String, direction: Int)
    fun updateWorkoutExerciseRest(entryId: String, seconds: Int)
    fun addSet(entryId: String)
    fun removeSet(setId: String)
    fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean)
    fun finishWorkout()
    fun discardWorkout()
    fun deleteWorkout(id: String)
    fun setPounds(pounds: Boolean)
    fun setTheme(theme: String)
    fun setAutoRest(enabled: Boolean)
    fun setDefaultRest(seconds: Int)
    fun startTimer(exerciseName: String, seconds: Int)
    fun pauseTimer()
    fun resumeTimer()
    fun resetTimer()
    fun skipTimer()
    fun adjustTimer(seconds: Int)
}
