package com.mooscles.domain

import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.math.ulp

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
    /** The heaviest load before this workout; null on the exercise's first session, which is a record too. */
    val previousWeightKg: Double?,
    val newWeightKg: Double,
    val reps: Int,
    /** The set holding [newWeightKg]: the heaviest, ties broken by more reps, then the first in workout order. */
    val setId: String
)

data class RepsPr(
    val exerciseId: String,
    val exerciseName: String,
    val convention: LoadConvention,
    /** External load, or the assistance setting when [assistance] is true. */
    val weightKg: Double,
    val assistance: Boolean,
    /**
     * The most reps before this workout at this load, which is the exercise's heaviest; null on the first session of an
     * exercise that has no load record to give (no added load, or assisted sets).
     */
    val previousReps: Int?,
    val newReps: Int,
    /** The set holding [newReps]: the first in workout order with that many reps at this load. */
    val setId: String
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
    val repsPrs: List<RepsPr> = emptyList(),
    /** The exercise's heaviest qualifying load at the end of this workout, with lighter reps slots excluded. */
    val recordWeightKg: Double? = null,
)

data class WorkoutSummary(
    val workout: Workout,
    val exercises: List<ExerciseSummary>,
    val completedExerciseCount: Int,
    val completedSetCount: Int,
    val volumeKg: Double,
    /** Records: an exercise's first session, then only records that beat an earlier value. */
    val prs: List<WeightPr>,
    val durationMillis: Long,
    val repsPrs: List<RepsPr> = emptyList(),
) {
    /** Count each exercise/convention once across records of either type. */
    val prExerciseCount: Int get() = (prs.map { it.exerciseId to it.convention } +
        repsPrs.map { it.exerciseId to it.convention }).distinct().size
}

