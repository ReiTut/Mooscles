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

/**
 * Undoing a removed set puts the same row back, on the real generated Room code: same id and values, at its
 * position, with the later sets moving down. Every refusal writes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutSetRestoreTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repo: WorkoutRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).setDriver(AndroidSQLiteDriver()).build()
        repo = WorkoutRepository(database)
        repo.initialize()
    }

    @After fun close() { database.close() }

    private suspend fun catalogExercise(): Exercise = repo.snapshot().exercises.filter { !it.custom }.minBy { it.id }

    private suspend fun sets(entryId: String): List<WorkoutSet> = repo.snapshot().exerciseSets(entryId)

    /** An active workout with one exercise whose sets are A, B and C, each with its own values. */
    private suspend fun threeSets(): Triple<String, String, List<WorkoutSet>> {
        val workoutId = repo.startEmpty()
        val entryId = repo.addWorkoutExercise(workoutId, catalogExercise().id)
        repo.addSet(entryId)
        val (a, b, c) = sets(entryId)
        repo.updateSet(a.copy(reps = 10, weightKg = 40.0, completed = true, warmup = true))
        repo.updateSet(b.copy(reps = 8, weightKg = 82.5, completed = true, assistance = true))
        repo.updateSet(c.copy(reps = 6, weightKg = 85.0))
        return Triple(workoutId, entryId, sets(entryId))
    }

    private suspend fun assertRefused(error: DataError, call: suspend () -> Unit) {
        val before = repo.snapshot()
        val failure = try {
            call()
            null
        } catch (e: DataRuleException) {
            e
        }
        assertNotNull("Expected the restore to be refused with $error", failure)
        assertEquals(error, failure!!.error)
        assertEquals("A refused restore must write nothing", before, repo.snapshot())
    }

    @Test fun restoreReinsertsTheSameIdAndValuesAtItsPosition() = runBlocking {
        val (_, entryId, original) = threeSets()
        val (a, b, c) = original
        repo.removeSet(b.id)
        assertEquals(listOf(a.id to 0, c.id to 1), sets(entryId).map { it.id to it.position })

        repo.restoreSet(entryId, 1, b)

        val restored = sets(entryId)
        assertEquals(listOf(a.id, b.id, c.id), restored.map { it.id })
        assertEquals(listOf(0, 1, 2), restored.map { it.position })
        // Every value comes back as it was saved: reps, load, completed, warm-up and assistance.
        assertEquals(original, restored)
        assertEquals(b, restored[1])
        assertTrue(restored[1].completed)
        assertTrue(restored[1].assistance)
    }

    @Test fun restorePastTheEndGoesLast() = runBlocking {
        val (_, entryId, original) = threeSets()
        val (a, b, c) = original
        repo.removeSet(c.id)
        assertEquals(2, sets(entryId).size)

        repo.restoreSet(entryId, 7, c)

        val restored = sets(entryId)
        assertEquals(listOf(a.id, b.id, c.id), restored.map { it.id })
        assertEquals(listOf(0, 1, 2), restored.map { it.position })
        assertEquals(c, restored.last())
    }

    @Test fun restoreRefusesAFinishedWorkout() = runBlocking {
        val (workoutId, entryId, original) = threeSets()
        val c = original[2]
        repo.removeSet(c.id)
        assertTrue(repo.finishWorkout(workoutId))
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreSet(entryId, 2, c) }
    }

    @Test fun restoreRefusesARemovedExercise() = runBlocking {
        val (_, entryId, original) = threeSets()
        val b = original[1]
        repo.removeSet(b.id)
        repo.removeWorkoutExercise(entryId)
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreSet(entryId, 1, b) }
        assertNull(repo.workoutSet(b.id))
    }

    @Test fun restoreRefusesAnExistingId() = runBlocking {
        val (_, entryId, original) = threeSets()
        val b = original[1]
        repo.removeSet(b.id)
        repo.restoreSet(entryId, 1, b)
        // A second undo of the same removal is refused and leaves one copy at its place.
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreSet(entryId, 1, b) }
        assertRefused(DataError.RESTORE_UNAVAILABLE) { repo.restoreSet(entryId, 0, b) }
        assertEquals(original, sets(entryId))
    }

    @Test fun restoreRefusesTheHundredAndFirstSet() = runBlocking {
        val (_, entryId, original) = threeSets()
        val b = original[1]
        repo.removeSet(b.id)
        while (sets(entryId).size < 100) repo.addSet(entryId)
        assertRefused(DataError.SET_LIMIT) { repo.restoreSet(entryId, 1, b) }
        assertNull(repo.workoutSet(b.id))
    }

    @Test fun restoreValidatesValues() = runBlocking {
        val (_, entryId, original) = threeSets()
        val b = original[1]
        repo.removeSet(b.id)
        assertRefused(DataError.INVALID_VALUE) { repo.restoreSet(entryId, 1, b.copy(reps = -1)) }
        assertNull(repo.workoutSet(b.id))
    }

    @Test fun failedRestoreLeavesPositionsUntouched() = runBlocking {
        val (_, entryId, original) = threeSets()
        val (a, b, c) = original
        repo.removeSet(a.id)
        val before = sets(entryId)
        assertEquals(listOf(b.id to 0, c.id to 1), before.map { it.id to it.position })
        // Position 0 would move both later sets; the invalid load is refused before any of them moves.
        assertRefused(DataError.INVALID_VALUE) { repo.restoreSet(entryId, 0, a.copy(weightKg = Double.NaN)) }
        assertEquals(before, sets(entryId))
    }
}
