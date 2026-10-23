package com.mooscles.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Instance ownership is additive, durable, and never inferred from a repeated display name. */
class RestTimerExerciseOwnershipPersistenceTest {
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var persistence: RestTimerPersistence
    private var job: Job? = null
    private var now = 1_000_000L

    @Before fun setup() {
        file = Files.createTempDirectory("mooscles-timer-owner-").resolve("timer.preferences_pb").toFile()
        open()
    }

    private fun open() {
        job = SupervisorJob()
        store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job!! + Dispatchers.IO), produceFile = { file })
        persistence = RestTimerPersistence(store) { now }
    }

    private suspend fun reopen() {
        job!!.cancelAndJoin()
        open()
    }

    @After fun close() = runBlocking {
        job?.cancelAndJoin()
        file.delete()
        file.parentFile?.delete()
        Unit
    }

    @Test fun ownerAndAdjustedCountdownSurviveRealFileReopenPauseResumeAndReset() = runBlocking {
        persistence.reconcileSession("active-workout")
        val started = persistence.start("Repeated press", 120, "second-press-instance")
        now += 20_000
        persistence.adjust(15)
        val adjusted = persistence.snapshot()
        reopen()
        assertEquals(adjusted, persistence.snapshot())
        assertEquals("second-press-instance", persistence.snapshot().workoutExerciseId)
        assertEquals(started.token, persistence.snapshot().token)
        assertEquals(115_000L, persistence.snapshot().remaining(now))
        persistence.pause()
        val paused = persistence.snapshot()
        now += 600_000
        reopen()
        assertEquals(paused, persistence.snapshot())
        persistence.resume()
        assertEquals("second-press-instance", persistence.snapshot().workoutExerciseId)
        persistence.reset()
        val reset = persistence.snapshot()
        assertEquals("second-press-instance", reset.workoutExerciseId)
        assertEquals("active-workout", reset.workoutId)
        assertEquals(120_000L, reset.remaining(now))
        reopen()
        assertEquals(reset, persistence.snapshot())
    }

    @Test fun oldNameOnlyCountdownKeepsAnUnknownOwnerInsteadOfGuessingBetweenDuplicates() = runBlocking {
        store.edit {
            it[longPreferencesKey("timer_token")] = 71
            it[stringPreferencesKey("timer_name")] = "Repeated press"
            it[longPreferencesKey("timer_duration")] = 120_000
            it[longPreferencesKey("timer_deadline")] = now + 90_000
            it[stringPreferencesKey("timer_workout")] = "legacy-workout"
        }
        reopen()
        val legacy = persistence.snapshot()
        assertNull(legacy.workoutExerciseId)
        assertEquals(90_000L, legacy.remaining(now))
        assertEquals(legacy, persistence.reconcileSession("legacy-workout"))
        persistence.pause()
        persistence.adjust(15)
        persistence.resume()
        reopen()
        assertNull(persistence.snapshot().workoutExerciseId)
        assertEquals("legacy-workout", persistence.snapshot().workoutId)
        assertEquals("Repeated press", persistence.snapshot().exerciseName)
    }

    @Test fun newCompletedSetUsesItsExactInstanceAndAnonymousManualStartDoesNotRetainPreviousOwner() = runBlocking {
        persistence.reconcileSession("active-workout")
        val first = persistence.start("Repeated press", 120, "first-instance")
        val second = persistence.start("Repeated press", 120, "second-instance")
        assertTrue(second.token > first.token)
        assertEquals("second-instance", second.workoutExerciseId)
        reopen()
        assertEquals(second, persistence.snapshot())
        val manual = persistence.start("Repeated press", 120)
        assertNull(manual.workoutExerciseId)
        reopen()
        assertNull(persistence.snapshot().workoutExerciseId)
        assertEquals("active-workout", persistence.snapshot().workoutId)
    }

    @Test fun completionRetainsOwnerButSessionClearAndReplacementRemoveItDurably() = runBlocking {
        persistence.reconcileSession("first-workout")
        val running = persistence.start("Repeated press", 30, "first-instance")
        now += 30_000
        assertEquals("first-instance", persistence.claimCompletion(running.token)!!.workoutExerciseId)
        assertEquals("first-instance", persistence.snapshot().workoutExerciseId)
        persistence.clear()
        reopen()
        assertNull(persistence.snapshot().workoutExerciseId)
        assertNull(persistence.snapshot().workoutId)
        assertNull(persistence.claimCompletion(running.token))
        persistence.reconcileSession("second-workout")
        persistence.start("Repeated press", 120, "second-instance")
        persistence.reconcileSession("replacement-workout")
        reopen()
        assertEquals("replacement-workout", persistence.snapshot().workoutId)
        assertNull(persistence.snapshot().workoutExerciseId)
        assertFalse(persistence.snapshot().running)
        assertFalse(persistence.snapshot().finished)
    }
}
