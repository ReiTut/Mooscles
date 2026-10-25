package com.mooscles.domain

import com.mooscles.data.*

/** Independent pre-optimization PR algorithm from v1.4.5 (4045bb8), retained as a test oracle. */
internal object LegacyPrOracle {
    private val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
    private data class Key(val exerciseId: String, val convention: LoadConvention)
    private data class Candidate(val exercise: WorkoutExercise, val set: WorkoutSet) {
        val assistance get() = set.assistance || exercise.convention == LoadConvention.ASSISTANCE
        val key get() = Key(exercise.exerciseId, exercise.convention)
    }
    private fun completed(set: WorkoutSet) = set.completed && set.reps > 0 && set.weightKg.isFinite() && set.weightKg >= 0.0
    private fun working(set: WorkoutSet, convention: LoadConvention) = completed(set) && !set.warmup &&
        !set.assistance && convention != LoadConvention.ASSISTANCE && set.weightKg > 0.0
    private fun sameLoad(a: Double, b: Double) = kotlin.math.abs(a - b) <= maxOf(Math.ulp(a), Math.ulp(b)) * 4
    private fun comparable(a: Candidate, b: Candidate) = a.key == b.key &&
        a.assistance == b.assistance && sameLoad(a.set.weightKg, b.set.weightKg)
    private fun earlier(data: AppData, target: Workout) = data.workouts.filter {
        it.status == WorkoutStatus.FINISHED && it.id != target.id && chronology.compare(it, target) < 0
    }.map { it.id }.toSet()
    private fun bestReps(data: AppData, entries: List<WorkoutExercise>): List<Candidate> {
        val best = mutableListOf<Candidate>()
        entries.forEach { exercise ->
            data.exerciseSets(exercise.id).filter { completed(it) && !it.warmup }.forEach { set ->
                val candidate = Candidate(exercise, set)
                val found = best.indexOfFirst { comparable(it, candidate) }
                if (found < 0) best += candidate
                else if (set.reps > best[found].set.reps) best[found] = candidate
            }
        }
        return best
    }

    fun weights(data: AppData, workoutId: String): List<WeightPr> {
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = earlier(data, target)
        val baseline = mutableMapOf<Key, Double>()
        data.workoutExercises.filter { it.workoutId in earlier }.forEach { exercise ->
            val best = data.exerciseSets(exercise.id).filter { working(it, exercise.convention) }.maxOfOrNull { it.weightKg }
            if (best != null) {
                val key = Key(exercise.exerciseId, exercise.convention)
                baseline[key] = maxOf(baseline[key] ?: 0.0, best)
            }
        }
        return data.workoutItems(workoutId).groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, entries) ->
            val candidates = entries.flatMap { entry -> data.exerciseSets(entry.id).filter { working(it, entry.convention) } }
            val best = candidates.maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps }) ?: return@mapNotNull null
            val previous = baseline[key]
            val heavier = previous == null || best.weightKg > previous &&
                best.weightKg - previous > maxOf(Math.ulp(best.weightKg), Math.ulp(previous)) * 4
            if (!heavier) null else WeightPr(key.exerciseId, entries.first().name, key.convention,
                previous, best.weightKg, best.reps, previous == null)
        }
    }

    fun repetitions(data: AppData, workoutId: String): List<RepsPr> {
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = earlier(data, target)
        val baseline = bestReps(data, data.workoutExercises.filter { it.workoutId in earlier })
        return bestReps(data, data.workoutItems(workoutId)).mapNotNull { candidate ->
            val previous = baseline.firstOrNull { comparable(it, candidate) }?.set?.reps
            if (previous != null && candidate.set.reps <= previous) null
            else RepsPr(candidate.exercise.exerciseId, candidate.exercise.name, candidate.exercise.convention,
                candidate.set.weightKg, candidate.assistance, previous, candidate.set.reps, previous == null)
        }
    }

    fun weightRecords(data: AppData): List<ExerciseRecord> {
        val result = mutableMapOf<Key, ExerciseRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            data.workoutItems(workout.id).forEach { exercise ->
                val best = data.exerciseSets(exercise.id).filter { working(it, exercise.convention) }
                    .maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps })
                if (best != null) {
                    val key = Key(exercise.exerciseId, exercise.convention)
                    val previous = result[key]
                    if (previous == null || best.weightKg > previous.weightKg &&
                        best.weightKg - previous.weightKg > maxOf(Math.ulp(best.weightKg), Math.ulp(previous.weightKg)) * 4) {
                        result[key] = ExerciseRecord(exercise.exerciseId, exercise.name, exercise.convention,
                            best.weightKg, best.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    }
                }
            }
        }
        return result.values.sortedWith(compareBy<ExerciseRecord> { it.exerciseName }.thenBy { it.convention.name })
    }

    fun repetitionRecords(data: AppData): List<ExerciseRepsRecord> {
        val result = mutableListOf<ExerciseRepsRecord>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            bestReps(data, data.workoutItems(workout.id)).forEach { candidate ->
                val found = result.indexOfFirst { it.exerciseId == candidate.exercise.exerciseId &&
                    it.convention == candidate.exercise.convention && it.assistance == candidate.assistance &&
                    sameLoad(it.weightKg, candidate.set.weightKg) }
                if (found < 0 || candidate.set.reps > result[found].reps) {
                    val record = ExerciseRepsRecord(candidate.exercise.exerciseId, candidate.exercise.name,
                        candidate.exercise.convention, candidate.set.weightKg, candidate.assistance,
                        candidate.set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (found < 0) result += record else result[found] = record
                }
            }
        }
        return result.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
            .thenBy { it.assistance }.thenBy { it.weightKg })
    }

    fun history(data: AppData, exerciseId: String, convention: LoadConvention?): List<ExercisePerformance> {
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }
        return data.workoutExercises.filter { it.exerciseId == exerciseId && it.workoutId in finished &&
            (convention == null || convention == it.convention) }.map { exercise ->
            val sets = data.exerciseSets(exercise.id).filter(::completed)
            ExercisePerformance(finished.getValue(exercise.workoutId), exercise, sets, sets.sumOf {
                if (!it.assistance && exercise.convention != LoadConvention.ASSISTANCE) it.weightKg * it.reps else 0.0
            })
        }.filter { it.sets.isNotEmpty() }.sortedWith(compareByDescending<ExercisePerformance> { it.workout.startedAt }.thenBy { it.exercise.position })
    }
}
