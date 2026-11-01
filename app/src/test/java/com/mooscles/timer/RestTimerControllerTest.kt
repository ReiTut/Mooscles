package com.mooscles.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestTimerControllerTest {
    private class Store : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private class UnavailableStore : DataStore<Preferences> {
        var available = false
        var reads = 0
        var writes = 0
        var stored: Preferences = emptyPreferences()
        override val data: Flow<Preferences> = flow {
            reads++
            if (!available) throw IOException("Storage temporarily unavailable")
            emit(stored)
        }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writes++
            if (!available) throw IOException("Storage temporarily unavailable")
            stored = transform(stored)
            return stored
        }
    }

    @Test fun inaccessiblePreferencesReportErrorWithoutCrashingOrOverwritingData() = runTest {
        val store = UnavailableStore()
        val controller = RestTimerController(ApplicationProvider.getApplicationContext<Context>(), backgroundScope,
            RestTimerPersistence(store), activeWorkoutId = { "active" }, sessionChanges = flowOf("active"))
        runCurrent()
        assertTrue(store.reads > 0)
        assertEquals(0, store.writes)
        assertTrue(controller.error.value!!.contains("Storage temporarily unavailable"))
        assertFalse(controller.ui.value.running)
        controller.clearError()
        assertNull(controller.error.value)
        controller.start("Bench press", 90)
        assertTrue(controller.error.value!!.contains("Storage temporarily unavailable"))
        assertEquals(0, store.writes)
        assertEquals(emptyPreferences(), store.stored)
        // The receiver/ticker completion path also contains failed DataStore access.
        controller.complete(123)
        assertEquals(emptyPreferences(), store.stored)
    }

    @Test fun storageFailureSetsTheErrorCode() = runTest {
        val store = UnavailableStore()
        val controller = RestTimerController(ApplicationProvider.getApplicationContext<Context>(), backgroundScope,
            RestTimerPersistence(store), activeWorkoutId = { "active" }, sessionChanges = flowOf("active"))
        assertNull(controller.errorCode.value)
        runCurrent()
        assertNotNull(controller.error.value)
        assertEquals(RestTimerError.STORAGE_UNAVAILABLE, controller.errorCode.value)

        controller.clearError()
        assertNull(controller.error.value)
        assertNull(controller.errorCode.value)

        // A failed command reports both again.
        controller.start("Bench press", 90)
        assertNotNull(controller.error.value)
        assertEquals(RestTimerError.STORAGE_UNAVAILABLE, controller.errorCode.value)
    }

    @Test fun transientReadFailureRecoversPersistedTimerOnRetry() = runTest {
        val store = UnavailableStore()
        val deadline = System.currentTimeMillis() + 120_000
        store.stored = mutablePreferencesOf(
            longPreferencesKey("timer_token") to 123L,
            stringPreferencesKey("timer_name") to "Saved squat",
            longPreferencesKey("timer_deadline") to deadline,
            longPreferencesKey("timer_duration") to 120_000L
        )
        val controller = RestTimerController(ApplicationProvider.getApplicationContext<Context>(), backgroundScope,
            RestTimerPersistence(store), activeWorkoutId = { "active" }, sessionChanges = flowOf("active"))
        runCurrent()
        assertNotNull(controller.error.value)
        store.available = true
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("Saved squat", controller.state.value.exerciseName)
        assertEquals(deadline, controller.state.value.deadline)
        assertTrue(controller.state.value.running)
        assertEquals(1, store.writes) // Attach the legacy countdown to the persisted active session.
    }

    @Test fun displayedCountdownAndProgressFollowTheSameClockAcrossControls() = runTest {
        var now = System.currentTimeMillis()
        val persistence = RestTimerPersistence(Store()) { now }
        val controller = RestTimerController(ApplicationProvider.getApplicationContext<Context>(), backgroundScope,
            persistence, activeWorkoutId = { "active" }, sessionChanges = flowOf("active"), now = { now })
        runCurrent()
        controller.start("Seated row", 90)
        runCurrent()
        assertEquals(90, controller.ui.value.remainingSeconds)
        assertEquals(1f, controller.ui.value.progress, 0f)
        now += 22_500
        advanceTimeBy(250)
        runCurrent()
        assertEquals(68, controller.ui.value.remainingSeconds)
        assertEquals(0.75f, controller.ui.value.progress, 0.00001f)
        controller.pause()
        runCurrent()
        now += 60_000
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.ui.value.paused)
        assertEquals(68, controller.ui.value.remainingSeconds)
        assertEquals(0.75f, controller.ui.value.progress, 0.00001f)
        controller.adjust(15)
        runCurrent()
        assertEquals(83, controller.ui.value.remainingSeconds)
        assertEquals(82.5f / 105f, controller.ui.value.progress, 0.00001f)
        assertEquals(90, controller.ui.value.durationSeconds) // Reset remains the exercise's original interval.
        controller.resume()
        runCurrent()
        assertEquals(83, controller.ui.value.remainingSeconds)
        assertEquals(82.5f / 105f, controller.ui.value.progress, 0.00001f)
        now += 82_500
        advanceTimeBy(250)
        runCurrent()
        assertTrue(controller.ui.value.finished)
        assertEquals(0, controller.ui.value.remainingSeconds)
        assertEquals(0f, controller.ui.value.progress, 0f)
        controller.reset()
        runCurrent()
        assertTrue(controller.ui.value.paused)
        assertEquals(90, controller.ui.value.remainingSeconds)
        assertEquals(1f, controller.ui.value.progress, 0f)
        controller.skip()
        runCurrent()
        assertEquals(0f, controller.ui.value.progress, 0f)
    }
}
