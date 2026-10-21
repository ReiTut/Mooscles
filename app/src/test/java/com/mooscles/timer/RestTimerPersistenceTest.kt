package com.mooscles.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Host JVM tests against real persisted files, independent of Android or an emulator. */
class RestTimerPersistenceTest {
    private lateinit var file: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var persistence: RestTimerPersistence
    private var storeJob: Job? = null
    private var now = 1_000_000L

    @Before
    fun createStore() {
        file = Files.createTempDirectory("mooscles-timer-test-").resolve("timer.preferences_pb").toFile()
        openStore()
    }

    private fun openStore() {
        val job = SupervisorJob()
        storeJob = job
        store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file }
        )
        persistence = RestTimerPersistence(store) { now }
    }

    private suspend fun recreateStore() {
        storeJob!!.cancelAndJoin()
        openStore()
    }

    @After
    fun closeStore() = runBlocking {
        storeJob?.cancelAndJoin()
        file.delete()
        file.parentFile?.delete()
        Unit
    }

    @Test
    fun processRecreationRetainsDeadlineAndCountsBackgroundTime() = runBlocking {
        val original = persistence.start("Incline bench press", 90)
        now += 37_000
        recreateStore()
        val restored = persistence.snapshot()
        assertEquals(original.token, restored.token)
        assertEquals(original.deadline, restored.deadline)
        assertEquals("Incline bench press", restored.exerciseName)
        assertEquals(53_000L, restored.remaining(now))
        assertTrue(restored.running)
        assertFalse(restored.finished)
    }

    @Test
    fun pausedTimeDoesNotTickWhileProcessIsGoneAndResumeKeepsRemainder() = runBlocking {
        persistence.start("Squat", 120)
        now += 35_000
        persistence.pause()
        now += 600_000
        recreateStore()
        val paused = persistence.snapshot()
        assertTrue(paused.paused)
        assertFalse(paused.running)
        assertEquals(85_000L, paused.remaining(now))
        val resumed = persistence.resume()
        assertEquals(now + 85_000, resumed.deadline)
        assertEquals(85_000L, resumed.remaining(now))
        assertFalse(resumed.paused)
    }

    @Test
    fun restartedTimerRejectsStaleAlarmEvenAtSameWallClockTime() = runBlocking {
        val old = persistence.start("Bench press", 15)
        val restarted = persistence.start("Deadlift", 90)
        assertTrue(restarted.token > old.token)
        now += 100_000
        assertNull(persistence.claimCompletion(old.token))
        assertEquals("Deadlift", persistence.snapshot().exerciseName)
        assertFalse(persistence.snapshot().finished)
        assertNotNull(persistence.claimCompletion(restarted.token))
        assertTrue(persistence.snapshot().finished)
    }

    @Test
    fun expiredRestoredTimerCanOnlyBeClaimedOnceByConcurrentReceivers() = runBlocking {
        val original = persistence.start("Cable row", 30)
        now += 60_000
        recreateStore()
        val claims = coroutineScope {
            List(4) { async(Dispatchers.Default) { persistence.claimCompletion(original.token) } }.awaitAll()
        }
        assertEquals(1, claims.count { it != null })
        recreateStore()
        assertTrue(persistence.snapshot().finished)
        assertNull(persistence.claimCompletion(original.token))
    }

    @Test
    fun earlyAlarmCannotCompleteAndSkipCannotBeRevivedByOldAlarm() = runBlocking {
        val timer = persistence.start("Pull-up", 90)
        now += 10_000
        assertNull(persistence.claimCompletion(timer.token))
        assertTrue(persistence.snapshot().running)
        persistence.skip()
        now += 200_000
        recreateStore()
        assertNull(persistence.claimCompletion(timer.token))
        val skipped = persistence.snapshot()
        assertFalse(skipped.running)
        assertFalse(skipped.finished)
        assertEquals(0L, skipped.remaining(now))
    }

    @Test
    fun resetAndQuickAdjustmentSurviveRecreationWithoutChangingOtherPreferences() = runBlocking {
        val theme = stringPreferencesKey("theme")
        store.edit { it[theme] = "dark" }
        val original = persistence.start("Leg press", 75)
        now += 20_000
        val reset = persistence.reset()
        assertTrue(reset.token > original.token)
        assertTrue(reset.paused)
        assertEquals(75_000L, reset.remaining(now))
        persistence.adjust(-15)
        recreateStore()
        assertEquals(60_000L, persistence.snapshot().remaining(now))
        assertEquals("dark", store.data.first()[theme])
    }

    @Test
    fun addingTimeAfterCompletionProducesAResumablePausedTimer() = runBlocking {
        val timer = persistence.start("Curl", 15)
        now += 15_000
        assertNotNull(persistence.claimCompletion(timer.token))
        persistence.adjust(15)
        recreateStore()
        val adjusted = persistence.snapshot()
        assertFalse(adjusted.finished)
        assertTrue(adjusted.paused)
        assertEquals(15_000L, adjusted.remaining(now))
        assertEquals(now + 15_000, persistence.resume().deadline)
    }

    @Test
    fun pauseAtExpirationCannotSwallowTheCompletionAlert() = runBlocking {
        val timer = persistence.start("Row", 30)
        now += 30_000
        persistence.pause()
        recreateStore()
        assertNotNull(persistence.claimCompletion(timer.token))
        assertTrue(persistence.snapshot().finished)
    }

    @Test
    fun zeroSecondTimerCompletesOnceAndResetKeepsItStopped() = runBlocking {
        val timer = persistence.start("No rest", 0)
        assertEquals(0L, timer.remaining(now))
        assertTrue(timer.running)
        assertNotNull(persistence.claimCompletion(timer.token))
        assertNull(persistence.claimCompletion(timer.token))
        val reset = persistence.reset()
        assertFalse(reset.running)
        assertFalse(reset.paused)
        assertFalse(reset.finished)
        recreateStore()
        assertEquals(0L, persistence.snapshot().durationMillis)
        assertFalse(persistence.snapshot().running)
    }
}
