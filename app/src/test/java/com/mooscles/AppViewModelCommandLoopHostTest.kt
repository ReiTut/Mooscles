package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineFolder
import com.mooscles.ui.UiState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** The serialized command loop survives cancellations that do not come from the ViewModel itself; real Room and DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelCommandLoopHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking(Dispatchers.IO) {
            app.preferences.selectRoutineFolder(RoutineFolder.DEFAULT_ID)
            app.preferences.autoRest(false)
        }
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun cancellationEscapingACommandIsReportedAndLaterCommandsStillRun() {
        val model = newModel()
        awaitUi(model) { !it.loading }

        // A timed-out wait inside a command, while the ViewModel itself is alive. It runs on a real clock:
        // the paused main looper never advances time, so a delay on the main dispatcher would never expire.
        model.enqueueCommandForTest { withContext(Dispatchers.Default) { withTimeout(1) { awaitCancellation() } } }
        model.createRoutine("After a timed-out wait")
        awaitUi(model) { it.routines.any { routine -> routine.name == "After a timed-out wait" } }
        assertEquals(SAVE_FAILED, model.contentUi.value.error)

        // Any other cancellation that escapes a command is reported the same way.
        model.clearError()
        model.enqueueCommandForTest { throw CancellationException("Child work was cancelled") }
        model.createRoutine("After a plain cancellation")
        awaitUi(model) { it.routines.any { routine -> routine.name == "After a plain cancellation" } }
        assertEquals(SAVE_FAILED, model.contentUi.value.error)

        val saved = snapshot().routines.map { it.name }
        assertTrue(saved.containsAll(listOf("After a timed-out wait", "After a plain cancellation")))
    }

    @Test fun clearingTheViewModelStillEndsTheCommandLoop() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        val started = AtomicBoolean(false)
        val ranAfterClear = AtomicBoolean(false)
        model.enqueueCommandForTest { started.set(true); awaitCancellation() }
        awaitCondition { started.get() }
        // Queued behind the suspended command, so it is already waiting in the channel when the scope ends.
        model.enqueueCommandForTest { ranAfterClear.set(true) }
        model.viewModelScope.cancel()
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertFalse("A cleared ViewModel must not run queued commands", ranAfterClear.get())
    }

    @Test fun discardWhoseScreenUpdateTimesOutReportsItAndKeepsAcceptingActions() {
        val routineId = seedRoutine()
        val model = newModel()
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }
        model.startWorkout(routineId)
        awaitUi(model) { !it.startingWorkout && it.active?.sourceRoutineId == routineId }
        val started = model.contentUi.value.active!!
        val previousEvent = model.contentUi.value.discardNavigationRequest

        // The wait for the screen gives up at once; the deletion itself is already durable.
        model.screenWaitMillis = 0
        model.discardWorkout()
        awaitUi(model) { !it.discardingWorkout && it.error == DISCARD_SCREEN_FAILED }
        assertEquals(previousEvent, model.contentUi.value.discardNavigationRequest)
        assertTrue(snapshot().workouts.none { it.id == started.id })

        // Later actions are still processed, and the screen catches up with the deletion.
        model.clearError()
        model.createRoutine("After the discard")
        awaitUi(model) { it.active == null && it.routines.any { routine -> routine.name == "After the discard" } }
        assertNull(model.contentUi.value.error)
        assertTrue(snapshot().routines.any { it.name == "After the discard" })
    }

    private fun seedRoutine(): String = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        val routineId = app.repository.createRoutine("Saved routine")
        val exerciseId = app.repository.snapshot().exercises.first { it.convention == LoadConvention.TOTAL_LOAD }.id
        app.repository.addRoutineExercise(routineId, exerciseId)
        routineId
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun snapshot(): AppData = runBlocking(Dispatchers.IO) { app.repository.snapshot() }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Condition was not reached", condition())
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }

    private companion object {
        const val SAVE_FAILED = "Could not save this change. Please try again."
        const val DISCARD_SCREEN_FAILED = "The workout was discarded, but the screen could not update. Open History or Workout to refresh it."
    }
}
