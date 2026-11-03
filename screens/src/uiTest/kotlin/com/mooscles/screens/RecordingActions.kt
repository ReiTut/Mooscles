package com.mooscles.screens

import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.SetUi

/**
 * A [MoosclesActions] that only records: every call adds `"<method>:<arg1>,<arg2>,…"` to [calls],
 * each argument written with its `toString()` (`"updateSet:s2,8,82.5,true,false,false"`), and
 * `"<method>:"` for a method without arguments (`"skipTimer:"`). A list argument is its items joined
 * by `|` (`"addRoutineExercises:r1,bench|row"`); a restored set (or each set of a restored copy) is the whole set, as `toString()`
 * writes it, so a test checks the snapshot and not only the id.
 */
class RecordingActions : MoosclesActions {
    private val recorded = mutableListOf<String>()

    /** The calls in the order they were made. A copy: later calls do not change it. */
    val calls: List<String> get() = recorded.toList()

    private fun record(method: String, vararg args: Any?) {
        recorded += "$method:" + args.joinToString(",") { if (it is List<*>) it.joinToString("|") else it.toString() }
    }

    // UpdateActions
    override fun checkForUpdates() = record("checkForUpdates")
    override fun setAutomaticUpdates(enabled: Boolean) = record("setAutomaticUpdates", enabled)
    override fun setUpdateWifiOnly(enabled: Boolean) = record("setUpdateWifiOnly", enabled)
    override fun setIncludePrereleases(enabled: Boolean) = record("setIncludePrereleases", enabled)
    override fun downloadUpdate() = record("downloadUpdate")
    override fun cancelUpdate() = record("cancelUpdate")
    override fun installUpdate() = record("installUpdate")

    // DataActions
    override fun exportData(uri: String) = record("exportData", uri)
    override fun prepareImport(uri: String) = record("prepareImport", uri)
    override fun confirmImport() = record("confirmImport")
    override fun cancelImport() = record("cancelImport")
    override fun clearBackupMessage() = record("clearBackupMessage")

    // MoosclesActions
    override fun clearError() = record("clearError")
    override fun createRoutine(name: String) = record("createRoutine", name)
    override fun createRoutine(name: String, folderId: String, id: String) = record("createRoutine", name, folderId, id)
    override fun renameRoutine(id: String, name: String) = record("renameRoutine", id, name)
    override fun duplicateRoutine(id: String) = record("duplicateRoutine", id)
    override fun duplicateRoutine(id: String, copyName: String, newId: String) = record("duplicateRoutine", id, copyName, newId)
    override fun reorderRoutines(folderId: String, ids: List<String>) = record("reorderRoutines", folderId, ids)
    override fun reorderRoutineFolders(ids: List<String>) = record("reorderRoutineFolders", ids)
    override fun deleteRoutineFolder(id: String) = record("deleteRoutineFolder", id)
    override fun deleteRoutine(id: String) = record("deleteRoutine", id)
    override fun createRoutineFolder(name: String) = record("createRoutineFolder", name)
    override fun renameRoutineFolder(id: String, name: String) = record("renameRoutineFolder", id, name)
    override fun selectRoutineFolder(id: String) = record("selectRoutineFolder", id)
    override fun moveRoutineToFolder(routineId: String, folderId: String) = record("moveRoutineToFolder", routineId, folderId)
    override fun addRoutineExercise(routineId: String, exerciseId: String) = record("addRoutineExercise", routineId, exerciseId)
    override fun addRoutineExercises(routineId: String, exerciseIds: List<String>) = record("addRoutineExercises", routineId, exerciseIds)
    override fun reorderRoutineExercises(routineId: String, ids: List<String>) = record("reorderRoutineExercises", routineId, ids)
    override fun replaceRoutineExercise(entryId: String, exerciseId: String) = record("replaceRoutineExercise", entryId, exerciseId)
    override fun removeRoutineExercise(entryId: String) = record("removeRoutineExercise", entryId)
    override fun addRoutineSet(entryId: String) = record("addRoutineSet", entryId)
    override fun removeRoutineSet(setId: String) = record("removeRoutineSet", setId)
    override fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) = record("updateRoutineSet", setId, reps, weightKg)
    override fun inheritRoutineSet(setId: String) = record("inheritRoutineSet", setId)
    override fun setRoutineSetWarmup(setId: String, warmup: Boolean) = record("setRoutineSetWarmup", setId, warmup)
    override fun restoreRoutineSet(entryId: String, position: Int, set: RoutineSetUi) = record("restoreRoutineSet", entryId, position, set)
    override fun copyRoutineSetToAll(entryId: String, sourceSetId: String) = record("copyRoutineSetToAll", entryId, sourceSetId)
    override fun restoreRoutineSetTargets(entryId: String, exerciseId: String, previous: List<RoutineSetUi>) =
        record("restoreRoutineSetTargets", entryId, exerciseId, previous)
    override fun updateRoutineExerciseRest(entryId: String, seconds: Int) = record("updateRoutineExerciseRest", entryId, seconds)
    override fun createCustomExercise(id: String, name: String, muscle: String, equipment: String, convention: String) =
        record("createCustomExercise", id, name, muscle, equipment, convention)
    override fun saveCustomExercise(id: String?, name: String, muscle: String, equipment: String, convention: String) =
        record("saveCustomExercise", id, name, muscle, equipment, convention)
    override fun startWorkout(routineId: String?) = record("startWorkout", routineId)
    override fun renameWorkout(id: String, name: String) = record("renameWorkout", id, name)
    override fun addWorkoutExercise(workoutId: String, exerciseId: String) = record("addWorkoutExercise", workoutId, exerciseId)
    override fun replaceWorkoutExercise(entryId: String, exerciseId: String) = record("replaceWorkoutExercise", entryId, exerciseId)
    override fun removeWorkoutExercise(entryId: String) = record("removeWorkoutExercise", entryId)
    override fun moveWorkoutExercise(entryId: String, direction: Int) = record("moveWorkoutExercise", entryId, direction)
    override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) = record("updateWorkoutExerciseRest", entryId, seconds)
    override fun addSet(entryId: String) = record("addSet", entryId)
    override fun removeSet(setId: String) = record("removeSet", setId)
    override fun restoreSet(entryId: String, position: Int, set: SetUi) = record("restoreSet", entryId, position, set)
    override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) =
        record("updateSet", setId, reps, weightKg, completed, warmup, assistance)
    override fun finishWorkout() = record("finishWorkout")
    override fun discardWorkout() = record("discardWorkout")
    override fun deleteWorkout(id: String) = record("deleteWorkout", id)
    override fun setPounds(pounds: Boolean) = record("setPounds", pounds)
    override fun setTheme(theme: String) = record("setTheme", theme)
    override fun setAutoRest(enabled: Boolean) = record("setAutoRest", enabled)
    override fun setDefaultRest(seconds: Int) = record("setDefaultRest", seconds)
    override fun startTimer(exerciseName: String, seconds: Int, workoutExerciseId: String?) =
        record("startTimer", exerciseName, seconds, workoutExerciseId)
    override fun pauseTimer() = record("pauseTimer")
    override fun resumeTimer() = record("resumeTimer")
    override fun resetTimer() = record("resetTimer")
    override fun skipTimer() = record("skipTimer")
    override fun adjustTimer(seconds: Int) = record("adjustTimer", seconds)
}
