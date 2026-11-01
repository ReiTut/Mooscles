package com.mooscles.screens.progress

import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi

/**
 * A large `UiState` for timing the progress index: the shape of the `LargeHistoryFixture` of `:shared` (208 finished
 * workouts × 61 sets, about four years of weekly-ish training) over catalog exercises of every load convention.
 *
 * The records are made here, not by the domain: a weight record when the workout's heaviest working set passes the
 * best before it, and a reps record when a load's most reps pass the reps before at that load (the repetitions shift
 * once every 40 days, so later rounds beat earlier ones). That is enough for the timing; whether the index agrees with
 * the real domain is `ProgressIndexDomainHostTest`'s job, in `:app`.
 */
object LargeProgressHistory {
    const val WORKOUTS = 208
    const val SETS_PER_WORKOUT = 61
    private const val START = 1_700_000_000_000L

    /** `UiState.history` (newest first) and `UiState.records` (the current record of each key). */
    fun state(exercises: Int = 48): Pair<List<WorkoutUi>, List<RecordUi>> = state(exercises, WORKOUTS)

    /** [state] over [workouts] workouts instead of 208. */
    fun state(exercises: Int, workouts: Int): Pair<List<WorkoutUi>, List<RecordUi>> {
        val chosen = exercises(exercises)
        val bestLoad = HashMap<SeriesKey, Double>()
        val repsAt = HashMap<Triple<SeriesKey, Boolean, Double>, Int>()
        val currentWeight = LinkedHashMap<SeriesKey, RecordUi>()
        val currentReps = LinkedHashMap<Triple<SeriesKey, Boolean, Double>, RecordUi>()
        val finished = List(workouts) { day ->
            val id = "w$day"
            val start = START + day * DAY
            val end = start + HOUR
            val sets = List(chosen.size) { ArrayList<SetUi>() }
            repeat(SETS_PER_WORKOUT) { index ->
                sets[index % chosen.size] += SetUi(
                    id = "$id-s$index",
                    reps = 5 + (index + day / 40) % 8,
                    weightKg = 20.0 + (day % 40) * 2.5,
                    completed = true,
                    warmup = index % 10 == 0,
                )
            }
            val entries = chosen.mapIndexed { position, exercise -> WorkoutExerciseUi("$id-e$position", exercise, sets[position]) }
            val records = ArrayList<RecordUi>()
            entries.forEach { entry ->
                val key = SeriesKey(entry.exercise.id, entry.exercise.convention)
                val convention = WorkingSets.convention(key.convention)
                entry.sets.filter { WorkingSets.forLoad(it, convention) }
                    .maxWithOrNull(compareBy<SetUi> { it.weightKg }.thenBy { it.reps })?.let { best ->
                        val previous = bestLoad[key]
                        if (previous == null || best.weightKg > previous) {
                            bestLoad[key] = best.weightKg
                            val record = RecordUi(entry.exercise, best.weightKg, best.reps, previous, id, end)
                            records += record
                            currentWeight[key] = record.copy(previousKg = null)
                        }
                    }
                val assisted = convention == LoadConvention.ASSISTANCE
                entry.sets.filter { WorkingSets.forReps(it, assisted, convention) }.groupBy(SetUi::weightKg)
                    .forEach { (load, atLoad) ->
                        val most = atLoad.maxOf(SetUi::reps)
                        val slot = Triple(key, assisted, load)
                        val previous = repsAt[slot]
                        if (previous == null || most > previous) {
                            repsAt[slot] = most
                            val record = RecordUi(entry.exercise, load, most, workoutId = id, date = end,
                                kind = RecordKind.REPS, previousReps = previous, assistance = assisted)
                            records += record
                            currentReps[slot] = record.copy(previousReps = null)
                        }
                    }
            }
            WorkoutUi(id, "Session $day", start, end, entries, records)
        }
        return finished.asReversed() to (currentWeight.values + currentReps.values)
    }

    /** [count] catalog exercises, taken in turn from each load convention so that every one is in. */
    fun exercises(count: Int): List<ExerciseUi> {
        val byConvention = ExerciseCatalog.exercises.filter { !it.custom }.groupBy { it.convention }.values.map { it.iterator() }
        val chosen = ArrayList<ExerciseUi>()
        while (chosen.size < count && byConvention.any { it.hasNext() }) {
            byConvention.forEach { exercises ->
                if (chosen.size < count && exercises.hasNext()) {
                    val it = exercises.next()
                    chosen += ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label)
                }
            }
        }
        require(chosen.size == count) { "catalog has only ${chosen.size} exercises" }
        return chosen
    }
}
