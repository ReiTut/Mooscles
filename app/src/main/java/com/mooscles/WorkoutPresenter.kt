package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.RestDuration
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.ui.*
import java.util.Locale

/**
 * Prepares every card before publishing a workout. Called on the presentation worker, never from
 * composition. Indexing avoids a full saved-history/set scan for each visible exercise. Only the
 * current source snapshot is retained; the cache cannot grow with editing or scrolling.
 */
internal class WorkoutPresenter {
    private data class Variation(val exerciseId: String, val convention: LoadConvention)
    private data class Candidate(val entry: WorkoutExercise, val set: WorkoutSet) {
        val key get() = Variation(entry.exerciseId, entry.convention)
        val assistance get() = set.assistance || entry.convention == LoadConvention.ASSISTANCE
    }
    private data class Repetition(val weight: Double, val assistance: Boolean, val reps: Int)
    private data class Achievements(val weights: List<RecordUi>, val repetitions: List<RecordUi>) {
        val records = weights + repetitions
    }
    private data class Performance(val workout: Workout, val entry: WorkoutExercise, val sets: List<WorkoutSet>)
    private data class HistorySource(
        val catalog: List<Exercise>, val workouts: List<Workout>, val entries: List<WorkoutExercise>,
        val sets: List<WorkoutSet>, val pounds: Boolean,
    )
    private data class RoutineSource(
        val catalog: List<Exercise>, val routines: List<Routine>, val entries: List<RoutineExercise>,
        val sets: List<RoutineSet>,
    )
    private data class CardSource(
        val entry: WorkoutExercise, val exercise: ExerciseUi, val sets: List<WorkoutSet>,
        val previous: String?, val record: RecordUi?, val records: List<RecordUi>,
    )
    private data class ActiveSource(
        val workout: Workout?, val entries: List<WorkoutExercise>, val sets: List<WorkoutSet>,
        val catalog: List<Exercise>, val historyRevision: Long, val pounds: Boolean,
    )

    internal data class Metrics(
        val presentations: Long, val historyPreparations: Long, val preparedWorkouts: Long,
        val preparedExerciseCards: Long, val lastPreparationNanos: Long, val lastPreparationThread: String,
    )
    private var presentations = 0L
    private var historyPreparations = 0L
    private var preparedWorkouts = 0L
    private var preparedExerciseCards = 0L
    private var lastPreparationNanos = 0L
    private var lastPreparationThread = ""
    internal fun metrics() = Metrics(presentations, historyPreparations, preparedWorkouts,
        preparedExerciseCards, lastPreparationNanos, lastPreparationThread)

    private var historySource: HistorySource? = null
    private var historyIndex = HistoryIndex(emptyList(), emptyList(), emptyList(), emptyMap())
    private var historyUi: List<WorkoutUi> = emptyList()
    private var routineSource: RoutineSource? = null
    private var routineUi: List<RoutineUi> = emptyList()
    private var catalog: List<Exercise>? = null
    private var catalogUi: List<ExerciseUi> = emptyList()
    private var activeSource: ActiveSource? = null
    private var activeUi: WorkoutUi? = null
    private var activeCards: Map<String, Pair<CardSource, WorkoutExerciseUi>> = emptyMap()

