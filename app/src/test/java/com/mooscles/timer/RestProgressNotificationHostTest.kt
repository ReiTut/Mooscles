package com.mooscles.timer

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import com.mooscles.MainActivity
import com.mooscles.MoosclesApplication
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * D16: while Mooscles is in the background with a rest running or paused, a silent notification on
 * `rest_progress` counts it down with the system chronometer and offers −15 s, +15 s and Skip. The
 * end-of-rest notification keeps id 1 on the `rest` channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestProgressNotificationHostTest {
    private class Store : DataStore<Preferences> {
        private val mutex = Mutex()
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var now = System.currentTimeMillis()
    private val pending = mutableMapOf("entry" to 2)

    @Before fun grant() { shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) }

    private fun persistence(store: Store = Store()) = RestTimerPersistence(store) { now }
    private fun TestScope.controller(persistence: RestTimerPersistence, foreground: Boolean = false) =
        controllerIn(backgroundScope, persistence, foreground)
    private fun controllerIn(scope: CoroutineScope, persistence: RestTimerPersistence, foreground: Boolean) =
        RestTimerController(app, scope, persistence, activeWorkoutId = { "active" }, sessionChanges = flowOf("active"),
            now = { now }, pendingSet = { pending[it] }).also { it.foreground = foreground }

    private fun progress(): Notification? = shadowOf(manager).getNotification(RestNotifications.PROGRESS_ID)
    private fun finished(): Notification? = shadowOf(manager).getNotification(1)
    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun TestScope.settle() { advanceTimeBy(250); runCurrent() }

    @Test fun backgroundWithARunningTimerPostsOngoingOnRestProgress() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        val posted = progress()!!
        assertEquals(RestNotifications.PROGRESS_CHANNEL, posted.channelId)
        val channel = manager.getNotificationChannel(RestNotifications.PROGRESS_CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(timer.state.value.deadline, posted.`when`)
        assertEquals("Rest", posted.title())
        assertEquals("Bench press · next set 2", posted.text())
        assertEquals(listOf("−15 s", "+15 s", "Skip"), posted.actions.map { it.title.toString() })
        val expected = listOf(RestNotifications.ACTION_MINUS, RestNotifications.ACTION_PLUS, RestNotifications.ACTION_SKIP)
        posted.actions.zip(expected).forEach { (action, name) ->
            val intent = shadowOf(action.actionIntent)
            assertTrue(intent.isBroadcast)
            assertTrue(intent.flags and PendingIntent.FLAG_IMMUTABLE != 0)
            assertEquals(name, intent.savedIntent.action)
            assertEquals(ComponentName(app, RestTimerActionReceiver::class.java), intent.savedIntent.component)
        }
    }

    @Test fun runningRestTimesOutJustAfterItsEnd() = runTest {
        // With an inexact alarm and the process gone, the chronometer would count below zero: the
        // system removes the notification a moment after the rest ends instead.
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        assertEquals(90_000 + RestNotifications.PROGRESS_TIMEOUT_MARGIN_MILLIS, progress()!!.timeoutAfter)
        now += 30_000
        timer.adjust(15)
        settle()
        assertEquals(75_000 + RestNotifications.PROGRESS_TIMEOUT_MARGIN_MILLIS, progress()!!.timeoutAfter)
        // A paused rest has no end, so it never times out.
        timer.pause()
        settle()
        assertEquals(0L, progress()!!.timeoutAfter)
    }

    @Test fun withoutAPendingSetTheTextIsTheName() = runTest {
        pending.clear()
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        assertEquals("Bench press", progress()!!.text())
    }

    @Test fun pausedShowsAFixedTime() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        now += 22_000
        timer.pause()
        settle()
        val posted = progress()!!
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(posted.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertEquals("1:08 · paused", posted.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        assertEquals("Bench press · next set 2", posted.text())
        assertEquals(3, posted.actions.size)
    }

    @Test fun foregroundCancelsIt() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        assertNotNull(progress())
        timer.foreground = true
        settle()
        assertNull(progress())
        // Leaving the app again shows it again.
        timer.foreground = false
        settle()
        assertNotNull(progress())
    }

    @Test fun nothingIsPostedInTheForeground() = runTest {
        val timer = controller(persistence(), foreground = true)
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        assertNull(progress())
    }

    @Test fun skipFinishAndDiscardCancelIt() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        assertNotNull(progress())
        timer.skip()
        settle()
        assertNull(progress())

        timer.start("Bench press", 30, "entry")
        settle()
        assertNotNull(progress())
        now += 30_000
        settle() // The ticker claims the completion.
        assertNull(progress())
        assertNotNull(finished())

        timer.start("Bench press", 90, "entry")
        settle()
        assertNotNull(progress())
        timer.clearForSessionEnd() // Finishing and discarding the workout both end the session this way.
        settle()
        assertNull(progress())
    }

    @Test fun onlyStateChangesUpdateIt() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 90, "entry")
        settle()
        val first = progress()!!
        repeat(8) { now += 1000; settle() }
        assertSame("A tick must not post it again", first, progress())
        timer.adjust(15)
        settle()
        val adjusted = progress()!!
        assertNotSame(first, adjusted)
        assertEquals(timer.state.value.deadline, adjusted.`when`)
        assertEquals(first.`when` + 15_000, adjusted.`when`)
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun textsArePortugueseOnAPtBrDevice() = runTest {
        pending["entry"] = 5
        val timer = controller(persistence())
        runCurrent()
        timer.start("Supino reto", 90, "entry")
        settle()
        val posted = progress()!!
        assertEquals("Descanso", posted.title())
        assertEquals("Supino reto · a seguir série 5", posted.text())
        assertEquals(listOf("−15 s", "+15 s", "Pular"), posted.actions.map { it.title.toString() })
        assertEquals("Timer de descanso", manager.getNotificationChannel("rest").name)
        assertEquals("Descanso em andamento", manager.getNotificationChannel(RestNotifications.PROGRESS_CHANNEL).name)
        now += 22_000
        timer.pause()
        settle()
        assertEquals("1:08 · pausado", progress()!!.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        timer.resume()
        settle()
        now += 68_000
        settle()
        assertEquals("Descanso acabou", finished()!!.title())
        assertEquals("Próxima: Supino reto, série 5", finished()!!.text())
    }

    @Test fun restChannelKeepsItsIdAndImportance() = runTest {
        controller(persistence())
        runCurrent()
        val rest = manager.getNotificationChannel("rest")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, rest.importance)
        assertTrue(rest.shouldVibrate())
        assertEquals("Rest timer", rest.name)
        assertEquals("Rest in progress", manager.getNotificationChannel(RestNotifications.PROGRESS_CHANNEL).name)
    }

    @Test fun finishNotificationKeepsIdOneWithLocalizedText() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 30, "entry")
        settle()
        now += 30_000
        settle()
        val posted = finished()!!
        assertEquals("rest", posted.channelId)
        assertEquals("Rest is over", posted.title())
        assertEquals("Next: Bench press, set 2", posted.text())
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test fun recreatedWhenTheProcessReturnsInTheBackground() = runTest {
        // A previous process saved a running rest; its notification is gone with it.
        val saved = persistence()
        saved.reconcileSession("active")
        saved.start("Bench press", 90, "entry")
        assertNull(progress())
        // A new process starts without an Activity in front (a receiver or the system woke it).
        val timer = controller(saved)
        runCurrent()
        settle()
        val posted = progress()!!
        assertEquals(timer.state.value.deadline, posted.`when`)
        assertEquals("Bench press · next set 2", posted.text())
    }

    @Test fun tappingOpensTheWorkout() = runTest {
        val timer = controller(persistence())
        runCurrent()
        timer.start("Bench press", 30, "entry")
        settle()
        val running = progress()!!
        now += 30_000
        settle()
        listOf(running, finished()!!).forEach { posted ->
            val open = shadowOf(posted.contentIntent)
            assertTrue(open.isActivity)
            assertTrue(open.flags and PendingIntent.FLAG_IMMUTABLE != 0)
            assertEquals(RestNotifications.OPEN_WORKOUT, open.savedIntent.action)
            assertEquals(ComponentName(app, MainActivity::class.java), open.savedIntent.component)
            // Back to the Activity already in the task (no second instance), with the manifest's launch mode unchanged.
            val flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            assertEquals(flags, open.savedIntent.flags and flags)
        }
    }

    @Test fun pendingSetIsTheFirstUncompletedSetOfTheTimersExercise() {
        val moosclesApp = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        moosclesApp.deleteDatabase("mooscles.db")
        try {
            runBlocking(Dispatchers.IO) {
                val repo = moosclesApp.repository
                repo.initialize()
                val bench = repo.createCustomExercise("Bench press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
                val workout = repo.startEmpty()
                val first = repo.addWorkoutExercise(workout, bench)
                val second = repo.addWorkoutExercise(workout, bench)
                repo.addSet(first)
                val sets = repo.snapshot().exerciseSets(first)
                assertEquals(3, sets.size)
                assertEquals(1, repo.pendingSetNumber(first))
                repo.updateSet(sets[0].copy(reps = 8, completed = true))
                repo.updateSet(sets[2].copy(reps = 8, completed = true))
                assertEquals(2, repo.pendingSetNumber(first)) // The first pending one, not the one after the last done.
                repo.updateSet(sets[1].copy(reps = 8, completed = true))
                assertNull(repo.pendingSetNumber(first))
                assertEquals(1, repo.pendingSetNumber(second)) // Each instance of the exercise counts its own sets.
                assertNull(repo.pendingSetNumber("gone"))
            }
        } finally {
            moosclesApp.appScope.cancel()
            moosclesApp.database.close()
        }
    }
}