/** Records of every finished workout, keyed by workout id; a workout without records maps to an empty list. */
data class HistoryRecords(
    val weight: Map<String, List<WeightPr>>,
    val reps: Map<String, List<RepsPr>>
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
        value - baseline > maxOf(value.ulp, baseline.ulp) * 4

    private fun sameLoad(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= maxOf(first.ulp, second.ulp) * 4

    private data class Key(val exerciseId: String, val convention: LoadConvention)

    private data class RepsCandidate(val exercise: WorkoutExercise, val set: WorkoutSet) {
        val assistance get() = set.assistance || exercise.convention == LoadConvention.ASSISTANCE
        val key get() = Key(exercise.exerciseId, exercise.convention)
    }

    /** Candidates of one exercise, convention and assistance mode; only these can share a repetition slot. */
    private data class RepsGroup(val key: Key, val assistance: Boolean)

    private val RepsCandidate.group get() = RepsGroup(key, assistance)

    /**
     * Two candidates are comparable when they share a [RepsGroup] and [sameLoad]. Every slot list below holds a
     * single group, so inside it only the load is compared: each list is exactly that group's subsequence of one flat
     * list searched from the front, with the same first comparable slot and the same replacements.
     */
    private fun sameSlot(slot: RepsCandidate, candidate: RepsCandidate): Boolean =
        sameLoad(slot.set.weightKg, candidate.set.weightKg)

    private inline fun forEachRepetition(index: SnapshotIndex, entries: List<WorkoutExercise>, action: (RepsCandidate) -> Unit) =
        entries.forEach { exercise ->
            index.sets(exercise.id).forEach { set -> if (repetitionSet(set)) action(RepsCandidate(exercise, set)) }
        }

    /** The first comparable candidate keeps its slot; it is replaced only by strictly more reps. */
    private fun offerRepetition(group: MutableList<RepsCandidate>, candidate: RepsCandidate) {
        val index = group.indexOfFirst { sameSlot(it, candidate) }
        if (index < 0) group += candidate
        else if (candidate.set.reps > group[index].set.reps) group[index] = candidate
    }

    /** Folds [entries], in the given order, into [baseline], one slot list per [RepsGroup]. */
    private fun foldRepetitions(index: SnapshotIndex, entries: List<WorkoutExercise>,
        baseline: MutableMap<RepsGroup, MutableList<RepsCandidate>>) =
        forEachRepetition(index, entries) { candidate -> offerRepetition(baseline.getOrPut(candidate.group) { mutableListOf() }, candidate) }

    private fun previousReps(baseline: Map<RepsGroup, List<RepsCandidate>>, candidate: RepsCandidate): Int? =
        baseline[candidate.group]?.firstOrNull { sameSlot(it, candidate) }?.set?.reps

    /** One workout's best set per comparable slot, in first-seen order; each group's slots are searched alone. */
    private fun bestRepetitionSets(index: SnapshotIndex, entries: List<WorkoutExercise>): List<RepsCandidate> {
        val best = mutableListOf<RepsCandidate>()
        val slots = mutableMapOf<RepsGroup, MutableList<Int>>()
        forEachRepetition(index, entries) { candidate ->
            val group = slots.getOrPut(candidate.group) { mutableListOf() }
            val slot = group.firstOrNull { sameSlot(best[it], candidate) }
            if (slot == null) {
                group += best.size
                best += candidate
            } else if (candidate.set.reps > best[slot].set.reps) best[slot] = candidate
        }
        return best
    }

    private fun earlierWorkoutIds(data: AppData, target: Workout): Set<String> = data.workouts.filter {
        it.status == WorkoutStatus.FINISHED && it.id != target.id && chronology.compare(it, target) < 0
    }.map { it.id }.toSet()

    fun prsForWorkout(data: AppData, workoutId: String): List<WeightPr> =
        prsForWorkout(SnapshotIndex(data), workoutId)

    private fun prsForWorkout(index: SnapshotIndex, workoutId: String): List<WeightPr> {
        val target = index.data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        return weightPrs(index, workoutId, weightBaseline(index, earlierWorkoutIds(index.data, target)))
    }

    private fun weightBaseline(index: SnapshotIndex, earlier: Set<String>): Map<Key, Double> = buildMap {
        index.data.workoutExercises.forEach { entry ->
            if (entry.workoutId in earlier) addWeightBaseline(index, entry, this)
        }
    }

    private fun addWeightBaseline(index: SnapshotIndex, exercise: WorkoutExercise, baseline: MutableMap<Key, Double>) {
        val best = index.sets(exercise.id).filter { workingSet(it, exercise.convention) }.maxOfOrNull { it.weightKg }
        if (best != null) {
            val key = Key(exercise.exerciseId, exercise.convention)
            baseline[key] = maxOf(baseline[key] ?: 0.0, best)
        }
    }

    private fun weightPrs(index: SnapshotIndex, workoutId: String, baseline: Map<Key, Double>): List<WeightPr> =
        index.entries(workoutId).groupBy { Key(it.exerciseId, it.convention) }.mapNotNull { (key, entries) ->
            val candidates = entries.flatMap { entry -> index.sets(entry.id).filter { workingSet(it, entry.convention) } }
            val best = candidates.maxWithOrNull(compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps }) ?: return@mapNotNull null
            // The exercise's first session is a record; afterwards only a heavier load is.
            val previous = baseline[key]
            if (previous != null && !heavier(best.weightKg, previous)) return@mapNotNull null
            WeightPr(key.exerciseId, entries.first().name, key.convention, previous, best.weightKg, best.reps, best.id)
        }

    /**
     * Repetition records, for each variation and assistance mode, only at the hardest load done so far: the heaviest,
     * or the least assistance. They beat an earlier value at that load, except on the group's first session when it
     * has no load record to give ([firstSessionCounts]).
     */
    fun repsPrsForWorkout(data: AppData, workoutId: String): List<RepsPr> =
        repsPrsForWorkout(SnapshotIndex(data), workoutId)

    private fun repsPrsForWorkout(index: SnapshotIndex, workoutId: String): List<RepsPr> {
        val data = index.data
        val target = data.workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        val earlier = earlierWorkoutIds(data, target)
        val baseline = mutableMapOf<RepsGroup, MutableList<RepsCandidate>>()
        foldRepetitions(index, data.workoutExercises.filter { it.workoutId in earlier }, baseline)
        return repsPrs(index, workoutId, baseline)
    }

    private fun repsPrs(index: SnapshotIndex, workoutId: String, baseline: Map<RepsGroup, List<RepsCandidate>>): List<RepsPr> {
        val best = bestRepetitionSets(index, index.entries(workoutId))
        val hardest = best.groupBy { it.group }.mapValues { (group, current) -> hardestLoad(group, baseline[group].orEmpty() + current) }
        return best.mapNotNull { candidate ->
            val top = hardest.getValue(candidate.group)
            if (!sameLoad(candidate.set.weightKg, top)) return@mapNotNull null
            val previous = previousReps(baseline, candidate)
            if (previous == null && !(baseline[candidate.group].isNullOrEmpty() && firstSessionCounts(candidate.group, top))) {
                return@mapNotNull null
            }
            if (previous != null && candidate.set.reps <= previous) return@mapNotNull null
            RepsPr(candidate.exercise.exerciseId, candidate.exercise.name, candidate.exercise.convention,
                candidate.set.weightKg, candidate.assistance, previous, candidate.set.reps, candidate.set.id)
        }
    }

    /** The heaviest load of [group], or for assisted sets the least assistance, among [candidates]. */
    private fun hardestLoad(group: RepsGroup, candidates: List<RepsCandidate>): Double =
        candidates.map { it.set.weightKg }.let { if (group.assistance) it.min() else it.max() }

    /**
     * Whether a group's first session is a reps record: only when it gives no load record, so each exercise's first
     * session counts once. Assisted sets never give one, and neither does a hardest load of 0 (no added load).
     */
    private fun firstSessionCounts(group: RepsGroup, hardest: Double): Boolean = group.assistance || hardest == 0.0

    /** Shared by both record passes, so [historyRecords] indexes the snapshot once. */
    private class HistoryPass(data: AppData) {
        val index = SnapshotIndex(data)
        /**
         * Each workout's entries in stored order. The per-workout baseline folds all earlier entries in global stored
         * order instead, so the reps pass matches it only where fold order cannot matter (see [repsPrsByWorkout]).
         */
        val storedEntries = data.workoutExercises.groupBy { it.workoutId }
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology)
    }

    /** [prsByWorkout] and [repsPrsByWorkout] together, from one index of the snapshot. */
    fun historyRecords(data: AppData): HistoryRecords {
        val pass = HistoryPass(data)
        return HistoryRecords(weightByWorkout(pass), repsByWorkout(pass))
    }

    /**
     * [prsForWorkout] for every finished workout in one chronological pass: each workout is compared with the
     * baseline of the workouts before it, then folded into that baseline. Workouts without records map to an empty list.
     */
    fun prsByWorkout(data: AppData): Map<String, List<WeightPr>> = weightByWorkout(HistoryPass(data))

    /**
     * [repsPrsForWorkout] for every finished workout in one chronological pass. Earlier workouts are folded into the
     * baseline with the same rule ([foldRepetitions]), but workout by workout in chronological order, each one's
     * entries in stored order. The result equals [repsPrsForWorkout] exactly when distinct nominal loads are at least
     * 0.25 kg apart with at most ±1 ulp noise: [sameLoad] is then an equivalence, each slot ends with its cluster's
     * most reps whatever the fold order, and only those reps are read. A chain of loads a few ulp apart could make
     * the first comparable slot, and so the previous reps shown, depend on that order.
     */
    fun repsPrsByWorkout(data: AppData): Map<String, List<RepsPr>> = repsByWorkout(HistoryPass(data))

    private fun weightByWorkout(pass: HistoryPass): Map<String, List<WeightPr>> {
        val baseline = mutableMapOf<Key, Double>()
        val result = LinkedHashMap<String, List<WeightPr>>()
        pass.finished.forEach { workout ->
            result[workout.id] = weightPrs(pass.index, workout.id, baseline)
            pass.storedEntries[workout.id].orEmpty().forEach { addWeightBaseline(pass.index, it, baseline) }
        }
        return result
    }

    private fun repsByWorkout(pass: HistoryPass): Map<String, List<RepsPr>> {
        val baseline = mutableMapOf<RepsGroup, MutableList<RepsCandidate>>()
        val result = LinkedHashMap<String, List<RepsPr>>()
        pass.finished.forEach { workout ->
            result[workout.id] = repsPrs(pass.index, workout.id, baseline)
            foldRepetitions(pass.index, pass.storedEntries[workout.id].orEmpty(), baseline)
        }
        return result
    }

    fun summary(data: AppData, workoutId: String, now: Long = currentTimeMillis()): WorkoutSummary? {
        val index = SnapshotIndex(data)
        val workout = data.workouts.firstOrNull { it.id == workoutId } ?: return null
        val baseline = weightBaseline(index, earlierWorkoutIds(data, workout))
        val records = weightPrs(index, workoutId, baseline)
        val repetitions = repsPrsForWorkout(index, workoutId)
        val exercises = index.entries(workoutId).map { exercise ->
            val completed = index.sets(exercise.id).filter(::validCompleted)
            val key = Key(exercise.exerciseId, exercise.convention)
            val record = records.firstOrNull { it.exerciseId == key.exerciseId && it.convention == key.convention }
            ExerciseSummary(exercise, completed, completed.sumOf { volume(it, exercise.convention) },
                record, repetitions.filter { it.exerciseId == key.exerciseId && it.convention == key.convention },
                record?.newWeightKg ?: baseline[key])
        }
        return WorkoutSummary(
            workout, exercises, exercises.count { it.completedSets.isNotEmpty() }, exercises.sumOf { it.completedSets.size },
            exercises.sumOf { it.volumeKg }, records,
            ((workout.endedAt ?: now) - workout.startedAt).coerceAtLeast(0L), repetitions,
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
        // Whether each slot is a record: it beat an earlier value at its load, or it was the hardest set of its group's
        // first session when that session gives no load record (as [repsPrs] decides).
        val counted = mutableListOf<Boolean>()
        // Only a record of the same exercise, convention and assistance mode can match, so each group keeps its
        // own slots in [result], in insertion order: the first matching slot is the one a scan of [result] finds.
        val slots = mutableMapOf<RepsGroup, MutableList<Int>>()
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedWith(chronology).forEach { workout ->
            val best = bestRepetitionSets(snapshot, snapshot.entries(workout.id))
            val firsts = best.groupBy { it.group }.filterKeys { slots[it].isNullOrEmpty() }
                .mapValues { (group, sets) -> hardestLoad(group, sets) }.filter { (group, top) -> firstSessionCounts(group, top) }
            best.forEach { candidate ->
                val group = slots.getOrPut(candidate.group) { mutableListOf() }
                val index = group.firstOrNull { sameLoad(result[it].weightKg, candidate.set.weightKg) } ?: -1
                if (index < 0 || candidate.set.reps > result[index].reps) {
                    val record = ExerciseRepsRecord(candidate.exercise.exerciseId, candidate.exercise.name,
                        candidate.exercise.convention, candidate.set.weightKg, candidate.assistance,
                        candidate.set.reps, workout.id, workout.endedAt ?: workout.startedAt)
                    if (index < 0) {
                        group += result.size
                        result += record
                        counted += firsts[candidate.group]?.let { sameLoad(it, candidate.set.weightKg) } == true
                    } else {
                        result[index] = record
                        counted[index] = true
                    }
                }
            }
        }
        // Each group's current reps record is the one at its hardest load, when that slot is a record.
        return slots.values.mapNotNull { group ->
            val hardest = group.maxBy { if (result[it].assistance) -result[it].weightKg else result[it].weightKg }
            result[hardest].takeIf { counted[hardest] }
        }.sortedWith(compareBy<ExerciseRepsRecord> { it.exerciseName }.thenBy { it.convention.name }
            .thenBy { it.assistance }.thenBy { it.weightKg })
    }

    fun performanceHistory(data: AppData, exerciseId: String, convention: LoadConvention? = null): List<ExercisePerformance> {
        val finished = finishedById(data)
        return performances(SnapshotIndex(data), finished, data.workoutExercises.filter {
            it.exerciseId == exerciseId && it.workoutId in finished && (convention == null || convention == it.convention)
        })
    }

    /**
     * [performanceHistory] for every exercise and convention in the finished history, from one index of the snapshot.
     * A key absent from the map has no finished entries, so its history is empty.
     */
    fun performanceHistories(data: AppData): Map<Pair<String, LoadConvention>, List<ExercisePerformance>> {
        val index = SnapshotIndex(data)
        val finished = finishedById(data)
        // groupBy keeps the stored order inside each key, which the stable sort in [performances] relies on.
        return data.workoutExercises.filter { it.workoutId in finished }.groupBy { it.exerciseId to it.convention }
            .mapValues { (_, entries) -> performances(index, finished, entries) }
    }

    private fun finishedById(data: AppData): Map<String, Workout> =
        data.workouts.filter { it.status == WorkoutStatus.FINISHED }.associateBy { it.id }

    private fun performances(index: SnapshotIndex, finished: Map<String, Workout>, entries: List<WorkoutExercise>) =
        entries.map { exercise ->
            val sets = index.sets(exercise.id).filter(::validCompleted)
            ExercisePerformance(finished.getValue(exercise.workoutId), exercise, sets, sets.sumOf { volume(it, exercise.convention) })
        }.filter { it.sets.isNotEmpty() }.sortedWith(compareByDescending<ExercisePerformance> { it.workout.startedAt }.thenBy { it.exercise.position })
}
