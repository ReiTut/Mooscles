package com.mooscles.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestTimerSessionLifecycleTest {
    private class Store : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    @Test fun noSessionRejectsTimerCommandsAndCancelsAStaleCountdownAtStartup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistence = RestTimerPersistence(Store())
        persistence.reconcileSession("finished-before-process-died")
        val stale = persistence.start("Old bench press", 120)
        val session = MutableStateFlow<String?>(null)
        val controller = RestTimerController(context, backgroundScope, persistence, { session.value }, session)
        runCurrent()
        controller.start("No session", 90)
        controller.reset()
        controller.adjust(15)
        controller.resume()
        controller.complete(stale.token)
        runCurrent()
        val saved = persistence.snapshot()
        assertFalse(saved.running)
        assertFalse(saved.paused)
        assertFalse(saved.finished)
        assertNull(saved.workoutId)
        assertEquals("Rest timer", saved.exerciseName)
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }

    @Test fun endingSessionCancelsCountdownAlarmAndExistingCompletionNotification() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        var now = System.currentTimeMillis()
        val persistence = RestTimerPersistence(Store()) { now }
        val session = MutableStateFlow<String?>("active")
        val controller = RestTimerController(context, backgroundScope, persistence, { session.value }, session)
        runCurrent()
        controller.start("Bench press", 120)
        val token = persistence.snapshot().token
        assertEquals(1, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        now += 120_000
        controller.complete(token)
        assertTrue(persistence.snapshot().finished)
        assertEquals(1, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
        session.value = null // The same persisted transition follows a successful finish or discard.
        runCurrent()
        controller.complete(token)
        assertFalse(persistence.snapshot().finished)
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }

    @Test fun restartingControllerRetainsAnUnfinishedSessionsDeadlineAndPause() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistence = RestTimerPersistence(Store())
        persistence.reconcileSession("resumable")
        val running = persistence.start("Saved deadlift", 135)
        val session = MutableStateFlow<String?>("resumable")
        RestTimerController(context, backgroundScope, persistence, { session.value }, session)
        runCurrent()
        assertEquals(running, persistence.snapshot())
        persistence.pause()
        val paused = persistence.snapshot()
        RestTimerController(context, backgroundScope, persistence, { session.value }, session)
        runCurrent()
        assertEquals(paused, persistence.snapshot())
        assertTrue(paused.paused)
        assertEquals(0, shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size)
    }

    @Test fun staleReceiverCannotAlertAfterSessionEndsEvenBeforeObserverRuns() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var now = System.currentTimeMillis()
        val persistence = RestTimerPersistence(Store()) { now }
        val session = MutableStateFlow<String?>("active")
        val controller = RestTimerController(context, backgroundScope, persistence, { session.value }, session)
        runCurrent()
        controller.start("Row", 60)
        val token = persistence.snapshot().token
        now += 60_000
        session.value = null
        controller.complete(token) // Snapshot guard applies independently of collection timing.
        assertFalse(persistence.snapshot().finished)
        assertFalse(persistence.snapshot().running)
        assertEquals(0, shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.size)
    }
}
