package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.*
import java.util.Locale

/** Owned by the sequential presentation flow; never called from composition. */
internal class WorkoutPresentation {
    private var historySource: AppData? = null
    private var historicalAnalysis = WorkoutCalculations.historicalAnalysis(AppData())
    private var historyPounds = false
    private var historyUi = emptyList<WorkoutUi>()
    private var recordsUi = emptyList<RecordUi>()
    private var routineSource: AppData? = null
    private var routinesUi = emptyList<RoutineUi>()
    private var catalogSource = emptyList<Exercise>()
    private var catalogUi = emptyList<ExerciseUi>()
    private val previousDescriptions = mutableMapOf<Triple<String, LoadConvention, Long>, String?>()
    private var previousActive: WorkoutUi? = null
    private var lastUi = UiState()

    /** Start-only path. No historical cache is consulted or invalidated by a partial snapshot. */
    fun presentEssential(snapshot: ActiveWorkoutSnapshot, prefs: PreferencesUi): UiState {
        val old = previousActive?.takeIf { it.id == snapshot.workout.id }
        val oldEntries = old?.exercises?.associateBy { it.id }.orEmpty()
        val metadata = snapshot.exercises.associateBy { it.id }
        val setsByEntry = snapshot.sets.groupBy { it.workoutExerciseId }
        val entries = snapshot.entries.map { entry ->
            val prior = oldEntries[entry.id]
            val priorSets = prior?.sets?.associateBy { it.id }.orEmpty()
            val rows = setsByEntry[entry.id].orEmpty().sortedBy { it.position }.map { saved ->
                val row = SetUi(saved.id, saved.reps, saved.weightKg, saved.completed, saved.warmup, saved.assistance)
                priorSets[row.id]?.takeIf { it == row } ?: row
            }.let { rows -> prior?.sets?.takeIf { it == rows } ?: rows }
            // This partial path never guesses historical PRs from a possibly stale baseline.
            // It is retired after one full read acknowledges the current saved editor rows.
            val next = WorkoutExerciseUi(entry.id,
                ExerciseUi(entry.exerciseId, entry.name, entry.muscle.label, entry.equipment.label,
                    entry.convention.label, metadata[entry.exerciseId]?.custom ?: false),
                rows, entry.restSeconds)
            prior?.takeIf { it == next } ?: next
        }
        val saved = snapshot.workout
        val active = WorkoutUi(saved.id, saved.name, saved.startedAt, saved.endedAt, entries,
            sourceRoutineId = saved.sourceRoutineId)
        previousActive = old?.takeIf { it == active } ?: active
        return lastUi.copy(active = previousActive, preferences = prefs, secondaryLoading = true).also { lastUi = it }
    }

