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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestTimerControllerTest {
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
}
