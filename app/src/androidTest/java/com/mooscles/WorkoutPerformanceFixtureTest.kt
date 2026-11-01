package com.mooscles

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit, deterministic setup for a disposable performance device, not a
 * benchmark. Normal instrumentation runs skip it. Refuses real workout data.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutPerformanceFixtureTest {
    @Test
    fun seedDisposableDevice() = runBlocking(Dispatchers.IO) {
        val size = InstrumentationRegistry.getArguments().getString("performanceFixture")
        assumeTrue("Pass -e performanceFixture typical|large on a disposable test device", size in listOf("typical", "large"))
        val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val repo = app.repository
        repo.initialize()
        val before = repo.snapshot()
        require(before.routines.all { it.id.startsWith(PREFIX) } && before.workouts.all { it.id.startsWith(PREFIX) }) {
            "Performance seeding refuses a device with non-fixture routines or workouts. Use a separate test device."
        }
        // Each occurrence deliberately has a unique identity even when its
        // exercise repeats; the large fixture exposes position/identity bugs.
        val builtIns = before.exercises.filterNot { it.custom }.sortedBy { it.id }
        val weighted = builtIns.filter { it.convention == LoadConvention.TOTAL_LOAD }.take(7)
        val bodyweight = builtIns.first { it.convention == LoadConvention.BODYWEIGHT }
        require(weighted.size == 7)
        val catalog = weighted + bodyweight
        val exerciseCount = if (size == "large") 80 else 8
        val setCount = if (size == "large") 12 else 4
        val historyCount = if (size == "large") 200 else 30
        val dao = app.database.dao()
        app.database.writeTransaction {
            before.workouts.forEach { dao.deleteWorkout(it.id) }
            before.routines.forEach { dao.deleteRoutine(it.id) }
            val routine = Routine("${PREFIX}routine", "Performance $size", position = 0)
            dao.insertRoutine(routine)
            repeat(exerciseCount) { index ->
                val exercise = catalog[index % catalog.size]
                val entryId = "${PREFIX}plan-$index"
                val load = if (exercise.convention == LoadConvention.BODYWEIGHT) null else 20.0 + index % 5
                dao.insertRoutineExercise(RoutineExercise(entryId, routine.id, exercise.id, index,
                    plannedSets = setCount, targetReps = 8, targetWeightKg = load, restSeconds = 120))
                repeat(setCount) { set ->
                    dao.insertRoutineSet(RoutineSet("${PREFIX}plan-$index-set-$set", entryId, set,
                        targetReps = 8 + set % 3, targetWeightKg = load, inheritPr = false))
                }
            }
            // Fixed timestamps and values make the database identical between
            // captures; setup is never included in a Start/scroll measurement.
            repeat(historyCount) { session ->
                val started = 1_750_000_000_000L + session * 86_400_000L
                val workout = Workout("${PREFIX}history-$session", "Performance history $session", started,
                    endedAt = started + 2_700_000L, status = WorkoutStatus.FINISHED)
                dao.insertWorkout(workout)
                repeat(if (size == "large") 12 else 8) { index ->
                    val exercise = catalog[index % catalog.size]
                    val entry = WorkoutExercise("${PREFIX}history-$session-exercise-$index", workout.id,
                        exercise.id, exercise.name, exercise.muscle, exercise.equipment, exercise.convention,
                        position = index, restSeconds = 120)
                    dao.insertWorkoutExercise(entry)
                    repeat(4) { set ->
                        dao.insertSet(WorkoutSet("${entry.id}-set-$set", entry.id, set,
                            reps = 8 + set % 3,
                            weightKg = if (exercise.convention == LoadConvention.BODYWEIGHT) 0.0 else 15.0 + session % 10,
                            completed = true, warmup = set == 0))
                    }
                }
            }
        }
        app.timer.clear()
        app.preferences.pounds(false)
        app.preferences.autoRest(true)
        app.preferences.defaultRest(120)
        val seeded = repo.snapshot()
        assertNull(seeded.activeWorkout)
        assertEquals(exerciseCount, seeded.routineExercises.size)
        assertEquals(exerciseCount * setCount, seeded.routineSets.size)
        assertEquals(historyCount, seeded.workouts.size)
        assertEquals(listOf(8, 9, 10, 8), seeded.plannedSets("${PREFIX}plan-0").take(4).map { it.targetReps })
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("fixture", size)
            putString("routineId", "${PREFIX}routine")
            putInt("exerciseCount", exerciseCount)
            putInt("setsPerExercise", setCount)
            putInt("historyCount", historyCount)
        })
    }

    private companion object { const val PREFIX = "performance-fixture-" }
}
