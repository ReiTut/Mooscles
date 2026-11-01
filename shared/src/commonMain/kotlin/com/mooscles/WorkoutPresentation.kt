package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.DecimalText
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.ExercisePerformance
import com.mooscles.domain.RepsPr
import com.mooscles.domain.WeightPr
import com.mooscles.ui.*
import kotlin.math.ulp

/** Owned by the sequential presentation flow; never called from composition. */
internal class WorkoutPresentation {
    private var historySource: AppData? = null
    private var historyPounds = false
    private var historyUi = emptyList<WorkoutUi>()
    private var recordsUi = emptyList<RecordUi>()
    private var routineSource: AppData? = null
    private var routinesUi = emptyList<RoutineUi>()
    /** Each routine's latest finished session ([RoutineUi.lastFinishedAt]), rebuilt with the history. */
    private var lastFinished = emptyMap<String, Long>()
    private var routinesFinished = emptyMap<String, Long>()
    /** Each existing routine's entries and each entry's planned rows, in position order, for the target line. */
    private var routineEntries = emptyMap<String, List<RoutineExercise>>()
    private var plannedRows = emptyMap<String, List<RoutineSet>>()
    private var catalogSource = emptyList<Exercise>()
    private var catalogUi = emptyList<ExerciseUi>()
    /** Every exercise/convention's finished history, rebuilt from one index whenever the history changes. */
    private var previousPerformances = emptyMap<Pair<String, LoadConvention>, List<ExercisePerformance>>()
    /** Keyed by exercise, convention, the session's start and whether it is the active one: the session before it. */
    private val previousDescriptions = mutableMapOf<PreviousKey, PreviousSession>()
    private var previousActive: WorkoutUi? = null

    /**
     * The latest earlier session of one exercise and convention, built once per history change and reused. Only the
     * active workout suggests from its [sets] and shows its [date]; a finished workout's carries the text alone.
     */
    private class PreviousSession(val text: String?, val sets: List<PreviousSetUi>, val date: Long?)
    private data class PreviousKey(val exerciseId: String, val convention: LoadConvention, val startedAt: Long, val active: Boolean)

    companion object {
        private val NO_PREVIOUS = PreviousSession(null, emptyList(), null)

        /**
         * Each record beaten (never a first record) keyed by the set the domain chose for it. A set holding both
         * records shows the weight one (plan decision 5). Set ids are unique, so the set's own entry is the one with
         * the record's exercise and convention.
         */
        internal fun setRecords(weight: List<WeightPr>, reps: List<RepsPr>): Map<String, SetRecordUi> {
            if (weight.all { it.firstRecord } && reps.all { it.firstRecord }) return emptyMap()
            val result = HashMap<String, SetRecordUi>()
            reps.forEach { if (!it.firstRecord) result[it.setId] = SetRecordUi(RecordKind.REPS, previousReps = it.previousReps) }
            weight.forEach { if (!it.firstRecord) result[it.setId] = SetRecordUi(RecordKind.WEIGHT, previousKg = it.previousWeightKg) }
            return result
        }
    }

