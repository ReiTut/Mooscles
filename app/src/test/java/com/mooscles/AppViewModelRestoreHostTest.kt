package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.RoutineFolder
import com.mooscles.data.WorkoutSet
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdatePhase
import com.mooscles.updates.UpdateSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Undo of a removed set through the view model (decision 14: under the training gate, refusals as
 * [UiError.SET_NOT_RESTORED]) and the 0:00 rest that does not start the timer. Real Room, DataStore, timer and
 * UpdateManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelRestoreHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking(Dispatchers.IO) {
            app.preferences.selectRoutineFolder(RoutineFolder.DEFAULT_ID)
            app.preferences.autoRest(false)
            app.updates.store.change { UpdateSnapshot() }
        }
    }

    @After fun close() {
        runBlocking(Dispatchers.IO) { app.updates.store.change { UpdateSnapshot() } }
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun restoreSetWritesUnderTheGate() {
        val (entryId, removed) = seedWithRemovedMiddleSet()
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.exercises?.singleOrNull()?.sets?.size == 2 }

        runBlocking { app.trainingGate.lock() }
        try {
            model.restoreSet(entryId, removed.position, removed.toUi())
            // The command waits for the gate: nothing is written while it is held.
            idleFor(300)
            assertNull(runBlocking(Dispatchers.IO) { app.repository.workoutSet(removed.id) })
        } finally {
            app.trainingGate.unlock()
        }

        awaitUi(model) { it.active?.exercises?.single()?.sets?.size == 3 }
        val sets = runBlocking(Dispatchers.IO) { app.repository.snapshot().exerciseSets(entryId) }
        assertEquals(removed, sets[1])
        assertEquals(listOf(0, 1, 2), sets.map { it.position })
        assertNull(model.contentUi.value.errorCode)
    }

    @Test fun installingUpdateBlocksTheRestore() {
        val (entryId, removed) = seedWithRemovedMiddleSet()
        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null }
        runBlocking(Dispatchers.IO) { app.updates.store.change { it.copy(phase = UpdatePhase.INSTALLING) } }

        model.restoreSet(entryId, removed.position, removed.toUi())

        awaitUi(model) { it.error != null }
        assertEquals(UiError.BLOCKED_BY_UPDATE, model.contentUi.value.errorCode)
        assertNull(runBlocking(Dispatchers.IO) { app.repository.workoutSet(removed.id) })
    }

    @Test fun refusedRestoreSetsSetNotRestored() {
        val (entryId, removed) = seedWithRemovedMiddleSet()
        runBlocking(Dispatchers.IO) { app.repository.removeWorkoutExercise(entryId) }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.exercises?.isEmpty() == true }

        model.restoreSet(entryId, removed.position, removed.toUi())

        awaitUi(model) { it.errorCode != null }
        assertEquals(UiError.SET_NOT_RESTORED, model.contentUi.value.errorCode)
        assertNotNull(model.contentUi.value.error)
        assertNull(runBlocking(Dispatchers.IO) { app.repository.workoutSet(removed.id) })
    }

    @Test fun zeroRestDoesNotStartTheTimer() {
        val set = seedRestAndAutoRest(restSeconds = 0)
        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null }

        model.updateSet(set.id, 8, 60.0, completed = true, warmup = false, assistance = false)
        awaitCommandsDone(model)

        assertTrue(runBlocking(Dispatchers.IO) { app.repository.workoutSet(set.id) }!!.completed)
        assertNull(model.contentUi.value.error)
        assertFalse(model.timerUi.value.running)
        // Not started at all: start(…, 0) would leave a finished interval owned by this exercise. (The timer's
        // own session tracking may still adopt the active workout's id; that is not a start.)
        assertFalse(model.timerUi.value.finished)
        app.timer.state.value.let { state ->
            assertFalse(state.running || state.paused || state.finished)
            assertNull(state.workoutExerciseId)
        }
    }

    @Test fun positiveRestStillStartsTheTimer() {
        val set = seedRestAndAutoRest(restSeconds = 90)
        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null }

        model.updateSet(set.id, 8, 60.0, completed = true, warmup = false, assistance = false)
        awaitCondition("The 1:30 rest did not start") { model.timerUi.value.running }
        assertEquals(90_000L, app.timer.state.value.durationMillis)
    }

    /**
     * Final review, item 1: a rest started from the rest sheet is stored as the automatic rest is,
     * with the entry's id and its snapshot name, so the dock names it by the entry.
     */
    @Test fun aManualRestStoresTheEntryAndItsSnapshotName() {
        seedRestAndAutoRest(restSeconds = 90)
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.exercises?.size == 1 }
        val entry = model.contentUi.value.active!!.exercises.single()

        model.startTimer(entry.exercise.name, 75, entry.id)
        awaitCondition("The manual rest did not start") { model.timerUi.value.let { it.running && it.durationSeconds == 75 } }

        val stored = runBlocking(Dispatchers.IO) { RestTimerPersistence(app.moosclesStore).snapshot() }
        val snapshotName = runBlocking(Dispatchers.IO) { app.repository.workoutExercise(entry.id) }!!.name
        assertEquals(snapshotName, stored.exerciseName)
        assertEquals(entry.id, stored.workoutExerciseId)
        assertEquals(75_000L, stored.durationMillis)
        assertEquals(entry.id, model.timerUi.value.workoutExerciseId)
    }

    /** An active workout with one exercise of three sets, the middle one completed and then removed. */
    private fun seedWithRemovedMiddleSet(): Pair<String, WorkoutSet> = runBlocking(Dispatchers.IO) {
        val repo = app.repository
        repo.initialize()
        val workoutId = repo.startEmpty()
        val exercise = repo.snapshot().exercises.filter { !it.custom }.minBy { it.id }
        val entryId = repo.addWorkoutExercise(workoutId, exercise.id)
        repo.addSet(entryId)
        val middle = repo.snapshot().exerciseSets(entryId)[1].copy(reps = 8, weightKg = 82.5, completed = true)
        repo.updateSet(middle)
        repo.removeSet(middle.id)
        entryId to middle
    }

    private fun seedRestAndAutoRest(restSeconds: Int): WorkoutSet = runBlocking(Dispatchers.IO) {
        val repo = app.repository
        repo.initialize()
        app.preferences.autoRest(true)
        val workoutId = repo.startEmpty()
        val exercise = repo.snapshot().exercises.filter { !it.custom }.minBy { it.id }
        val entryId = repo.addWorkoutExercise(workoutId, exercise.id)
        repo.updateWorkoutExerciseRest(entryId, restSeconds)
        repo.snapshot().exerciseSets(entryId).first()
    }

    private fun WorkoutSet.toUi() = SetUi(id = id, reps = reps, weightKg = weightKg, completed = completed,
        warmup = warmup, assistance = assistance)

    private fun newModel() = AppViewModel(app).also { models += it }

    /** Commands run one at a time: once a marker queued after them ran, every earlier command has finished. */
    private fun awaitCommandsDone(model: AppViewModel) {
        val done = AtomicBoolean(false)
        model.enqueueCommandForTest { done.set(true) }
        awaitCondition("The command queue did not drain") { done.get() }
        idleFor(100)
    }

    private fun idleFor(millis: Long) {
        val deadline = System.nanoTime() + millis * 1_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(message, predicate())
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }
}
