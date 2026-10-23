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

data class RepsPr(
    val exerciseId: String,
    val exerciseName: String,
    val convention: LoadConvention,
    /** External load, or the assistance setting when [assistance] is true. */
    val weightKg: Double,
    val assistance: Boolean,
    val previousReps: Int?,
    val newReps: Int,
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

data class ExerciseRepsRecord(
    val exerciseId: String,
    val exerciseName: String,
    val convention: LoadConvention,
    val weightKg: Double,
    val assistance: Boolean,
    val reps: Int,
    val workoutId: String,
    val achievedAt: Long
)

data class ExerciseSummary(
    val exercise: WorkoutExercise,
    val completedSets: List<WorkoutSet>,
    val volumeKg: Double,
    val pr: WeightPr?,
    val repsPrs: List<RepsPr> = emptyList()
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
    val durationMillis: Long,
    val repsPrs: List<RepsPr> = emptyList(),
    val firstRepsRecords: List<RepsPr> = emptyList()
) {
    /** Count each exercise/convention once across first records and records beaten, of either type. */
    val prExerciseCount: Int get() = (prs.map { it.exerciseId to it.convention } +
        repsPrs.map { it.exerciseId to it.convention } +
        firstRecords.map { it.exerciseId to it.convention } +
        firstRepsRecords.map { it.exerciseId to it.convention }).distinct().size

    /** Records beaten exclude initial records, while still counting an exercise only once. */
    val beatenPrExerciseCount: Int get() = (prs.map { it.exerciseId to it.convention } +
        repsPrs.map { it.exerciseId to it.convention }).distinct().size

    val firstRecordExerciseCount: Int get() = (firstRecords.map { it.exerciseId to it.convention } +
        firstRepsRecords.map { it.exerciseId to it.convention }).distinct().size
}

data class ExercisePerformance(
    val workout: Workout,
    val exercise: WorkoutExercise,
    val sets: List<WorkoutSet>,
    val volumeKg: Double
)

/** All derived values are recomputed from saved sets, so edits/deletions cannot leave stale PRs. */
object WorkoutCalculations {
    private val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }

    /** Local to one calculation; never caches a caller-owned mutable AppData list across calls. */
    private class SnapshotIndex(val data: AppData) {
        private val setsByExercise = data.sets.groupBy { it.workoutExerciseId }
            .mapValues { (_, rows) -> rows.sortedBy { it.position } }
        private val entriesByWorkout = data.workoutExercises.groupBy { it.workoutId }
            .mapValues { (_, rows) -> rows.sortedBy { it.position } }

        fun sets(exerciseInstanceId: String): List<WorkoutSet> = setsByExercise[exerciseInstanceId].orEmpty()
        fun entries(workoutId: String): List<WorkoutExercise> = entriesByWorkout[workoutId].orEmpty()
    }

    fun validCompleted(set: WorkoutSet): Boolean = set.completed && set.reps > 0 &&
        set.weightKg.isFinite() && set.weightKg >= 0.0

    fun volume(set: WorkoutSet, convention: LoadConvention): Double =
        if (validCompleted(set) && !set.assistance && convention != LoadConvention.ASSISTANCE)
            set.weightKg * set.reps else 0.0

    private fun workingSet(set: WorkoutSet, convention: LoadConvention): Boolean =
        validCompleted(set) && !set.warmup && !set.assistance &&
            convention != LoadConvention.ASSISTANCE && set.weightKg > 0.0

    private fun repetitionSet(set: WorkoutSet): Boolean = validCompleted(set) && !set.warmup

    // Ignore only binary floating-point round-trip noise, not a fixed decimal load increment.
    private fun heavier(value: Double, baseline: Double): Boolean = value > baseline &&
        value - baseline > maxOf(Math.ulp(value), Math.ulp(baseline)) * 4

    private fun sameLoad(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= maxOf(Math.ulp(first), Math.ulp(second)) * 4

    private data class Key(val exerciseId: String, val convention: LoadConvention)

    private data class RepsCandidate(val exercise: WorkoutExercise, val set: WorkoutSet) {
        val assistance get() = set.assistance || exercise.convention == LoadConvention.ASSISTANCE
        val key get() = Key(exercise.exerciseId, exercise.convention)
    }

    private fun comparable(first: RepsCandidate, second: RepsCandidate): Boolean =
        first.key == second.key && first.assistance == second.assistance &&
            sameLoad(first.set.weightKg, second.set.weightKg)

    private fun bestRepetitionSets(index: SnapshotIndex, entries: List<WorkoutExercise>): List<RepsCandidate> {
        val best = mutableListOf<RepsCandidate>()
        entries.forEach { exercise ->
            index.sets(exercise.id).filter(::repetitionSet).forEach { set ->
                val candidate = RepsCandidate(exercise, set)
                val index = best.indexOfFirst { comparable(it, candidate) }
                if (index < 0) best += candidate
                else if (set.reps > best[index].set.reps) best[index] = candidate
            }
        }
        return best
    }

    private fun earlierWorkoutIds(data: AppData, target: Workout): Set<String> = data.workouts.filter {
        it.status == WorkoutStatus.FINISHED && it.id != target.id && chronology.compare(it, target) < 0
    }.map { it.id }.toSet()

    fun prsForWorkout(data: AppData, workoutId: String): List<WeightPr> =
        prsForWorkout(SnapshotIndex(data), workoutId)

    private fun prsForWorkout(index: SnapshotIndex, workoutId: String): List<WeightPr> {
        val data = index.data
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = earlierWorkoutIds(data, target)
        val baseline = mutableMapOf<Key, Double>()
        data.workoutExercises.filter { it.workoutId in earlier }.forEach { exercise ->
            val best = index.sets(exercise.id).filter { workingSet(it, exercise.convention) }.maxOfOrNull { it.weightKg }
            if (best != null) {
                val key = Key(exercise.exerciseId, exercise.convention)
                baseline[key] = maxOf(baseline[key] ?: 0.0, best)
            }
        }
        return index.entries(workoutId).groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, entries) ->
            val candidates = entries.flatMap { entry -> index.sets(entry.id).filter { workingSet(it, entry.convention) } }
            val best = candidates.maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps }) ?: return@mapNotNull null
            val previous = baseline[key]
            if (previous != null && !heavier(best.weightKg, previous)) return@mapNotNull null
            WeightPr(key.exerciseId, entries.first().name, key.convention, previous, best.weightKg, best.reps, previous == null)
        }
    }

    /** Repetition records are independent for each variation, assistance mode, and external load. */
    fun repsPrsForWorkout(data: AppData, workoutId: String): List<RepsPr> =
        repsPrsForWorkout(SnapshotIndex(data), workoutId)

    private fun repsPrsForWorkout(index: SnapshotIndex, workoutId: String): List<RepsPr> {
        val data = index.data
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = earlierWorkoutIds(data, target)
        val baseline = bestRepetitionSets(index, data.workoutExercises.filter { it.workoutId in earlier })
        return bestRepetitionSets(index, index.entries(workoutId)).mapNotNull { candidate ->
            val previous = baseline.firstOrNull { comparable(it, candidate) }?.set?.reps
            if (previous != null && candidate.set.reps <= previous) return@mapNotNull null
            RepsPr(candidate.exercise.exerciseId, candidate.exercise.name, candidate.exercise.convention,
                candidate.set.weightKg, candidate.assistance, previous, candidate.set.reps, previous == null)
        }
    }

    fun summary(data: AppData, workoutId: String, now: Long = System.currentTimeMillis()): WorkoutSummary? {
        val index = SnapshotIndex(data)
        val workout = data.workouts.firstOrNull { it.id == workoutId } ?: return null
        val records = prsForWorkout(index, workoutId)
        val repetitions = repsPrsForWorkout(index, workoutId)
        val exercises = index.entries(workoutId).map { exercise ->
            val completed = index.sets(exercise.id).filter(::validCompleted)
            ExerciseSummary(exercise, completed, completed.sumOf { volume(it, exercise.convention) },
                records.firstOrNull { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention },
                repetitions.filter { it.exerciseId == exercise.exerciseId && it.convention == exercise.convention })
        }
        return WorkoutSummary(
            workout, exercises, exercises.count { it.completedSets.isNotEmpty() }, exercises.sumOf { it.completedSets.size },
            exercises.sumOf { it.volumeKg }, records.filterNot { it.firstRecord }, records.filter { it.firstRecord },
            ((workout.endedAt ?: now) - workout.startedAt).coerceAtLeast(0L),
            repetitions.filterNot { it.firstRecord }, repetitions.filter { it.firstRecord }
        )
    }

    fun records(data: AppData): List<ExerciseRecord> {
        val index = SnapshotIndex(data)
        val result = mutableMapOf<Key, ExerciseRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            index.entries(workout.id).forEach { exercise ->
                val best = index.sets(exercise.id).filter { workingSet(it, exercise.convention) }
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

    fun repsRecords(data: AppData): List<ExerciseRepsRecord> {
        val snapshot = SnapshotIndex(data)
        val result = mutableListOf<ExerciseRepsRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            bestRepetitionSets(snapshot, snapshot.entries(workout.id)).forEach { candidate ->
                val index = result.indexOfFirst { it.exerciseId == candidate.exercise.exerciseId &&
                    it.convention == candidate.exercise.convention && it.assistance == candidate.assistance &&
                    sameLoad(it.weightKg, candidate.set.weightKg) }
                if (index < 0 || candidate.set.reps > result[index].reps) {
                    val record = ExerciseRepsRecord(candidate.exercise.exerciseId, candidate.exercise.name,
                        candidate.exercise.convention, candidate.set.weightKg, candidate.assistance,
                        candidate.set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (index < 0) result += record else result[index] = record
                }
            }
        }
        return result.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
            .thenBy { it.assistance }.thenBy { it.weightKg })
    }

    fun performanceHistory(data: AppData, exerciseId: String, convention: LoadConvention? = null): List<ExercisePerformance> {
        val index = SnapshotIndex(data)
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }
        return data.workoutExercises.filter { it.exerciseId == exerciseId && it.workoutId in finished &&
            (convention == null || convention == it.convention) }.map { exercise ->
            val sets = index.sets(exercise.id).filter(::validCompleted)
            ExercisePerformance(finished.getValue(exercise.workoutId), exercise, sets, sets.sumOf { volume(it, exercise.convention) })
        }.filter { it.sets.isNotEmpty() }.sortedWith(compareByDescending<ExercisePerformance> { it.workout.startedAt }.thenBy { it.exercise.position })
    }
}