    private fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
    fun present(data: AppData, prefs: PreferencesUi): UiState {
        val exercises = data.exercises.associateBy { it.id }
        if (catalogSource != data.exercises) {
            catalogSource = data.exercises
            catalogUi = data.exercises.map { it.toUi() }
        }
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
        val finishedIds = finished.mapTo(hashSetOf()) { it.id }
        val finishedEntries = data.workoutExercises.filter { it.workoutId in finishedIds }
        val finishedEntryIds = finishedEntries.mapTo(hashSetOf()) { it.id }
        // Actual completed data, not only IDs/counts, invalidates history after corrections/deletions.
        val historical = AppData(exercises = data.exercises, workouts = finished,
            workoutExercises = finishedEntries, sets = data.sets.filter { it.workoutExerciseId in finishedEntryIds })
        val historyChanged = historySource != historical || historyPounds != prefs.pounds
        if (historyChanged) {
            previousPerformances = WorkoutCalculations.performanceHistories(historical)
            previousDescriptions.clear()
            lastFinished = buildMap {
                finished.forEach { w ->
                    val routine = w.sourceRoutineId ?: return@forEach
                    val ended = w.endedAt ?: w.startedAt
                    if (ended > (get(routine) ?: Long.MIN_VALUE)) put(routine, ended)
                }
            }
        }
        // Discard/start cycles must not retain descriptions for abandoned sessions.
        if (previousActive?.id != data.activeWorkout?.id) previousDescriptions.clear()
        val allSetsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        // Records are always passed in: the history's come from one chronological pass, and only the active
        // session is compared on its own, so no caller can fall back to a per-workout pass by omission.
        fun workout(w: Workout, workoutEntries: List<WorkoutExercise>, prs: List<WeightPr>, repsPrs: List<RepsPr>, active: Boolean): WorkoutUi {
            val oldEntries = previousActive?.takeIf { it.id == w.id }?.exercises?.associateBy { it.id }.orEmpty()
            val targets = if (active) targets(workoutEntries, w.sourceRoutineId) else null
            val records = prs.map { p ->
                val e = workoutEntries.first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.newWeightKg, p.reps, p.previousWeightKg, w.id, w.endedAt ?: w.startedAt)
            } + repsPrs.map { p ->
                val e = workoutEntries.first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.weightKg, p.newReps, workoutId = w.id, date = w.endedAt ?: w.startedAt,
                    kind = RecordKind.REPS, previousReps = p.previousReps, assistance = p.assistance)
            }
            val setRecords = setRecords(prs, repsPrs)
            val entries = workoutEntries.mapIndexed { index, e ->
                val previous = previousDescriptions.getOrPut(PreviousKey(e.exerciseId, e.convention, w.startedAt, active)) {
                    val performance = previousPerformances[e.exerciseId to e.convention].orEmpty()
                        .firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }
                    if (performance == null) NO_PREVIOUS
                    else PreviousSession(performance.sets.joinToString(" · ") {
                        val load = if (it.weightKg == 0.0 && !it.assistance && e.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                        else "${if (it.assistance || e.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${DecimalText.fixed2(WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"}"
                        "$load × ${it.reps}"
                    }, if (active) performance.sets.map { PreviousSetUi(it.weightKg, it.reps, it.warmup, it.assistance) } else emptyList(),
                        performance.workout.startedAt.takeIf { active })
                }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                val savedSets = allSetsByEntry[e.id].orEmpty()
                val old = oldEntries[e.id]
                val oldSets = old?.sets?.associateBy { it.id }.orEmpty()
                val sets = savedSets.map {
                    val row = SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance, setRecords[it.id])
                    oldSets[it.id]?.takeIf { previous -> previous == row } ?: row
                }
                val entry = WorkoutExerciseUi(e.id, snapshot(e), old?.sets?.takeIf { it == sets } ?: sets,
                    e.restSeconds, previous.text, pr?.newWeightKg, pr?.firstRecord ?: false,
                    records.filter { record ->
                        record.exercise.id == e.exerciseId && record.exercise.convention == e.convention.label &&
                            savedSets.any { set ->
                                val sameLoad = kotlin.math.abs(set.weightKg - record.weightKg) <=
                                    maxOf(set.weightKg.ulp, record.weightKg.ulp) * 4
                                val assisted = set.assistance || e.convention == LoadConvention.ASSISTANCE
                                WorkoutCalculations.validCompleted(set) && !set.warmup && sameLoad &&
                                    if (record.kind == RecordKind.WEIGHT) !assisted
                                    else assisted == record.assistance && set.reps == record.reps
                            }
                    }, previous.sets, previous.date, targets?.get(index))
                old?.takeIf { it == entry } ?: entry
            }
            return WorkoutUi(w.id, w.name, w.startedAt, w.endedAt, entries, records, sourceRoutineId = w.sourceRoutineId)
        }
        if (historyChanged) {
            // Same rows and order as historical.workoutItems(id), without scanning every entry once per lookup.
            val entriesByWorkout = finishedEntries.groupBy { it.workoutId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
            fun items(workoutId: String) = entriesByWorkout[workoutId].orEmpty()
            recordsUi = WorkoutCalculations.records(historical).map { r ->
                val e = items(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
            } + WorkoutCalculations.repsRecords(historical).map { r ->
                val e = items(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps,
                    workoutId = r.workoutId, date = r.achievedAt, kind = RecordKind.REPS, assistance = r.assistance)
            }
            val historyRecords = WorkoutCalculations.historyRecords(historical)
            historyUi = finished.sortedByDescending { it.startedAt }.map {
                workout(it, items(it.id), historyRecords.weight.getValue(it.id), historyRecords.reps.getValue(it.id), active = false)
            }
            historySource = historical
            historyPounds = prefs.pounds
        }
        val routineData = AppData(exercises = data.exercises, routines = data.routines,
            routineExercises = data.routineExercises, routineSets = data.routineSets, routineFolders = data.routineFolders)
        if (routineSource != routineData || routinesFinished != lastFinished) {
            val routineIds = data.routines.mapTo(hashSetOf()) { it.id }
            routineEntries = data.routineExercises.filter { it.routineId in routineIds }.groupBy { it.routineId }
                .mapValues { (_, rows) -> rows.sortedBy { it.position } }
            plannedRows = data.routineSets.groupBy { it.routineExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
            val folderIds = data.routineFolders.mapTo(hashSetOf()) { it.id }
            val catalog = catalogUi.associateBy { it.id }
            val oldRoutines = routinesUi.associateBy { it.id }
            val next = data.routines.sortedBy { it.position }.map { r ->
                val old = oldRoutines[r.id]
                val oldPlans = old?.exercises?.associateBy { it.id }.orEmpty()
                val plans = routineEntries[r.id].orEmpty().mapNotNull { e -> catalog[e.exerciseId]?.let { exercise ->
                    val targets = plannedRows[e.id].orEmpty().map { row ->
                        RoutineSetUi(row.id, row.targetReps, row.targetWeightKg, row.inheritPr && !row.warmup, row.warmup)
                    }
                    // The summary columns follow the first working set, as the repository mirrors them.
                    val summary = targets.firstOrNull { !it.warmup } ?: targets.firstOrNull()
                    val plan = PlannedExerciseUi(e.id, exercise, targets.size, summary?.reps ?: 0, summary?.weightKg,
                        e.restSeconds ?: com.mooscles.domain.RestDuration.DEFAULT_SECONDS, targets)
                    oldPlans[e.id]?.takeIf { it == plan } ?: plan
                } }
                // A routine whose folder is gone shows in the default one, where the repository orders it.
                val folderId = if (r.folderId in folderIds) r.folderId else RoutineFolder.DEFAULT_ID
                val routine = RoutineUi(r.id, r.name, old?.exercises?.takeIf { it == plans } ?: plans, folderId, lastFinished[r.id])
                old?.takeIf { it == routine } ?: routine
            }
            routinesUi = routinesUi.takeIf { it == next } ?: next
            routineSource = routineData
            routinesFinished = lastFinished
        }
        val active = data.activeWorkout?.let {
            workout(it, data.workoutItems(it.id), WorkoutCalculations.prsForWorkout(data, it.id),
                WorkoutCalculations.repsPrsForWorkout(data, it.id), active = true)
        }
        previousActive = previousActive?.takeIf { it == active } ?: active
        val folders = data.routineFolders.sortedBy { it.position }.map { RoutineFolderUi(it.id, it.name) }
            .ifEmpty { listOf(RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)) }
        return UiState(false, catalogUi, routinesUi, historyUi, previousActive, recordsUi, preferences = prefs,
            routineFolders = folders)
    }

    /**
     * The target line of each of the active workout's [entries] (plan decision 8, preflight F16): the n-th entry of
     * an exercise matches the n-th entry of that exercise in the source routine, by position. The workout keeps no
     * link to the routine entry it came from, so an entry replaced by an exercise the routine still has unmatched
     * takes that one's target; one with nothing left to match, or without a source routine, has none.
     */
    private fun targets(entries: List<WorkoutExercise>, routineId: String?): List<RoutineTargetUi?> {
        val plans = routineId?.let { routineEntries[it] } ?: return entries.map { null }
        val byExercise = plans.groupBy { it.exerciseId }
        val seen = HashMap<String, Int>()
        return entries.map { e ->
            val occurrence = seen[e.exerciseId] ?: 0
            seen[e.exerciseId] = occurrence + 1
            byExercise[e.exerciseId]?.getOrNull(occurrence)?.let { plan ->
                val rows = plannedRows[plan.id].orEmpty()
                RoutineTargetUi(rows.filterNot { it.warmup }.map { TargetSetUi(it.targetReps, it.targetWeightKg) }, rows.count { it.warmup })
            }
        }
    }
}
