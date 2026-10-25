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

/** All derived values come from saved sets; reusable analyses are scoped to a value snapshot. */
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

    internal data class WorkoutPrs(val weights: List<WeightPr>, val repetitions: List<RepsPr>) {
        companion object { val EMPTY = WorkoutPrs(emptyList(), emptyList()) }
    }

    private data class Candidates(
        val weights: Map<Key, Pair<WorkoutExercise, WorkoutSet>>,
        val repetitions: List<RepsCandidate>
    ) {
        val empty get() = weights.isEmpty() && repetitions.isEmpty()
    }

    private fun candidates(index: SnapshotIndex, entries: List<WorkoutExercise>): Candidates {
        val weights = linkedMapOf<Key, Pair<WorkoutExercise, WorkoutSet>>()
        val firstEntries = linkedMapOf<Key, WorkoutExercise>()
        entries.forEach { exercise ->
            val key = Key(exercise.exerciseId, exercise.convention)
            firstEntries.putIfAbsent(key, exercise)
            index.sets(exercise.id).forEach { set ->
                if (workingSet(set, exercise.convention)) {
                    val previous = weights[key]
                    if (previous == null) weights[key] = firstEntries.getValue(key) to set
                    else if (set.weightKg > previous.second.weightKg ||
                        (set.weightKg == previous.second.weightKg && set.reps > previous.second.reps)) {
                        // Retain the first entry's name, as the original grouped calculation did.
                        weights[key] = previous.first to set
                    }
                }
            }
        }
        val orderedWeights = firstEntries.keys.mapNotNull { key -> weights[key]?.let { key to it } }.toMap()
        return Candidates(orderedWeights, bestRepetitionSets(index, entries))
    }

    private class Baseline(
        val weights: MutableMap<Key, Double> = mutableMapOf(),
        val repetitions: MutableList<RepsCandidate> = mutableListOf()
    )

    private fun baseline(index: SnapshotIndex, target: Workout): Baseline {
        val earlier = earlierWorkoutIds(index.data, target)
        val entries = index.data.workoutExercises.filter { it.workoutId in earlier }
        val result = Baseline(repetitions = bestRepetitionSets(index, entries).toMutableList())
        entries.forEach { exercise ->
            index.sets(exercise.id).forEach { set ->
                if (workingSet(set, exercise.convention)) {
                    val key = Key(exercise.exerciseId, exercise.convention)
                    result.weights[key] = maxOf(result.weights[key] ?: 0.0, set.weightKg)
                }
            }
        }
        return result
    }

    private fun achievements(current: Candidates, previous: Baseline): WorkoutPrs = WorkoutPrs(
        current.weights.mapNotNull { (key, candidate) ->
            val (exercise, best) = candidate
            val old = previous.weights[key]
            if (old != null && !heavier(best.weightKg, old)) null
            else WeightPr(key.exerciseId, exercise.name, key.convention, old, best.weightKg, best.reps, old == null)
        },
        current.repetitions.mapNotNull { candidate ->
            val old = previous.repetitions.firstOrNull { comparable(it, candidate) }?.set?.reps
            if (old != null && candidate.set.reps <= old) null
            else RepsPr(candidate.exercise.exerciseId, candidate.exercise.name, candidate.exercise.convention,
                candidate.set.weightKg, candidate.assistance, old, candidate.set.reps, old == null)
        }
    )

    private fun achievements(index: SnapshotIndex, workoutId: String): WorkoutPrs {
        val target = index.data.workouts.firstOrNull { it.id == workoutId } ?: return WorkoutPrs.EMPTY
        val current = candidates(index, index.entries(workoutId))
        // Unfinished, warm-up and invalid sets need no historical scan. Zero-load/assisted
        // working sets still qualify for repetition records.
        if (current.empty) return WorkoutPrs.EMPTY
        return achievements(current, baseline(index, target))
    }

    /**
     * Sequential-owner cache, created only when finished row values change. It copies lists:
     * a caller mutating an AppData list cannot silently mutate this analysis's cache key/data.
     * Baselines are lazy; merely opening a workout with no completed working sets never asks
     * for historical PR calculations. No stored weights are rounded or converted here.
     */
    internal class HistoricalAnalysis private constructor(private val index: SnapshotIndex) {
        internal var baselineBuildCount = 0
            private set
        private val finished = index.data.workouts.sortedWith(chronology)
        private var allAchievements: Map<String, WorkoutPrs>? = null
        private var finalBaseline: Baseline? = null
        private var activeTarget: Workout? = null
        private var activeBaseline: Baseline? = null
        private val histories = mutableMapOf<Pair<String, LoadConvention?>, List<ExercisePerformance>>()
        private val weightRecords by lazy { WorkoutCalculations.records(index) }
        private val repetitionRecords by lazy { WorkoutCalculations.repsRecords(index) }

        fun entries(workoutId: String): List<WorkoutExercise> = index.entries(workoutId)
        fun sets(exerciseId: String): List<WorkoutSet> = index.sets(exerciseId)
        fun records(): List<ExerciseRecord> = weightRecords
        fun repsRecords(): List<ExerciseRepsRecord> = repetitionRecords
        fun performanceHistory(exerciseId: String, convention: LoadConvention? = null): List<ExercisePerformance> =
            histories.getOrPut(exerciseId to convention) { WorkoutCalculations.performanceHistory(index, exerciseId, convention) }

        fun prsForWorkout(workoutId: String): WorkoutPrs {
            if (allAchievements == null) prepareHistory()
            return allAchievements?.get(workoutId) ?: WorkoutPrs.EMPTY
        }

        fun prsForActive(workout: Workout, entries: List<WorkoutExercise>, sets: List<WorkoutSet>): WorkoutPrs {
            val currentIndex = SnapshotIndex(AppData(workoutExercises = entries, sets = sets))
            val current = candidates(currentIndex, currentIndex.entries(workout.id))
            if (current.empty) return WorkoutPrs.EMPTY
            if (activeTarget != workout || activeBaseline == null) {
                activeTarget = workout
                // Usually the active workout follows every finished workout. Reuse the final
                // shared baseline when available; backdated/overlapping sessions use their cutoff.
                activeBaseline = finalBaseline?.takeIf { finished.lastOrNull()?.let { chronology.compare(it, workout) < 0 } != false }
                    ?: buildBaseline(workout)
            }
            return achievements(current, activeBaseline!!)
        }

        private fun buildBaseline(target: Workout): Baseline {
            baselineBuildCount++
            return baseline(index, target)
        }

        /**
         * Ordinary load groups are equivalence classes, so one chronological pass can retain
         * each group's best reps and original storage-order tie breaking. Four-ULP comparison
         * is deliberately not globally transitive: for an unusual A~B~C but A!~C chain, use
         * the legacy order-sensitive baseline instead of changing an existing achievement.
         */
        private fun prepareHistory() {
            val ordered = index.data.workoutExercises.flatMap { exercise ->
                index.sets(exercise.id).filter(::repetitionSet).map { RepsCandidate(exercise, it) }
            }
            val ambiguous = ordered.groupBy { it.key to it.assistance }.values.any { rows ->
                val loads = rows.map { it.set.weightKg }.distinct().sorted()
                var first = loads.firstOrNull() ?: 0.0
                loads.zipWithNext().any { (left, right) ->
                    if (!sameLoad(left, right)) first = right
                    !sameLoad(first, right)
                }
            }
            if (ambiguous) {
                allAchievements = finished.associate { workout ->
                    val current = candidates(index, index.entries(workout.id))
                    workout.id to if (current.empty) WorkoutPrs.EMPTY else achievements(current, buildBaseline(workout))
                }
                return
            }
            baselineBuildCount++
            val result = linkedMapOf<String, WorkoutPrs>()
            val shared = Baseline()
            // Original flattened rank preserves baseline grouping/ties even when rows are
            // stored newest-first or exercise positions differ between saved workouts.
            val rank = ordered.withIndex().associate { it.value.set.id to it.index }
            val firstRank = mutableMapOf<Int, Int>()
            finished.forEach { workout ->
                val current = candidates(index, index.entries(workout.id))
                result[workout.id] = if (current.empty) WorkoutPrs.EMPTY else achievements(current, shared)
                index.entries(workout.id).forEach { exercise ->
                    index.sets(exercise.id).forEach { set ->
                        if (workingSet(set, exercise.convention)) {
                            val key = Key(exercise.exerciseId, exercise.convention)
                            shared.weights[key] = maxOf(shared.weights[key] ?: 0.0, set.weightKg)
                        }
                        if (repetitionSet(set)) {
                            val candidate = RepsCandidate(exercise, set)
                            val found = shared.repetitions.indexOfFirst { comparable(it, candidate) }
                            if (found < 0) {
                                shared.repetitions += candidate
                                firstRank[shared.repetitions.lastIndex] = rank.getValue(set.id)
                            } else {
                                val previous = shared.repetitions[found]
                                firstRank[found] = minOf(firstRank.getValue(found), rank.getValue(set.id))
                                if (set.reps > previous.set.reps ||
                                    (set.reps == previous.set.reps && rank.getValue(set.id) < rank.getValue(previous.set.id))) {
                                    shared.repetitions[found] = candidate
                                }
                            }
                        }
                    }
                }
                // Nonoverlapping groups are distinct; iteration order only matters for a
                // new active load between groups, so sort once after all history is ready.
            }
            val sorted = shared.repetitions.withIndex().sortedBy { firstRank.getValue(it.index) }.map { it.value }
            finalBaseline = Baseline(shared.weights, sorted.toMutableList())
            allAchievements = result
        }

        companion object {
            fun from(data: AppData): HistoricalAnalysis {
                val workouts = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
                val ids = workouts.mapTo(hashSetOf()) { it.id }
                val entries = data.workoutExercises.filter { it.workoutId in ids }
                val entryIds = entries.mapTo(hashSetOf()) { it.id }
                return HistoricalAnalysis(SnapshotIndex(AppData(workouts = workouts,
                    workoutExercises = entries, sets = data.sets.filter { it.workoutExerciseId in entryIds })))
            }
        }
    }

    internal fun historicalAnalysis(data: AppData): HistoricalAnalysis = HistoricalAnalysis.from(data)

    fun prsForWorkout(data: AppData, workoutId: String): List<WeightPr> =
        prsForWorkout(SnapshotIndex(data), workoutId)

    private fun prsForWorkout(index: SnapshotIndex, workoutId: String): List<WeightPr> {
        return achievements(index, workoutId).weights
    }

    /** Repetition records are independent for each variation, assistance mode, and external load. */
    fun repsPrsForWorkout(data: AppData, workoutId: String): List<RepsPr> =
        repsPrsForWorkout(SnapshotIndex(data), workoutId)

    private fun repsPrsForWorkout(index: SnapshotIndex, workoutId: String): List<RepsPr> {
        return achievements(index, workoutId).repetitions
    }

    fun summary(data: AppData, workoutId: String, now: Long = System.currentTimeMillis()): WorkoutSummary? {
        val index = SnapshotIndex(data)
        val workout = data.workouts.firstOrNull { it.id == workoutId } ?: return null
        val achievements = achievements(index, workoutId)
        val records = achievements.weights
        val repetitions = achievements.repetitions
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

    fun records(data: AppData): List<ExerciseRecord> = records(SnapshotIndex(data))

    private fun records(index: SnapshotIndex): List<ExerciseRecord> {
        val data = index.data
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

    fun repsRecords(data: AppData): List<ExerciseRepsRecord> = repsRecords(SnapshotIndex(data))

    private fun repsRecords(snapshot: SnapshotIndex): List<ExerciseRepsRecord> {
        val data = snapshot.data
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

    fun performanceHistory(data: AppData, exerciseId: String, convention: LoadConvention? = null): List<ExercisePerformance> =
        performanceHistory(SnapshotIndex(data), exerciseId, convention)

    private fun performanceHistory(index: SnapshotIndex, exerciseId: String, convention: LoadConvention?): List<ExercisePerformance> {
        val data = index.data
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }
        return data.workoutExercises.filter { it.exerciseId == exerciseId && it.workoutId in finished &&
            (convention == null || convention == it.convention) }.map { exercise ->
            val sets = index.sets(exercise.id).filter(::validCompleted)
            ExercisePerformance(finished.getValue(exercise.workoutId), exercise, sets, sets.sumOf { volume(it, exercise.convention) })
        }.filter { it.sets.isNotEmpty() }.sortedWith(compareByDescending<ExercisePerformance> { it.workout.startedAt }.thenBy { it.exercise.position })
    }
}
