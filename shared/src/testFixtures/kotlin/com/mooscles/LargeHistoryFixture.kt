package com.mooscles

import com.mooscles.data.AppData
import com.mooscles.data.Exercise
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus

/** About four years of weekly-ish training: 208 finished workouts × 61 sets over six exercises (12,688 sets). */
internal object LargeHistoryFixture {
    const val WORKOUTS = 208
    const val SETS_PER_WORKOUT = 61
    const val SETS = WORKOUTS * SETS_PER_WORKOUT

    class Rows(val workouts: List<Workout>, val entries: List<WorkoutExercise>, val sets: List<WorkoutSet>)

    /** Six built-in total-load exercises, the same kind the Room-backed tests take from the seeded catalog. */
    val defaultExercises: List<Exercise>
        get() = ExerciseCatalog.exercises.filter { !it.custom && it.convention == LoadConvention.TOTAL_LOAD }.take(6)

    /** A user who logs many movements: [count] distinct built-in exercises of every load convention. */
    fun variedExercises(count: Int): List<Exercise> = ExerciseCatalog.exercises.filter { !it.custom }.take(count)
        .also { require(it.size == count) { "catalog has only ${it.size} exercises" } }

    fun rows(exercises: List<Exercise>): Rows {
        val workouts = ArrayList<Workout>()
        val items = ArrayList<WorkoutExercise>()
        val sets = ArrayList<WorkoutSet>()
        repeat(WORKOUTS) { day ->
            val start = 1_700_000_000_000L + day * 86_400_000L
            val workout = Workout(name = "Session $day", startedAt = start, endedAt = start + 3_600_000L, status = WorkoutStatus.FINISHED)
            workouts += workout
            val entries = exercises.mapIndexed { position, exercise ->
                WorkoutExercise(workoutId = workout.id, exerciseId = exercise.id, name = exercise.name, muscle = exercise.muscle,
                    equipment = exercise.equipment, convention = exercise.convention, position = position)
            }
            items += entries
            repeat(SETS_PER_WORKOUT) { index ->
                val entry = entries[index % entries.size]
                sets += WorkoutSet(workoutExerciseId = entry.id, position = index / entries.size, reps = 5 + index % 8,
                    weightKg = 20.0 + (day % 40) * 2.5, completed = true, warmup = index % 10 == 0)
            }
        }
        return Rows(workouts, items, sets)
    }

    fun appData(exercises: List<Exercise> = defaultExercises): AppData {
        val rows = rows(exercises)
        return AppData(exercises = exercises, workouts = rows.workouts, workoutExercises = rows.entries, sets = rows.sets)
    }
}