    /** Serial worker use; timer ticks are combined with this completed snapshot downstream. */
    fun present(data: AppData, prefs: PreferencesUi): UiState {
        val started = System.nanoTime()
        lastPreparationThread = Thread.currentThread().name
        presentations++
        val exercises = data.exercises.associateBy { it.id }
        if (catalog != data.exercises) {
            catalog = data.exercises
            catalogUi = data.exercises.map { it.toUi() }
        }
        val nextRoutine = RoutineSource(data.exercises, data.routines, data.routineExercises, data.routineSets)
        if (routineSource != nextRoutine) {
            routineSource = nextRoutine
            val entries = data.routineExercises.groupBy { it.routineId }
            val sets = data.routineSets.groupBy { it.routineExerciseId }
            routineUi = data.routines.sortedBy { it.position }.map { routine ->
                RoutineUi(routine.id, routine.name, entries[routine.id].orEmpty().sortedBy { it.position }.mapNotNull { entry ->
                    exercises[entry.exerciseId]?.let { exercise ->
                        val targets = sets[entry.id].orEmpty().sortedBy { it.position }.map {
                            RoutineSetUi(it.id, it.targetReps, it.targetWeightKg, it.inheritPr)
                        }
                        PlannedExerciseUi(entry.id, exercise.toUi(), targets.size, targets.firstOrNull()?.reps ?: 0,
                            targets.firstOrNull()?.weightKg, entry.restSeconds ?: RestDuration.DEFAULT_SECONDS, targets)
                    }
                })
            }
        }

        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
        val finishedIds = finished.mapTo(HashSet()) { it.id }
        val historyEntries = data.workoutExercises.filter { it.workoutId in finishedIds }
        val historyEntryIds = historyEntries.mapTo(HashSet()) { it.id }
        val historySets = data.sets.filter { it.workoutExerciseId in historyEntryIds }
        val nextHistory = HistorySource(data.exercises, finished, historyEntries, historySets, prefs.pounds)
        if (historySource != nextHistory) {
            historySource = nextHistory
            historyPreparations++
            historyIndex = HistoryIndex(finished, historyEntries, historySets, exercises)
            historyUi = finished.sortedByDescending { it.startedAt }.map { workout ->
                prepareWorkout(workout, historyIndex.entries[workout.id].orEmpty(), historyIndex.sets,
                    exercises, historyIndex.achievements.getValue(workout.id), prefs.pounds).first
            }
        }

        val active = data.activeWorkout
        val entries = active?.let { workout -> data.workoutExercises.filter { it.workoutId == workout.id }.sortedBy { it.position } }.orEmpty()
        val entryIds = entries.mapTo(HashSet()) { it.id }
        val sets = data.sets.filter { it.workoutExerciseId in entryIds }
        val nextActive = ActiveSource(active, entries, sets, data.exercises, historyPreparations, prefs.pounds)
        if (activeSource != nextActive) {
            activeSource = nextActive
            if (active == null) {
                activeUi = null
                activeCards = emptyMap()
            } else {
                val groupedSets = sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
                val achievements = historyIndex.achievementsFor(active, entries, groupedSets)
                val prepared = prepareWorkout(active, entries, groupedSets, exercises, achievements, prefs.pounds, activeCards)
                // An order-only change reuses cards and their sets. A routine/settings update reuses
                // the complete WorkoutUi; changing one set only replaces that exercise's card.
                val oldExercises = activeUi?.takeIf { it.id == active.id }?.exercises
                activeUi = if (oldExercises != null && oldExercises.size == prepared.first.exercises.size &&
                    oldExercises.indices.all { oldExercises[it] === prepared.first.exercises[it] })
                    prepared.first.copy(exercises = oldExercises) else prepared.first
                activeCards = prepared.second
            }
        }
        lastPreparationNanos = System.nanoTime() - started
        return UiState(loading = false, exercises = catalogUi, routines = routineUi, history = historyUi,
            active = activeUi, records = historyIndex.records, preferences = prefs)
    }

