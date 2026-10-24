package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.domain.ExercisePerformance
import com.mooscles.ui.*
import java.util.Locale

/** Owned by the sequential presentation flow; never called from composition. */
internal class WorkoutPresentation {
    private var historySource: AppData? = null
    private var historyPounds = false
    private var historyUi = emptyList<WorkoutUi>()
    private var recordsUi = emptyList<RecordUi>()
    private var routineSource: AppData? = null
    private var routinesUi = emptyList<RoutineUi>()
    private var catalogSource = emptyList<Exercise>()
    private var catalogUi = emptyList<ExerciseUi>()
    private val previousPerformances = mutableMapOf<Pair<String, LoadConvention>, List<ExercisePerformance>>()
    private val previousDescriptions = mutableMapOf<Triple<String, LoadConvention, Long>, String?>()
    private var previousActive: WorkoutUi? = null

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
            previousPerformances.clear()
            previousDescriptions.clear()
        }
        // Discard/start cycles must not retain descriptions for abandoned sessions.
        if (previousActive?.id != data.activeWorkout?.id) previousDescriptions.clear()
        val allSetsByEntry = data.sets.groupBy { it.workoutExerciseId }.mapValues { (_, rows) -> rows.sortedBy { it.position } }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        fun workout(w: Workout, data: AppData): WorkoutUi {
            val prs = WorkoutCalculations.prsForWorkout(data, w.id)
            val repsPrs = WorkoutCalculations.repsPrsForWorkout(data, w.id)
            val workoutEntries = data.workoutItems(w.id)
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
                    previousPerformances.getOrPut(e.exerciseId to e.convention) {
                        WorkoutCalculations.performanceHistory(historical, e.exerciseId, e.convention)
                    }.firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }?.sets?.joinToString(" · ") {
                        val load = if (it.weightKg == 0.0 && !it.assistance && e.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                        else "${if (it.assistance || e.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${String.format(Locale.US, "%.2f", WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"}"
                        "$load × ${it.reps}"
                    }
                }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                val savedSets = allSetsByEntry[e.id].orEmpty()
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
            recordsUi = WorkoutCalculations.records(historical).map { r ->
                val e = historical.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
            } + WorkoutCalculations.repsRecords(historical).map { r ->
                val e = historical.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
                RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps,
                    workoutId = r.workoutId, date = r.achievedAt, kind = RecordKind.REPS, assistance = r.assistance)
            }
            historyUi = finished.sortedByDescending { it.startedAt }.map { workout(it, historical) }
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
        val active = data.activeWorkout?.let { workout(it, data) }
        previousActive = previousActive?.takeIf { it == active } ?: active
        val folders = data.routineFolders.sortedBy { it.position }.map { RoutineFolderUi(it.id, it.name) }
            .ifEmpty { listOf(RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)) }
        return UiState(false, catalogUi, routinesUi, historyUi, previousActive, recordsUi, preferences = prefs,
            routineFolders = folders)
    }
}
