package com.mooscles.backup

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.Exercise
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Routine
import com.mooscles.data.RoutineExercise
import com.mooscles.data.RoutineFolder
import com.mooscles.data.RoutineSet
import com.mooscles.data.RoutineTargetCheckpoint
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus

data class BackupEntities(
    val exercises: List<Exercise>,
    val routineFolders: List<RoutineFolder>,
    val routines: List<Routine>,
    val routineExercises: List<RoutineExercise>,
    val routineSets: List<RoutineSet>,
    val workouts: List<Workout>,
    val workoutExercises: List<WorkoutExercise>,
    val workoutSets: List<WorkoutSet>,
    val checkpoints: List<RoutineTargetCheckpoint>,
)

fun BackupData.Companion.from(data: AppData, checkpoints: List<RoutineTargetCheckpoint>): BackupData = BackupData(
    exercises = data.exercises.map { ExerciseRow(it.id, it.name, it.muscle.name, it.equipment.name, it.convention.name, it.custom) },
    routineFolders = data.routineFolders.map { RoutineFolderRow(it.id, it.name, it.position) },
    routines = data.routines.map { RoutineRow(it.id, it.name, it.position, it.folderId) },
    routineExercises = data.routineExercises.map {
        RoutineExerciseRow(it.id, it.routineId, it.exerciseId, it.position, it.plannedSets, it.targetReps, it.targetWeightKg, it.restSeconds)
    },
    routineSets = data.routineSets.map {
        RoutineSetRow(it.id, it.routineExerciseId, it.position, it.targetReps, it.targetWeightKg, it.inheritPr, it.warmup)
    },
    workouts = data.workouts.map { WorkoutRow(it.id, it.name, it.startedAt, it.endedAt, it.status.name, it.sourceRoutineId) },
    workoutExercises = data.workoutExercises.map {
        WorkoutExerciseRow(
            it.id, it.workoutId, it.exerciseId, it.name, it.muscle.name, it.equipment.name,
            it.convention.name, it.position, it.restSeconds,
        )
    },
    workoutSets = data.sets.map {
        WorkoutSetRow(it.id, it.workoutExerciseId, it.position, it.reps, it.weightKg, it.completed, it.warmup, it.assistance)
    },
    routineTargetCheckpoints = checkpoints.map { CheckpointRow(it.exerciseId, it.convention.name, it.weightKg, it.reps) },
)

private inline fun <reified E : Enum<E>> enumOf(field: String, value: String): E =
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw BackupException.Invalid("unknown $field \"$value\"")

fun BackupData.toEntities(): BackupEntities = BackupEntities(
    exercises = exercises.map {
        Exercise(
            it.id, it.name, enumOf<MuscleGroup>("muscle", it.muscle), enumOf<Equipment>("equipment", it.equipment),
            enumOf<LoadConvention>("convention", it.convention), it.custom,
        )
    },
    routineFolders = routineFolders.map { RoutineFolder(it.id, it.name, it.position) },
    routines = routines.map { Routine(it.id, it.name, it.position, it.folderId) },
    routineExercises = routineExercises.map {
        RoutineExercise(it.id, it.routineId, it.exerciseId, it.position, it.plannedSets, it.targetReps, it.targetWeightKg, it.restSeconds)
    },
    routineSets = routineSets.map {
        RoutineSet(it.id, it.routineExerciseId, it.position, it.targetReps, it.targetWeightKg, it.inheritPr, it.warmup)
    },
    workouts = workouts.map {
        Workout(it.id, it.name, it.startedAt, it.endedAt, enumOf<WorkoutStatus>("status", it.status), it.sourceRoutineId)
    },
    workoutExercises = workoutExercises.map {
        WorkoutExercise(
            it.id, it.workoutId, it.exerciseId, it.name, enumOf<MuscleGroup>("muscle", it.muscle),
            enumOf<Equipment>("equipment", it.equipment), enumOf<LoadConvention>("convention", it.convention),
            it.position, it.restSeconds,
        )
    },
    workoutSets = workoutSets.map {
        WorkoutSet(it.id, it.workoutExerciseId, it.position, it.reps, it.weightKg, it.completed, it.warmup, it.assistance)
    },
    checkpoints = routineTargetCheckpoints.map {
        RoutineTargetCheckpoint(it.exerciseId, enumOf<LoadConvention>("convention", it.convention), it.weightKg, it.reps)
    },
)