    private fun prepareWorkout(
        workout: Workout, entries: List<WorkoutExercise>, sets: Map<String, List<WorkoutSet>>,
        catalog: Map<String, Exercise>, achievements: Achievements, pounds: Boolean,
        oldCards: Map<String, Pair<CardSource, WorkoutExerciseUi>> = emptyMap(),
    ): Pair<WorkoutUi, Map<String, Pair<CardSource, WorkoutExerciseUi>>> {
        preparedWorkouts++
        val cards = LinkedHashMap<String, Pair<CardSource, WorkoutExerciseUi>>(entries.size)
        val weights = achievements.weights.associateBy { it.exercise.id to it.exercise.convention }
        val recordsByVariation = achievements.records.groupBy { it.exercise.id to it.exercise.convention }
        val prepared = entries.map { entry ->
            val rows = sets[entry.id].orEmpty()
            val exercise = entry.toUi(catalog)
            val key = entry.exerciseId to entry.convention.label
            val records = recordsByVariation[key].orEmpty().filter { record -> rows.any { set ->
                WorkoutCalculations.validCompleted(set) && !set.warmup && sameLoad(set.weightKg, record.weightKg) &&
                    if (record.kind == RecordKind.WEIGHT) !set.assistance && entry.convention != LoadConvention.ASSISTANCE
                    else (set.assistance || entry.convention == LoadConvention.ASSISTANCE) == record.assistance && set.reps == record.reps
            } }
            val previous = historyIndex.previous(entry, workout.startedAt, pounds)
            // Position is ordering metadata, not card contents. Ignore it so drag save does not
            // invalidate otherwise identical cards or transfer values between duplicate exercises.
            val source = CardSource(entry.copy(position = 0), exercise, rows, previous, weights[key], records)
            val ui = oldCards[entry.id]?.takeIf { it.first == source }?.second ?: run {
                preparedExerciseCards++
                WorkoutExerciseUi(entry.id, exercise, rows.map { SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance) },
                    entry.restSeconds, previous, weights[key]?.weightKg, weights[key]?.firstRecord ?: false, records)
            }
            cards[entry.id] = source to ui
            ui
        }
        return WorkoutUi(workout.id, workout.name, workout.startedAt, workout.endedAt, prepared,
            achievements.records, workout.sourceRoutineId) to cards
    }

    /** Indexed timeline: one pass for weight PRs, indexed prior sets for exact repetition rules. */
    private class HistoryIndex(
        workouts: List<Workout>, workoutEntries: List<WorkoutExercise>, workoutSets: List<WorkoutSet>,
        private val catalog: Map<String, Exercise>,
    ) {
        val entries = workoutEntries.groupBy { it.workoutId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        val sets = workoutSets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        val achievements = HashMap<String, Achievements>()
        private val performances: Map<Variation, List<Performance>>
        private val chronological = workouts.sortedWith(chronology)
        private val rawEntries = workoutEntries
        private val weightBaselines = HashMap<Variation, Double>()
        private val repBaselines = repetitionsBefore(null)
        val records: List<RecordUi>

        init {
            val performanceRows = HashMap<Variation, MutableList<Performance>>()
            val weightRecords = LinkedHashMap<Variation, RecordUi>()
            val repetitionRecords = LinkedHashMap<Variation, MutableList<RecordUi>>()
            val weightRecordNames = HashMap<Variation, String>()
            val repetitionRecordNames = HashMap<RecordUi, String>()
            val repetitionRecordOrder = HashMap<RecordUi, Int>()
            var nextRepetitionOrder = 0
            chronological.forEach { workout ->
                val current = entries[workout.id].orEmpty()
                achievements[workout.id] = calculateAchievements(workout, current, sets, weightBaselines, repetitionsBefore(workout))
                current.forEach { entry ->
                    val rows = sets[entry.id].orEmpty()
                    val key = entry.key()
                    val best = rows.filter { eligibleWeight(it, entry.convention) }.maxWithOrNull(bestWeight)
                    if (best != null) {
                        weightBaselines[key] = maxOf(weightBaselines[key] ?: 0.0, best.weightKg)
                        val previous = weightRecords[key]
                        // Match the existing global record's first achievement at a weight; a later
                        // same-weight rep improvement is independently retained as a repetition PR.
                        if (previous == null || heavier(best.weightKg, previous.weightKg)) {
                            val snapshot = current.first { it.key() == key }
                            val exercise = snapshot.toUi(catalog).copy(name = catalog[entry.exerciseId]?.name ?: entry.name)
                            weightRecords[key] = RecordUi(exercise, best.weightKg, best.reps,
                                workoutId = workout.id, date = workout.endedAt ?: workout.startedAt)
                            weightRecordNames[key] = entry.name
                        }
                    }
                }
                bestRepetitions(current, sets).forEach { candidate ->
                    val found = repetitionRecords.getOrPut(candidate.key) { mutableListOf() }
                    val index = found.indexOfFirst { it.assistance == candidate.assistance && sameLoad(it.weightKg, candidate.set.weightKg) }
                    if (index < 0 || candidate.set.reps > found[index].reps) {
                        // Snapshot resolution deliberately uses the first matching variation, just
                        // as the original presentation did for duplicate instances of a variation.
                        val entry = current.first { it.key() == candidate.key }
                        val exercise = entry.toUi(catalog).copy(name = catalog[entry.exerciseId]?.name ?: candidate.entry.name)
                        val record = RecordUi(exercise, candidate.set.weightKg, candidate.set.reps,
                            workoutId = workout.id, date = workout.endedAt ?: workout.startedAt,
                            kind = RecordKind.REPS, assistance = candidate.assistance)
                        val order = if (index < 0) nextRepetitionOrder++ else repetitionRecordOrder.getValue(found[index])
                        if (index < 0) found += record else {
                            repetitionRecordOrder.remove(found[index])
                            repetitionRecordNames.remove(found[index])
                            found[index] = record
                        }
                        repetitionRecordNames[record] = candidate.entry.name
                        repetitionRecordOrder[record] = order
                    }
                }
            }
            val workoutsById = workouts.associateBy { it.id }
            // Previous-card lookup keeps the original database encounter order at timestamp and
            // position ties, independently of the stricter PR chronology used above.
            workoutEntries.forEach { entry ->
                val completed = sets[entry.id].orEmpty().filter(WorkoutCalculations::validCompleted)
                if (completed.isNotEmpty()) performanceRows.getOrPut(entry.key()) { mutableListOf() } +=
                    Performance(workoutsById.getValue(entry.workoutId), entry, completed)
            }
            performances = performanceRows.mapValues { (_, rows) ->
                // At equal timestamps the old card lookup chose the smallest position, preserving
                // database encounter order for ties. Retain exactly that representative.
                rows.groupBy { it.workout.startedAt }.values.map { group -> group.minBy { it.entry.position } }
                    .sortedBy { it.workout.startedAt }
            }
            records = weightRecords.entries.sortedWith(compareBy<Map.Entry<Variation, RecordUi>> { weightRecordNames[it.key] }
                .thenBy { it.key.convention.name }).map { it.value } +
                repetitionRecords.entries.flatMap { (key, values) -> values.map { key to it } }.sortedWith(
                    compareBy<Pair<Variation, RecordUi>> { repetitionRecordNames[it.second] }.thenBy { it.first.convention.name }
                        .thenBy { it.second.assistance }.thenBy { it.second.weightKg }
                        .thenBy { repetitionRecordOrder[it.second] }).map { it.second }
        }

        fun previous(entry: WorkoutExercise, startedAt: Long, pounds: Boolean): String? {
            val rows = performances[entry.key()].orEmpty()
            var low = 0
            var high = rows.size
            // Upper bound for strictly earlier start times. Equal-time sessions do not count as
            // "previous" cards, even though their ended-at/id chronology can affect PR baselines.
            while (low < high) {
                val mid = (low + high).ushr(1)
                if (rows[mid].workout.startedAt < startedAt) low = mid + 1 else high = mid
            }
            return rows.getOrNull(low - 1)?.sets?.joinToString(" · ") { set ->
                val load = if (set.weightKg == 0.0 && !set.assistance && entry.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                else "${if (set.assistance || entry.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${String.format(Locale.US, "%.2f", WeightUnits.fromKg(set.weightKg, pounds)).trimEnd('0').trimEnd('.')} ${if (pounds) "lb" else "kg"}"
                "$load × ${set.reps}"
            }
        }

        fun achievementsFor(workout: Workout, current: List<WorkoutExercise>, currentSets: Map<String, List<WorkoutSet>>): Achievements {
            // Normal active sessions follow the entire saved history. Backdated imported sessions
            // need baselines limited to the same strict chronology as WorkoutCalculations.
            if (chronological.lastOrNull()?.let { chronology.compare(it, workout) < 0 } != false) {
                return calculateAchievements(workout, current, currentSets, weightBaselines, repBaselines)
            }
            val weights = HashMap<Variation, Double>()
            chronological.takeWhile { chronology.compare(it, workout) < 0 }.forEach { prior ->
                val priorEntries = entries[prior.id].orEmpty()
                priorEntries.forEach { entry ->
                    val best = sets[entry.id].orEmpty().filter { eligibleWeight(it, entry.convention) }.maxOfOrNull { it.weightKg }
                    if (best != null) weights[entry.key()] = maxOf(weights[entry.key()] ?: 0.0, best)
                }
            }
            return calculateAchievements(workout, current, currentSets, weights, repetitionsBefore(workout))
        }

        private fun repetitionsBefore(target: Workout?): Map<Variation, List<Repetition>> {
            val earlier = chronological.asSequence().takeWhile { target == null || chronology.compare(it, target) < 0 }
                .mapTo(HashSet()) { it.id }
            // Approximate load equality is intentionally the existing four-ULP rule, which is not
            // transitive. Preserve raw database encounter order before selecting best sets; merging
            // chronological per-session maxima could change a chained near-equal load's PR status.
            // All sets are already indexed, so this never scans the entire set table per entry.
            return bestRepetitions(rawEntries.filter { it.workoutId in earlier }, sets).groupBy { it.key }
                .mapValues { (_, candidates) -> candidates.map { Repetition(it.set.weightKg, it.assistance, it.set.reps) } }
        }

        private fun calculateAchievements(
            workout: Workout, current: List<WorkoutExercise>, rows: Map<String, List<WorkoutSet>>,
            weights: Map<Variation, Double>, repetitions: Map<Variation, List<Repetition>>,
        ): Achievements {
            val weightPrs = current.groupBy { it.key() }.mapNotNull { (key, group) ->
                val best = group.asSequence().flatMap { entry -> rows[entry.id].orEmpty().asSequence().filter { eligibleWeight(it, entry.convention) } }
                    .maxWithOrNull(bestWeight) ?: return@mapNotNull null
                val previous = weights[key]
                if (previous != null && !heavier(best.weightKg, previous)) return@mapNotNull null
                RecordUi(group.first().toUi(catalog), best.weightKg, best.reps, previous,
                    workout.id, workout.endedAt ?: workout.startedAt)
            }
            val repetitionPrs = bestRepetitions(current, rows).mapNotNull { candidate ->
                val previous = repetitions[candidate.key].orEmpty().firstOrNull {
                    it.assistance == candidate.assistance && sameLoad(it.weight, candidate.set.weightKg)
                }?.reps
                if (previous != null && candidate.set.reps <= previous) return@mapNotNull null
                val entry = current.first { it.key() == candidate.key }
                RecordUi(entry.toUi(catalog), candidate.set.weightKg, candidate.set.reps,
                    workoutId = workout.id, date = workout.endedAt ?: workout.startedAt,
                    kind = RecordKind.REPS, previousReps = previous, assistance = candidate.assistance)
            }
            return Achievements(weightPrs, repetitionPrs)
        }
    }

    private companion object {
        val chronology = compareBy<Workout> { it.startedAt }.thenBy { it.endedAt ?: Long.MAX_VALUE }.thenBy { it.id }
        val bestWeight = compareBy<WorkoutSet> { it.weightKg }.thenBy { it.reps }
        fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
        fun WorkoutExercise.key() = Variation(exerciseId, convention)
        fun WorkoutExercise.toUi(catalog: Map<String, Exercise>) = ExerciseUi(exerciseId, name, muscle.label,
            equipment.label, convention.label, catalog[exerciseId]?.custom ?: false)
        fun sameLoad(first: Double, second: Double) = kotlin.math.abs(first - second) <= maxOf(Math.ulp(first), Math.ulp(second)) * 4
        fun heavier(value: Double, baseline: Double) = value > baseline && value - baseline > maxOf(Math.ulp(value), Math.ulp(baseline)) * 4
        fun eligibleWeight(set: WorkoutSet, convention: LoadConvention) = WorkoutCalculations.validCompleted(set) &&
            !set.warmup && !set.assistance && convention != LoadConvention.ASSISTANCE && set.weightKg > 0.0
        fun bestRepetitions(entries: List<WorkoutExercise>, sets: Map<String, List<WorkoutSet>>): List<Candidate> {
            val best = mutableListOf<Candidate>()
            val groups = HashMap<Variation, MutableList<Int>>()
            entries.forEach { entry -> sets[entry.id].orEmpty().forEach { set ->
                if (WorkoutCalculations.validCompleted(set) && !set.warmup) {
                    val candidate = Candidate(entry, set)
                    val group = groups.getOrPut(candidate.key) { mutableListOf() }
                    val index = group.firstOrNull { best[it].assistance == candidate.assistance && sameLoad(best[it].set.weightKg, set.weightKg) }
                    if (index == null) { group += best.size; best += candidate }
                    else if (set.reps > best[index].set.reps) best[index] = candidate
                }
            } }
            return best
        }
    }
}
