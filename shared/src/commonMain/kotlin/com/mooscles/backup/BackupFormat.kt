package com.mooscles.backup

import kotlinx.serialization.Serializable

/*
 * Format contract. Adding an enum constant (muscle, equipment, convention, status, theme) or a required field
 * requires bumping BackupCodec.FORMAT_VERSION: an older app then reports "made by a newer version" instead of
 * "invalid data". Optional fields with defaults and unknown keys are ignored by older apps and need no bump.
 */
@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val exportedAt: String,
    val app: BackupApp,
    val preferences: BackupPreferences,
    val data: BackupData,
)

@Serializable
data class BackupApp(val versionName: String, val versionCode: Int, val platform: String)

@Serializable
data class BackupPreferences(
    val pounds: Boolean,
    val theme: String,
    val autoRest: Boolean,
    val defaultRestSeconds: Int,
    val selectedRoutineFolderId: String,
)

@Serializable
data class BackupData(
    val exercises: List<ExerciseRow>,
    val routineFolders: List<RoutineFolderRow>,
    val routines: List<RoutineRow>,
    val routineExercises: List<RoutineExerciseRow>,
    val routineSets: List<RoutineSetRow>,
    val workouts: List<WorkoutRow>,
    val workoutExercises: List<WorkoutExerciseRow>,
    val workoutSets: List<WorkoutSetRow>,
    val routineTargetCheckpoints: List<CheckpointRow>,
) {
    companion object
}

@Serializable
data class ExerciseRow(
    val id: String, val name: String, val muscle: String, val equipment: String,
    val convention: String, val custom: Boolean,
)

@Serializable
data class RoutineFolderRow(val id: String, val name: String, val position: Int)

@Serializable
data class RoutineRow(val id: String, val name: String, val position: Int, val folderId: String)

@Serializable
data class RoutineExerciseRow(
    val id: String, val routineId: String, val exerciseId: String, val position: Int,
    val plannedSets: Int, val targetReps: Int, val targetWeightKg: Double?, val restSeconds: Int?,
)

@Serializable
data class RoutineSetRow(
    val id: String, val routineExerciseId: String, val position: Int, val targetReps: Int,
    val targetWeightKg: Double?, val inheritPr: Boolean,
    /** Optional (schema 6): files without it are working sets, and older apps skip it. */
    val warmup: Boolean = false,
)

@Serializable
data class WorkoutRow(
    val id: String, val name: String, val startedAt: Long, val endedAt: Long?,
    val status: String, val sourceRoutineId: String?,
)

@Serializable
data class WorkoutExerciseRow(
    val id: String, val workoutId: String, val exerciseId: String, val name: String,
    val muscle: String, val equipment: String, val convention: String, val position: Int,
    val restSeconds: Int,
)

@Serializable
data class WorkoutSetRow(
    val id: String, val workoutExerciseId: String, val position: Int, val reps: Int,
    val weightKg: Double, val completed: Boolean, val warmup: Boolean, val assistance: Boolean,
)

@Serializable
data class CheckpointRow(
    val exerciseId: String, val convention: String, val weightKg: Double?, val reps: Int,
)
