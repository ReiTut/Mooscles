package com.mooscles.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise identity, source identity and editable instance rest all survive a real SQLite reopen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutRoutineSourcePersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val filename = "rs-${newId().take(8)}.db"
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private lateinit var exerciseId: String

    @Before fun setup() = runBlocking {
        open()
        exerciseId = repo.createCustomExercise("Press", MuscleGroup.CHEST, Equipment.BARBELL,
            LoadConvention.TOTAL_LOAD)
    }

    private fun open() {
        database = Room.databaseBuilder(context, WorkoutDatabase::class.java, filename).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
    }

    @After fun close() {
        database.close()
        context.deleteDatabase(filename)
    }

    private suspend fun routine(name: String = "Same name"): String = repo.createRoutine(name).also {
        repo.addRoutineExercise(it, exerciseId)
    }

    private suspend fun completeFirstSet(workoutId: String) {
        val entry = repo.snapshot().workoutItems(workoutId).first()
        val row = repo.snapshot().exerciseSets(entry.id).first()
        repo.updateSet(row.copy(reps = 8, weightKg = 17.25, completed = true))
    }

    @Test fun sourceIsStableAcrossIdenticalNamesRenameReorderAndReopening() = runBlocking {
        val first = routine()
        val second = routine()
        val workout = repo.startRoutine(first, now = 1234)
        completeFirstSet(workout)
        val entry = repo.snapshot().workoutItems(workout).single()
        repo.updateWorkoutExerciseRest(entry.id, 155)
        repo.renameRoutine(first, "Renamed source")
        repo.reorderRoutines(listOf(second, first))
        val expected = repo.snapshot()
        assertEquals(first, expected.activeWorkout!!.sourceRoutineId)
        assertEquals("Same name", expected.activeWorkout!!.name)
        assertEquals(listOf(second, first), expected.routines.map { it.id })

        database.close()
        open()
        repo.initialize()
        val restored = repo.snapshot()
        assertEquals(expected.workouts, restored.workouts)
        assertEquals(expected.workoutExercises, restored.workoutExercises)
        assertEquals(expected.sets, restored.sets)
        assertEquals(first, restored.activeWorkout!!.sourceRoutineId)
        assertEquals(1234L, restored.activeWorkout!!.startedAt)
        assertEquals(155, restored.workoutItems(workout).single().restSeconds)
        assertEquals(workout, repo.startRoutine(first, now = 9999))
        assertEquals(1, repo.snapshot().workouts.size)
    }

    @Test fun startingAnotherRoutineOrFreeWorkoutNeverReplacesTheActiveSession() = runBlocking {
        val first = routine()
        val second = routine()
        val workout = repo.startRoutine(first, now = 1000)
        completeFirstSet(workout)
        val before = repo.snapshot()
        assertEquals(workout, repo.startRoutine(second, now = 2000))
        assertEquals(workout, repo.startEmpty("Another workout", now = 3000))
        val after = repo.snapshot()
        assertEquals(before, after)
        assertEquals(first, after.activeWorkout!!.sourceRoutineId)
    }

    @Test fun freeWorkoutMatchingARoutineNameStillHasNoSourceAfterReopening() = runBlocking {
        val first = routine()
        routine()
        val workout = repo.startEmpty("Same name", now = 1000)
        val entry = repo.addWorkoutExercise(workout, exerciseId)
        repo.updateWorkoutExerciseRest(entry, 145)
        database.close()
        open()
        assertNull(repo.snapshot().activeWorkout!!.sourceRoutineId)
        assertEquals(workout, repo.startRoutine(first, now = 2000))
        assertNull(repo.snapshot().activeWorkout!!.sourceRoutineId)
        assertEquals(1, repo.snapshot().workouts.size)
    }

    @Test fun finishedOriginSurvivesRoutineDeletionWhileNewFreeSessionHasNoAssociation() = runBlocking {
        val source = routine()
        val workout = repo.startRoutine(source, now = 1000)
        completeFirstSet(workout)
        assertTrue(repo.finishWorkout(workout, now = 3000))
        val finished = repo.snapshot()
        assertNull(finished.activeWorkout)
        assertEquals(source, finished.workouts.single().sourceRoutineId)
        repo.deleteRoutine(source)
        val next = repo.startEmpty("Same name", now = 4000)
        assertNull(repo.snapshot().activeWorkout!!.sourceRoutineId)
        assertEquals(source, repo.snapshot().workouts.single { it.id == workout }.sourceRoutineId)
        assertEquals(finished.workoutExercises, repo.snapshot().workoutExercises)
        assertEquals(finished.sets, repo.snapshot().sets)
        repo.discardWorkout(next)
        database.close()
        open()
        val restored = repo.snapshot()
        assertNull(restored.activeWorkout)
        assertEquals(finished.workouts, restored.workouts)
        assertEquals(finished.workoutExercises, restored.workoutExercises)
        assertEquals(finished.sets, restored.sets)
    }

    @Test fun discardingRoutineSessionAllowsAnotherSourceWithoutCreatingOrphanSnapshots() = runBlocking {
        val first = routine()
        val second = routine()
        val discarded = repo.startRoutine(first, now = 1000)
        completeFirstSet(discarded)
        repo.discardWorkout(discarded)
        val cleared = repo.snapshot()
        assertNull(cleared.activeWorkout)
        assertTrue(cleared.workouts.isEmpty())
        assertTrue(cleared.workoutExercises.isEmpty())
        assertTrue(cleared.sets.isEmpty())
        assertEquals(2, cleared.routines.size)
        val replacement = repo.startRoutine(second, now = 3000)
        assertNotEquals(discarded, replacement)
        assertEquals(second, repo.snapshot().activeWorkout!!.sourceRoutineId)
        assertEquals(1, repo.snapshot().workouts.size)
    }

    @Test fun restOverridePersistsForOnlyTheSelectedInstanceAndLeavesRoutineAndPerformanceIntact() = runBlocking {
        val routine = routine()
        val firstPlan = repo.snapshot().routineItems(routine).single()
        val secondPlanId = repo.addRoutineExercise(routine, exerciseId)
        repo.updateRoutineExerciseRest(firstPlan.id, 135)
        repo.updateRoutineExerciseRest(secondPlanId, 90)
        val workout = repo.startRoutine(routine, now = 1000)
        val entries = repo.snapshot().workoutItems(workout)
        completeFirstSet(workout)
        val selected = entries[0]
        repo.updateWorkoutExercise(selected.copy(name = "Instance-specific press"))
        val before = repo.snapshot()
        repo.updateWorkoutExerciseRest(selected.id, 0)
        assertEquals(0, repo.snapshot().workoutItems(workout)[0].restSeconds)
        repo.updateWorkoutExerciseRest(selected.id, 300)
        for (invalid in listOf(-1, 301, Int.MAX_VALUE)) {
            try {
                repo.updateWorkoutExerciseRest(selected.id, invalid)
                fail("Rest outside 0:00..5:00 must not be saved")
            } catch (_: IllegalArgumentException) { }
        }
        val changed = repo.snapshot()
        assertEquals(before.workoutExercises[0].copy(restSeconds = 300), changed.workoutExercises[0])
        assertEquals(entries[1], changed.workoutItems(workout)[1])
        assertEquals(before.routineExercises, changed.routineExercises)
        assertEquals(before.routineSets, changed.routineSets)
        assertEquals(before.sets, changed.sets)
        assertEquals(before.workouts, changed.workouts)

        database.close()
        open()
        repo.initialize()
        val restored = repo.snapshot()
        assertEquals(changed.workoutExercises, restored.workoutExercises)
        assertEquals(changed.sets, restored.sets)
        assertEquals(listOf(135, 90), restored.routineItems(routine).map { it.restSeconds })
        repo.finishWorkout(workout, now = 5000)
        val next = repo.startRoutine(routine, now = 6000)
        assertEquals(listOf(135, 90), repo.snapshot().workoutItems(next).map { it.restSeconds })
        assertEquals(listOf(300, 90), repo.snapshot().workoutItems(workout).map { it.restSeconds })
        assertEquals(changed.sets, repo.snapshot().sets.filter { it.workoutExerciseId in entries.map { entry -> entry.id } })
    }
}