    private fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
    fun present(data: AppData, prefs: PreferencesUi): UiState {
        val exercises = data.exercises.associateBy { it.id }
        if (catalogSource != data.exercises) {
            catalogSource = data.exercises.toList()
            catalogUi = data.exercises.map { it.toUi() }
        }
        val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
        val finishedIds = finished.mapTo(hashSetOf()) { it.id }
        val finishedEntries = data.workoutExercises.filter { it.workoutId in finishedIds }
        val finishedEntryIds = finishedEntries.mapTo(hashSetOf()) { it.id }
        // Actual completed data, not only IDs/counts, invalidates history after corrections/deletions.
        val historical = AppData(exercises = data.exercises.toList(), workouts = finished,
            workoutExercises = finishedEntries, sets = data.sets.filter { it.workoutExerciseId in finishedEntryIds })
        val historyDataChanged = historySource != historical
        val historyChanged = historyDataChanged || historyPounds != prefs.pounds
        if (historyChanged) {
            previousDescriptions.clear()
        }
        if (historyDataChanged) historicalAnalysis = WorkoutCalculations.historicalAnalysis(historical)
        // Discard/start cycles must not retain descriptions for abandoned sessions.
        if (previousActive?.id != data.activeWorkout?.id) previousDescriptions.clear()
        val activeWorkout = data.activeWorkout
        val activeEntries = activeWorkout?.let { data.workoutItems(it.id) }.orEmpty()
        val activeEntryIds = activeEntries.mapTo(hashSetOf()) { it.id }
        val activeSets = data.sets.filter { it.workoutExerciseId in activeEntryIds }
        val activeSetsByEntry = activeSets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        fun workout(w: Workout): WorkoutUi {
            val finishedWorkout = w.status == WorkoutStatus.FINISHED
            val achievements = if (finishedWorkout) historicalAnalysis.prsForWorkout(w.id)
                else historicalAnalysis.prsForActive(w, activeEntries, activeSets)
            val prs = achievements.weights
            val repsPrs = achievements.repetitions
            val workoutEntries = if (finishedWorkout) historicalAnalysis.entries(w.id) else activeEntries
            val oldEntries = previousActive?.takeIf { it.id == w.id }?.exercises?.associateBy { it.id }.orEmpty()
            val records = prs.map { p ->
                val e = workoutEntries.first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.newWeightKg, p.reps, p.previousWeightKg, w.id, w.endedAt ?: w.startedAt)
            } + repsPrs.map { p ->
                val e = workoutEntries.first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.weightKg, p.newReps, workoutId = w.id, date = w.endedAt ?: w.startedAt,
                    kind = RecordKind.REPS, previousReps = p.previousReps, assistance = p.assistance)
            }
            val entries = workoutEntries.map { e ->
                val previous = previousDescriptions.getOrPut(Triple(e.exerciseId, e.convention, w.startedAt)) {
                    historicalAnalysis.performanceHistory(e.exerciseId, e.convention)
                        .firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }?.sets?.joinToString(" · ") {
                        val load = if (it.weightKg == 0.0 && !it.assistance && e.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                        else "${if (it.assistance || e.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${String.format(Locale.US, "%.2f", WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"}"
                        "$load × ${it.reps}"
                    }
                }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                val savedSets = if (finishedWorkout) historicalAnalysis.sets(e.id) else activeSetsByEntry[e.id].orEmpty()
                val old = oldEntries[e.id]
                val oldSets = old?.sets?.associateBy { it.id }.orEmpty()
                val sets = savedSets.map {
                    val row = SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance)
                    oldSets[it.id]?.takeIf { previous -> previous == row } ?: row
                }
                val entry = WorkoutExerciseUi(e.id, snapshot(e), old?.sets?.takeIf { it == sets } ?: sets,
                    e.restSeconds, previous, pr?.newWeightKg, pr?.firstRecord ?: false,
                    records.filter { record ->
                        record.exercise.id == e.exerciseId && record.exercise.convention == e.convention.label &&
                            savedSets.any { set ->
                                val sameLoad = kotlin.math.abs(set.weightKg - record.weightKg) <=
                                    maxOf(Math.ulp(set.weightKg), Math.ulp(record.weightKg)) * 4
                                val assisted = set.assistance || e.convention == LoadConvention.ASSISTANCE
                                WorkoutCalculations.validCompleted(set) && !set.warmup && sameLoad &&
                                    if (record.kind == RecordKind.WEIGHT) !assisted
                                    else assisted == record.assistance && set.reps == record.reps
                            }
                    })
                old?.takeIf { it == entry } ?: entry
            }
            return WorkoutUi(w.id, w.name, w.startedAt, w.endedAt, entries, records, sourceRoutineId = w.sourceRoutineId)
        }
        if (historyChanged) {
            recordsUi = historicalAnalysis.records().map { r ->
                val e = historicalAnalysis.entries(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
            } + historicalAnalysis.repsRecords().map { r ->
                val e = historicalAnalysis.entries(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps,
                    workoutId = r.workoutId, date = r.achievedAt, kind = RecordKind.REPS, assistance = r.assistance)
            }
            historyUi = finished.sortedByDescending { it.startedAt }.map { workout(it) }
            historySource = historical
            historyPounds = prefs.pounds
        }
        val routineData = AppData(exercises = data.exercises, routines = data.routines,
            routineExercises = data.routineExercises, routineSets = data.routineSets)
        if (routineSource != routineData) {
            routinesUi = data.routines.sortedBy { it.position }.map { r ->
                RoutineUi(r.id, r.name, data.routineItems(r.id).mapNotNull { e -> exercises[e.exerciseId]?.let {
                    val targets = data.plannedSets(e.id).map { row -> RoutineSetUi(row.id, row.targetReps, row.targetWeightKg, row.inheritPr) }
                    PlannedExerciseUi(e.id, it.toUi(), targets.size, targets.firstOrNull()?.reps ?: 0,
                        targets.firstOrNull()?.weightKg, e.restSeconds ?: com.mooscles.domain.RestDuration.DEFAULT_SECONDS, targets)
                } }, folderId = r.folderId)
            }
            routineSource = routineData
        }
        val active = activeWorkout?.let { workout(it) }
        previousActive = previousActive?.takeIf { it == active } ?: active
        val folders = data.routineFolders.sortedBy { it.position }.map { RoutineFolderUi(it.id, it.name) }
            .ifEmpty { listOf(RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)) }
        return UiState(false, catalogUi, routinesUi, historyUi, previousActive, recordsUi, preferences = prefs,
            routineFolders = folders).also { lastUi = it }
    }
}
