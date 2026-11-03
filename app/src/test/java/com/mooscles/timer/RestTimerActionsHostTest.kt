package com.mooscles.timer

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UpdatePhase
import com.mooscles.ui.UpdateUi
import com.mooscles.updates.UpdateCoordinator
import com.mooscles.updates.UpdateManager
import com.mooscles.updates.UpdateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Decision 24: the notification's actions follow the screen's rules. ±15 s take the training gate and
 * are refused while an update installs; Skip does not take the gate and lets a deferred update go on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestTimerActionsHostTest {
    private class Store : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private class RecordingTimer(private val gate: Mutex, private val calls: MutableList<String>) : RestTimer {
        override val ui: StateFlow<TimerUi> = MutableStateFlow(TimerUi())
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override val errorCode: StateFlow<RestTimerError?> = MutableStateFlow(null)
        override fun clearError() {}
        override suspend fun start(name: String, seconds: Int, workoutExerciseId: String?) { calls += "start" }
        override suspend fun clear() { calls += "clear" }
        override suspend fun clearForSessionEnd() { calls += "clearForSessionEnd" }
        override suspend fun pause() { calls += "pause" }
        override suspend fun resume() { calls += "resume" }
        override suspend fun reset() { calls += "reset" }
        override suspend fun skip() { calls += "skip:${if (gate.isLocked) "locked" else "free"}" }
        override suspend fun adjust(seconds: Int) { calls += "adjust:$seconds:${if (gate.isLocked) "locked" else "free"}" }
    }

    private class RecordingUpdates(private val calls: MutableList<String>) : UpdateCoordinator {
        override val ui: Flow<UpdateUi> = emptyFlow()
        override suspend fun onTrainingChanged() { calls += "onTrainingChanged" }
        override suspend fun assertTrainingMayStart() { calls += "assertTrainingMayStart" }
        override suspend fun requestCheck(manual: Boolean) {}
        override suspend fun setAutomatic(enabled: Boolean) {}
        override suspend fun setWifiOnly(enabled: Boolean) {}
        override suspend fun setPrereleases(enabled: Boolean) {}
        override suspend fun enqueueDownload(manual: Boolean) {}
        override suspend fun cancelDownload(markCancelled: Boolean) {}
    }

    @Test fun plusFifteenRunsUnderTheGate() = runTest {
        val gate = Mutex()
        val calls = mutableListOf<String>()
        val actions = RestTimerActions(gate, RecordingUpdates(calls), RecordingTimer(gate, calls))
        actions.plus15()
        actions.minus15()
        assertEquals(listOf("assertTrainingMayStart", "adjust:15:locked", "assertTrainingMayStart", "adjust:-15:locked"), calls)

        // While another training command holds the gate, the action waits for it.
        calls.clear()
        gate.lock()
        val waiting = launch { actions.plus15() }
        runCurrent()
        assertEquals(emptyList<String>(), calls)
        gate.unlock()
        waiting.join()
        assertEquals(listOf("assertTrainingMayStart", "adjust:15:locked"), calls)
    }

    @Test fun notificationActionIsRefusedWhileAnUpdateInstalls() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val gate = Mutex()
        val updateStore = UpdateStore(Store())
        updateStore.change { it.copy(phase = UpdatePhase.INSTALLING) }
        val updates = UpdateManager(app, gate, trainingBusy = { true }, store = updateStore)
        val persistence = RestTimerPersistence(Store())
        val timer = RestTimerController(app, backgroundScope, persistence, activeWorkoutId = { "active" },
            sessionChanges = flowOf("active"), pendingSet = { null })
        runCurrent()
        timer.start("Bench press", 90, "entry")
        runCurrent()
        val before = persistence.snapshot()

        val actions = RestTimerActions(gate, updates, timer)
        // The receiver has no screen to tell: a refusal leaves the timer, and the notification, as they were.
        actions.perform(RestNotifications.ACTION_PLUS)
        actions.perform(RestNotifications.ACTION_MINUS)
        runCurrent()
        assertEquals(before, persistence.snapshot())
        assertFalse(gate.isLocked)
        assertNull(timer.error.value)

        // Once the installation is over, the same action applies.
        updateStore.change { it.copy(phase = UpdatePhase.IDLE) }
        actions.perform(RestNotifications.ACTION_PLUS)
        runCurrent()
        assertEquals(15_000.0, (persistence.snapshot().deadline - before.deadline).toDouble(), 1_000.0)
    }

    @Test fun aGateHeldTooLongDropsTheNotificationAction() = runTest {
        val gate = Mutex()
        val calls = mutableListOf<String>()
        val actions = RestTimerActions(gate, RecordingUpdates(calls), RecordingTimer(gate, calls))
        gate.lock() // An import or an installation holds the gate past the broadcast's budget.
        val started = testScheduler.currentTime
        actions.perform(RestNotifications.ACTION_PLUS)
        // Dropped within the goAsync budget, without throwing and without taking the gate from its holder.
        assertEquals(RestTimerActions.RECEIVER_TIMEOUT_MILLIS, testScheduler.currentTime - started)
        assertTrue(RestTimerActions.RECEIVER_TIMEOUT_MILLIS < 10_000)
        assertEquals(emptyList<String>(), calls)
        assertTrue(gate.isLocked)
        gate.unlock()
        assertFalse(gate.isLocked)
        actions.perform(RestNotifications.ACTION_PLUS)
        assertEquals(listOf("assertTrainingMayStart", "adjust:15:locked"), calls)
    }

    @Test fun skipCallsOnTrainingChanged() = runTest {
        val gate = Mutex()
        val calls = mutableListOf<String>()
        val actions = RestTimerActions(gate, RecordingUpdates(calls), RecordingTimer(gate, calls))
        // Skip never waits for the gate, as on the screen: ending a rest is always allowed.
        gate.lock()
        actions.skip()
        gate.unlock()
        assertEquals(listOf("skip:locked", "onTrainingChanged"), calls)
    }

    @Test fun eachActionNameRunsItsCommand() = runTest {
        val gate = Mutex()
        val calls = mutableListOf<String>()
        val actions = RestTimerActions(gate, RecordingUpdates(calls), RecordingTimer(gate, calls))
        actions.perform(RestNotifications.ACTION_MINUS)
        actions.perform(RestNotifications.ACTION_PLUS)
        actions.perform(RestNotifications.ACTION_SKIP)
        actions.perform("com.mooscles.timer.UNKNOWN")
        actions.perform(null)
        assertEquals(listOf("assertTrainingMayStart", "adjust:-15:locked", "assertTrainingMayStart", "adjust:15:locked",
            "skip:free", "onTrainingChanged"), calls)
    }
}
