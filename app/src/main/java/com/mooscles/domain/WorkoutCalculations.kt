package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus

object WeightUnits {
    const val POUNDS_PER_KG = 2.2046226218487757
    fun kgToLb(kg: Double) = kg * POUNDS_PER_KG
    fun lbToKg(lb: Double) = lb / POUNDS_PER_KG
    fun toKg(value: Double, pounds: Boolean) = if (pounds) lbToKg(value) else value
    fun fromKg(value: Double, pounds: Boolean) = if (pounds) kgToLb(value) else value
}

data class WeightPr(
    val exerciseId: String,
    val exerciseName: String,
    val convention: LoadConvention,
    val previousWeightKg: Double?,
    val newWeightKg: Double,
    val reps: Int,
    val firstRecord: Boolean
)

data class ExerciseRecord(
    val exerciseId: String,
    val exerciseName: String,
    val convention: LoadConvention,
    val weightKg: Double,
    val reps: Int,
    val workoutId: String,
    val achievedAt: Long
)

data class ExerciseSummary(
    val exercise: WorkoutExercise,
    val completedSets: List<WorkoutSet>,
    val volumeKg: Double,
    val pr: WeightPr?
)

data class WorkoutSummary(
    val workout: Workout,
    val exercises: List<ExerciseSummary>,
    val completedExerciseCount: Int,
    val completedSetCount: Int,
    val volumeKg: Double,
    /** Records beaten; initial positive records are deliberately separate. */
    val prs: List<WeightPr>,
    val firstRecords: List<WeightPr>,
    val durationMillis: Long
)

data class ExercisePerformance(
    val workout: Workout,
    val exercise: WorkoutExercise,
    val sets: List<WorkoutSet>,
    val volumeKg: Double
)

/** All derived values are recomputed from saved sets, so edits/deletions cannot leave stale PRs. */
object WorkoutCalculations {
    private val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }

    fun validCompleted(set: WorkoutSet): Boolean = set.completed && set.reps > 0 &&
        set.weightKg.isFinite() && set.weightKg >= 0.0

    fun volume(set: WorkoutSet, convention: LoadConvention): Double =
        if (validCompleted(set) && !set.assistance && convention != LoadConvention.ASSISTANCE)
            set.weightKg * set.reps else 0.0

    private fun workingSet(set: WorkoutSet, convention: LoadConvention): Boolean =
        validCompleted(set) && !set.warmup && !set.assistance &&
            convention != LoadConvention.ASSISTANCE && set.weightKg > 0.0

    // Ignore only binary floating-point round-trip noise, not a fixed decimal load increment.
    private fun heavier(value: Double, baseline: Double): Boolean = value > baseline &&
        value - baseline > maxOf(Math.ulp(value), Math.ulp(baseline)) * 4

    private data class Key(val exerciseId: String, val convention: LoadConvention)

    fun prsForWorkout(data: AppData, workoutId: String): List<WeightPr> {
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = data.workouts.filter { it.status == WorkoutStatus.FINISHED &&
            it.id != target.id && chronology.compare(it, target) < 0 }.map { it.id }.toSet()
        val baseline = mutableMapOf<Key, Double>()
        data.workoutExercises.filter { it.workoutId in earlier }.forEach { exercise ->
            val best = data.exerciseSets(exercise.id).filter { workingSet(it, exercise.convention) }.maxOfOrNull { it.weightKg }
            if (best != null) {
                val key = Key(exercise.exerciseId, exercise.convention)
                baseline[key] = maxOf(baseline[key] ?: 0.0, best)
            }
        }
        return data.workoutItems(workoutId).groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, entries) ->
            val candidates = entries.flatMap { entry -> data.exerciseSets(entry.id).filter { workingSet(it, entry.convention) } }
            val best = candidates.maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps }) ?: return@mapNotNull null
            val previous = baseline[key]
            if (previous != null && !heavier(best.weightKg, previous)) return@mapNotNull null
            WeightPr(key.exerciseId, entries.first().name, key.convention, previous, best.weightKg, best.reps, previous == null)
        }
    }

    fun summary(data: AppData, workoutId: String, now: Long = System.currentTimeMillis()): WorkoutSummary? {
        val workout = data.workouts.firstOrNull { it.id == workoutId } ?: return null
        val records = prsForWorkout(data, workoutId)
        val exercises = data.workoutItems(workoutId).map { exercise ->
            val completed = data.exerciseSets(exercise.id).filter(::validCompleted)
            ExerciseSummary(exercise, completed, completed.sumOf { volume(it, exercise.convention) },
                records.firstOrNull { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention })
        }
        return WorkoutSummary(
            workout, exercises, exercises.count { it.completedSets.isNotEmpty() }, exercises.sumOf { it.completedSets.size },
            exercises.sumOf { it.volumeKg }, records.filterNot { it.firstRecord }, records.filter { it.firstRecord },
            ((workout.endedAt ?: now) - workout.startedAt).coerceAtLeast(0L)
        )
    }

    fun records(data: AppData): List<ExerciseRecord> {
        val result = mutableMapOf<Key, ExerciseRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            data.workoutItems(workout.id).forEach { exercise ->
                val best = data.exerciseSets(exercise.id).filter { workingSet(it, exercise.convention) }
                    .maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps })
                if (best != null) {
                    val key = Key(exercise.exerciseId, exercise.convention)
                    val previous = result[key]
                    if (previous == null || heavier(best.weightKg, previous.weightKg)) {
                        result[key] = ExerciseRecord(exercise.exerciseId, exercise.name, exercise.convention,
                            best.weightKg, best.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    }
                }
            }
        }
        return result.values.sortedWith(compareBy<ExerciseRecord> { it.exerciseName }.thenBy { it.convention.name })
    }

    fun performanceHistory(data: AppData, exerciseId: String, convention: LoadConvention? = null): List<ExercisePerformance> {
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }
        return data.workoutExercises.filter { it.exerciseId == exerciseId && it.workoutId in finished &&
            (convention == null || convention == it.convention) }.map { exercise ->
            val sets = data.exerciseSets(exercise.id).filter(::validCompleted)
            ExercisePerformance(finished.getValue(exercise.workoutId), exercise, sets, sets.sumOf { volume(it, exercise.convention) })
        }.filter { it.sets.isNotEmpty() }.sortedWith(compareByDescending<ExercisePerformance> { it.workout.startedAt }.thenBy { it.exercise.position })
    }
}
