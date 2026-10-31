package com.mooscles.screens.progress

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi

/** Test-only builders for [ProgressIndex]: finished workouts, entries and sets with unique ids. */

private var nextId = 0

private fun id(prefix: String): String = "$prefix-${++nextId}"

const val DAY = 86_400_000L
const val HOUR = 3_600_000L

val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", "Total load")
val dumbbellPress = ExerciseUi("catalog_dumbbell_bench_flat", "Flat dumbbell bench press", "Chest", "Dumbbell", "Per hand")
val pullUp = ExerciseUi("catalog_bodyweight_pull_up", "Overhand pull-up", "Back", "Bodyweight", "Added weight")
val assistedDip = ExerciseUi(
    "catalog_selectorized_assisted_dip", "Assisted dip machine", "Triceps", "Selectorized machine", "Assistance",
)
val customRow = ExerciseUi("custom-row", "My cable row", "Back", "Cable", "Total load", custom = true)

/** A finished workout named "Workout [id]" that ends an hour after [startedAt]. */
fun workout(id: String, startedAt: Long, vararg entries: WorkoutExerciseUi, records: List<RecordUi> = emptyList()): WorkoutUi =
    WorkoutUi(id, "Workout $id", startedAt, startedAt + HOUR, entries.toList(), records)

fun entry(exercise: ExerciseUi, vararg sets: SetUi): WorkoutExerciseUi =
    WorkoutExerciseUi(id("entry"), exercise, sets.toList())

fun set(weightKg: Double, reps: Int, warmup: Boolean = false, assistance: Boolean = false, completed: Boolean = true): SetUi =
    SetUi(id("set"), reps, weightKg, completed, warmup, assistance)

/** A weight record of [workout], dated as the presentation dates it (its end). */
fun weightRecord(exercise: ExerciseUi, weightKg: Double, reps: Int, previousKg: Double?, workout: WorkoutUi): RecordUi =
    RecordUi(exercise, weightKg, reps, previousKg, workout.id, workout.endedAt ?: workout.startedAt)

/** A reps record of [workout] at [weightKg], dated as the presentation dates it (its end). */
fun repsRecord(
    exercise: ExerciseUi, weightKg: Double, reps: Int, previousReps: Int?, workout: WorkoutUi, assistance: Boolean = false,
): RecordUi = RecordUi(
    exercise, weightKg, reps, workoutId = workout.id, date = workout.endedAt ?: workout.startedAt, kind = RecordKind.REPS,
    previousReps = previousReps, assistance = assistance,
)

/** A [UiState.records][com.mooscles.ui.UiState.records] entry: the presentation gives them no previous value. */
fun currentOf(record: RecordUi): RecordUi = record.copy(previousKg = null, previousReps = null)

fun keyOf(exercise: ExerciseUi): SeriesKey = SeriesKey(exercise.id, exercise.convention)
