package com.mooscles.ui

import com.mooscles.backup.ImportSummary
import com.mooscles.domain.RestDuration

/** Presentation snapshots use kg internally; only text entry and display use the selected unit. */
data class ExerciseUi(
    val id: String, val name: String, val muscle: String, val equipment: String,
    val convention: String = "External load as entered", val custom: Boolean = false,
)
/**
 * One planned set. [inherited] means it follows the exercise's record; a warm-up ([warmup]) never does, even when a
 * backup marked it so, so the two are never both true.
 */
data class RoutineSetUi(
    val id: String, val reps: Int = 0, val weightKg: Double? = null, val inherited: Boolean = true,
    val warmup: Boolean = false,
)

data class PlannedExerciseUi(
    val id: String, val exercise: ExerciseUi, val sets: Int = 2, val reps: Int = 0,
    val weightKg: Double? = null, val restSeconds: Int = RestDuration.DEFAULT_SECONDS,
    val setTargets: List<RoutineSetUi> = emptyList(),
)
data class RoutineFolderUi(val id: String, val name: String)
/**
 * [folderId] is always an existing folder: a routine whose folder is gone is shown in the default one, as the
 * repository orders it. [lastFinishedAt] is the latest `endedAt` of the finished workouts started from it.
 */
data class RoutineUi(val id: String, val name: String, val exercises: List<PlannedExerciseUi> = emptyList(),
    val folderId: String = "default", val lastFinishedAt: Long? = null)
/** One planned working set of a target line, in exact kg; 0 reps and a null load are blank. */
data class TargetSetUi(val reps: Int, val weightKg: Double?)
/** What the source routine plans for one workout entry: its [working] sets in order and how many [warmups]. */
data class RoutineTargetUi(val working: List<TargetSetUi>, val warmups: Int)
data class SetUi(
    val id: String, val reps: Int = 0, val weightKg: Double = 0.0,
    val completed: Boolean = false, val warmup: Boolean = false, val assistance: Boolean = false,
    /** The record beaten by exactly this set, as the domain chose it; never a first record. */
    val record: SetRecordUi? = null,
)
/**
 * A record beaten by one set. [previousKg] is the history's best load (weight records only); [previousReps] the
 * history's most reps at this load (reps records only). When one set beats both, it shows the weight record.
 */
data class SetRecordUi(val kind: RecordKind, val previousKg: Double? = null, val previousReps: Int? = null)
/** One completed set of the previous session, in exact kg; [assistance] is the set's own flag, as in [SetUi]. */
data class PreviousSetUi(val weightKg: Double, val reps: Int, val warmup: Boolean, val assistance: Boolean)
data class WorkoutExerciseUi(
    val id: String, val exercise: ExerciseUi, val sets: List<SetUi> = emptyList(),
    val restSeconds: Int = RestDuration.DEFAULT_SECONDS, val previous: String? = null,
    val provisionalRecordKg: Double? = null, val firstRecord: Boolean = false,
    val records: List<RecordUi> = emptyList(),
    /** The completed sets of the latest earlier session of this exercise and convention, in set order. */
    val previousSets: List<PreviousSetUi> = emptyList(),
    /** That session's `startedAt`; null without an earlier session. */
    val previousDate: Long? = null,
    /**
     * The active workout's source routine's plan for this entry, as the routine is now; null in history, without
     * a source routine, or when the entry has no match there. The n-th entry of an exercise matches the n-th
     * entry of that exercise in the routine, by position.
     */
    val target: RoutineTargetUi? = null,
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
    val sourceRoutineId: String? = null,
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
    val progress: Float = if (finished || durationSeconds <= 0) 0f
        else (remainingSeconds.toFloat() / durationSeconds).coerceIn(0f, 1f),
    /** Compatibility with timer data written by Build 12; invisible to the restored UI. */
    val workoutExerciseId: String? = null,
)
data class PreferencesUi(
    val pounds: Boolean = false, val theme: String = "system", val autoRest: Boolean = true,
    val defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS,
    val selectedRoutineFolderId: String = "default",
)
data class UiState(
    val loading: Boolean = true, val exercises: List<ExerciseUi> = emptyList(),
    val routines: List<RoutineUi> = emptyList(), val history: List<WorkoutUi> = emptyList(),
    val active: WorkoutUi? = null, val records: List<RecordUi> = emptyList(),
    val timer: TimerUi = TimerUi(), val preferences: PreferencesUi = PreferencesUi(),
    val error: String? = null,
    val updates: UpdateUi = UpdateUi(), val updateSettingsRequest: Long = 0,
    val startingWorkout: Boolean = false,
    val discardingWorkout: Boolean = false, val discardNavigationRequest: Long = 0,
    val routineFolders: List<RoutineFolderUi> = listOf(RoutineFolderUi("default", "My routines")),
    val backup: BackupUi = BackupUi(),
    /** Why [error] is set, for screens that show their own localized copy instead of [error]'s text. */
    val errorCode: UiError? = null,
    /**
     * The id the failed command was about (plan decision 7): the new exercise, routine, copy, folder or set, or
     * the routine or workout something was added to. Null for other commands and for failures that are not a
     * command's. Cleared with [error].
     */
    val errorSubject: String? = null,
    /**
     * How many commands about each subject have failed while this screen state lives. A screen waiting for the
     * echo of a command about an id releases its wait when the id's count grows past the one it saw when it sent
     * the command: a later failure about another id replaces [errorSubject], never this count, and clearing the
     * error keeps it (preflight F10).
     */
    val subjectFailures: Map<String, Int> = emptyMap(),
)

