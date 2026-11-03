package com.mooscles.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mooscles.domain.RestDuration
import com.mooscles.domain.currentTimeMillis

enum class MuscleGroup(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"),
    TRICEPS("Triceps"), QUADRICEPS("Quadriceps"), HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"), CALVES("Calves"), CORE("Core"), FOREARMS("Forearms"), FULL_BODY("Full body");

    companion object {
        /** The group whose [label] (what `ExerciseUi` carries) or enum name is [value]; null for anything else. */
        fun fromLabelOrName(value: String): MuscleGroup? = entries.firstOrNull { it.label == value || it.name == value }
    }
}

enum class Equipment(val label: String) {
    BARBELL("Barbell"), DUMBBELL("Dumbbell"), KETTLEBELL("Kettlebell"), CABLE("Cable"),
    BODYWEIGHT("Bodyweight"), SMITH_MACHINE("Smith machine"),
    SELECTORIZED_MACHINE("Selectorized machine"), PLATE_LOADED_MACHINE("Plate-loaded machine"), OTHER("Other");

    companion object {
        /** The equipment whose [label] (what `ExerciseUi` carries) or enum name is [value]; null for anything else. */
        fun fromLabelOrName(value: String): Equipment? = entries.firstOrNull { it.label == value || it.name == value }
    }
}

/** Repetitions and load are always the numbers explicitly logged; no hidden multiplier. */
enum class LoadConvention(val label: String, val explanation: String) {
    TOTAL_LOAD("Total load", "Log the total external load; volume = logged load × reps."),
    PER_HAND("Per hand", "Log one dumbbell's load and reps. Volume is per hand; it is never doubled."),
    ONE_SIDE("One side", "Log total external load used while working one side and reps for that side. Volume is per side; it is never doubled."),
    BODYWEIGHT("Added weight", "Leave weight blank for bodyweight, or log added external weight. Bodyweight is excluded from weight moved."),
    ASSISTANCE("Assistance", "Log assistance separately. Assistance does not earn external-weight PRs or contribute to weight moved.");

    companion object {
        /** The convention whose [label] (what `ExerciseUi` carries) or enum name is [value]; null for anything else. */
        fun fromLabelOrName(value: String): LoadConvention? = entries.firstOrNull { it.label == value || it.name == value }
    }
}

enum class WorkoutStatus { ACTIVE, FINISHED }

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val muscle: MuscleGroup,
    val equipment: Equipment,
    val convention: LoadConvention = LoadConvention.TOTAL_LOAD,
    val custom: Boolean = false
)

@Entity(tableName = "routine_folders")
data class RoutineFolder(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val position: Int = 0
) {
    companion object {
        const val DEFAULT_ID = "default"
        const val DEFAULT_NAME = "My routines"
    }
}

// Folder commands validate membership. Keeping this additive avoids rebuilding the routine
// tables and their cascading prescription rows when upgrading an existing workout database.
@Entity(tableName = "routines", indices = [Index("folderId")])
data class Routine(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val position: Int = 0,
    @ColumnInfo(defaultValue = "'default'") val folderId: String = RoutineFolder.DEFAULT_ID
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("routineId"), Index("exerciseId")]
)
data class RoutineExercise(
    @PrimaryKey val id: String = newId(),
    val routineId: String,
    val exerciseId: String,
    val position: Int = 0,
    val plannedSets: Int = 2,
    val targetReps: Int = 0,
    val targetWeightKg: Double? = null,
    val restSeconds: Int? = null
)

/**
 * One independently saved prescription. Null load and zero reps are explicitly blank values.
 * [warmup] (version 6) plans a warm-up set; rows saved before it read as working sets.
 */
@Entity(
    tableName = "routine_sets",
    foreignKeys = [ForeignKey(entity = RoutineExercise::class, parentColumns = ["id"],
        childColumns = ["routineExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineExerciseId")]
)
data class RoutineSet(
    @PrimaryKey val id: String = newId(),
    val routineExerciseId: String,
    val position: Int = 0,
    val targetReps: Int = 0,
    val targetWeightKg: Double? = null,
    val inheritPr: Boolean = true,
    @ColumnInfo(defaultValue = "0") val warmup: Boolean = false
)

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val startedAt: Long = currentTimeMillis(),
    val endedAt: Long? = null,
    val status: WorkoutStatus = WorkoutStatus.ACTIVE,
    /** Stable origin only; deleting or renaming a routine must never alter this snapshot. */
    val sourceRoutineId: String? = null
)

/** Immutable exercise metadata is copied here when a workout starts. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutExercise(
    @PrimaryKey val id: String = newId(),
    val workoutId: String,
    val exerciseId: String,
    val name: String,
    val muscle: MuscleGroup,
    val equipment: Equipment,
    val convention: LoadConvention,
    val position: Int = 0,
    val restSeconds: Int = RestDuration.DEFAULT_SECONDS
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = WorkoutExercise::class, parentColumns = ["id"], childColumns = ["workoutExerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("workoutExerciseId")]
)
data class WorkoutSet(
    @PrimaryKey val id: String = newId(),
    val workoutExerciseId: String,
    val position: Int = 0,
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val completed: Boolean = false,
    val warmup: Boolean = false,
    val assistance: Boolean = false
)

data class AppData(
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val routineExercises: List<RoutineExercise> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val workoutExercises: List<WorkoutExercise> = emptyList(),
    val sets: List<WorkoutSet> = emptyList(),
    val routineSets: List<RoutineSet> = emptyList(),
    val routineFolders: List<RoutineFolder> = emptyList()
) {
    val activeWorkout: Workout? get() = workouts.firstOrNull { it.status == WorkoutStatus.ACTIVE }
    fun routineItems(routineId: String) = routineExercises.filter { it.routineId == routineId }.sortedBy { it.position }
    fun workoutItems(workoutId: String) = workoutExercises.filter { it.workoutId == workoutId }.sortedBy { it.position }
    fun exerciseSets(workoutExerciseId: String) = sets.filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.position }
    fun plannedSets(routineExerciseId: String) = routineSets.filter { it.routineExerciseId == routineExerciseId }.sortedBy { it.position }
}