/** What went wrong, as the new screens word it. [UiErrors] translates failures into it. */
enum class UiError {
    SAVE_FAILED, TIMER_STORAGE, LOCAL_DATA_UNAVAILABLE, BLOCKED_BY_UPDATE, SET_GONE, EXERCISE_GONE, WORKOUT_GONE,
    SET_LIMIT, INVALID_VALUE, NEEDS_COMPLETED_SET, SET_NOT_RESTORED, GENERIC,
}

/**
 * [pendingImport] is the summary of a read file waiting for confirmation; [message] reports the last outcome
 * ([messageIsError] tells failures from successes); [working] names the running operation while [busy].
 */
data class BackupUi(
    val busy: Boolean = false, val pendingImport: ImportSummary? = null, val message: String? = null,
    val messageIsError: Boolean = false, val working: String? = null,
)

/** Document URIs travel as strings so this contract stays free of Android types. */
interface DataActions {
    fun exportData(uri: String)
    fun prepareImport(uri: String)
    fun confirmImport()
    fun cancelImport()
    fun clearBackupMessage()
}

/** All changes are persisted by the implementation. move* takes -1 or +1. */
interface MoosclesActions : UpdateActions, DataActions {
    fun clearError()
    fun createRoutine(name: String)
    /** Creates a routine under [id], chosen by the screen, in [folderId]; an id already in use is refused. */
    fun createRoutine(name: String, folderId: String, id: String)
    fun renameRoutine(id: String, name: String)
    fun duplicateRoutine(id: String)
    /** Copies routine [id] as [copyName] (in the screen's language) under [newId], chosen by the screen. */
    fun duplicateRoutine(id: String, copyName: String, newId: String)
    /** Reorders [folderId]'s routines; [ids] is exactly that folder's routines. The other routines keep their places. */
    fun reorderRoutines(folderId: String, ids: List<String>)
    /** [ids] is every folder, the default one included, in its new order. */
    fun reorderRoutineFolders(ids: List<String>)
    /** Deletes a folder (never the default one); its routines move to the end of the default folder. */
    fun deleteRoutineFolder(id: String)
    fun deleteRoutine(id: String)
    fun createRoutineFolder(name: String)
    fun renameRoutineFolder(id: String, name: String)
    fun selectRoutineFolder(id: String)
    fun moveRoutineToFolder(routineId: String, folderId: String)
    fun addRoutineExercise(routineId: String, exerciseId: String)
    /** Adds every exercise in the order given, all or none. */
    fun addRoutineExercises(routineId: String, exerciseIds: List<String>)
    /** [ids] is exactly the routine's entries, in their new order. */
    fun reorderRoutineExercises(routineId: String, ids: List<String>)
    fun replaceRoutineExercise(entryId: String, exerciseId: String)
    fun removeRoutineExercise(entryId: String)
    fun addRoutineSet(entryId: String)
    fun removeRoutineSet(setId: String)
    fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?)
    fun inheritRoutineSet(setId: String)
    /** A warm-up is always a manual set; turning one into a working set keeps it manual. */
    fun setRoutineSetWarmup(setId: String, warmup: Boolean)
    /** Undoes [removeRoutineSet]: puts [set] back in [entryId] at [position], with its id, values, warm-up and mode. */
    fun restoreRoutineSet(entryId: String, position: Int, set: RoutineSetUi)
    /**
     * Copies the reps and load of the working set [sourceSetId] (as it shows them, also when it follows the record)
     * to every other working set of [entryId], which become manual; warm-ups are left alone. One write, all or none.
     */
    fun copyRoutineSetToAll(entryId: String, sourceSetId: String)
    /**
     * Undoes [copyRoutineSetToAll]: puts back the reps, load and mode of each set of [previous] (the sets the copy
     * changed, as they stood), all or none. Refused ([UiError.SET_NOT_RESTORED]) when a set is no longer a working
     * set of [entryId], the entry is gone or it no longer holds the exercise [exerciseId] (it was replaced).
     */
    fun restoreRoutineSetTargets(entryId: String, exerciseId: String, previous: List<RoutineSetUi>)
    fun updateRoutineExerciseRest(entryId: String, seconds: Int)
    /** Creates a custom exercise under [id], chosen by the screen; an id already in use is refused, never overwritten. */
    fun createCustomExercise(id: String, name: String, muscle: String, equipment: String, convention: String)
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
    /** Undoes [removeSet]: puts [set] back in [entryId] at [position], with its id and values. */
    fun restoreSet(entryId: String, position: Int, set: SetUi)
    fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean)
    fun finishWorkout()
    fun discardWorkout()
    fun deleteWorkout(id: String)
    fun setPounds(pounds: Boolean)
    fun setTheme(theme: String)
    fun setAutoRest(enabled: Boolean)
    fun setDefaultRest(seconds: Int)
    /**
     * Starts a rest of [seconds] named [exerciseName]. A rest of a workout's exercise passes that
     * entry's id as [workoutExerciseId] and the entry's snapshot name, as the automatic rest does,
     * so the timer shows the exercise by its entry in the screen's language.
     */
    fun startTimer(exerciseName: String, seconds: Int, workoutExerciseId: String? = null)
    fun pauseTimer()
    fun resumeTimer()
    fun resetTimer()
    fun skipTimer()
    fun adjustTimer(seconds: Int)
}
