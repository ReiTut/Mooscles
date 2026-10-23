package com.mooscles

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.*
import com.mooscles.timer.RestTimerController
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.UiState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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

/** Real command queue, prepared presentation, Room, DataStore, and Android timer effects. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelSessionCommandsHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) {
        withTimeout(20_000) { block() }
    }

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        io { app.moosclesStore.edit { it.clear() } }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    private data class Seed(val routine: String, val workout: String, val entries: List<String>)

    private fun seed(startTimer: Boolean = true): Seed {
        val seeded = io {
            val repo = app.repository
            repo.initialize()
            app.preferences.autoRest(true)
            val exercise = repo.createCustomExercise("Repeated press", MuscleGroup.CHEST,
                Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
            val earlier = repo.startEmpty(now = 1000)
            val oldEntry = repo.addWorkoutExercise(earlier, exercise)
            val oldSet = repo.snapshot().exerciseSets(oldEntry).first()
            repo.updateSet(oldSet.copy(weightKg = 20.0, reps = 7, completed = true))
            repo.finishWorkout(earlier, now = 2000)
            val routine = repo.createRoutine("Matching name")
            val first = repo.addRoutineExercise(routine, exercise)
            val second = repo.addRoutineExercise(routine, exercise)
            repo.updateRoutineExerciseRest(first, 150)
            repo.updateRoutineExerciseRest(second, 120)
            val another = repo.createRoutine("Matching name")
            repo.addRoutineExercise(another, exercise)
            val workout = repo.startRoutine(routine, now = 3000)
            val entries = repo.snapshot().workoutItems(workout)
            if (startTimer) {
                val provisional = repo.snapshot().exerciseSets(entries[1].id).first()
                repo.updateSet(provisional.copy(weightKg = 200.0, reps = 8, completed = true))
            }
            Seed(routine, workout, entries.map { it.id })
        }
        if (startTimer) appCommand {
            val entry = app.repository.snapshot().workoutItems(seeded.workout).single { it.id == seeded.entries[1] }
            app.timer.start(entry.name, entry.restSeconds, entry.id)
        }
        return seeded
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!predicate(model.ui.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected a durable session command result; error=${model.ui.value.error}", predicate(model.ui.value))
    }

    private fun timerSnapshot() = io { RestTimerPersistence(app.moosclesStore).snapshot() }

    /** Timer commands share a mutex with Main collectors, so keep Main moving while they finish. */
    private fun appCommand(block: suspend () -> Unit) {
        val command = app.appScope.async { withTimeout(20_000) { block() } }
        val deadline = System.nanoTime() + 25_000_000_000L
        while (!command.isCompleted && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        if (!command.isCompleted) {
            command.cancel()
            fail("Application command did not finish while Main remained available")
        }
        // Already complete: awaiting here cannot suspend a continuation needed by Main.
        runBlocking { command.await() }
    }

    @Test fun newAndRestoredRoutineWorkoutsPublishEveryPreparedCardFromTheBackgroundWorker() {
        val routineId = io {
            val repo = app.repository
            repo.initialize()
            val routine = repo.createRoutine("Complete preparation")
            val exercise = repo.snapshot().exercises.first()
            repeat(24) { index ->
                val entry = repo.addRoutineExercise(routine, exercise.id)
                repo.updateRoutineExerciseRest(entry, 90 + index)
            }
            routine
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }
        model.startWorkout(routineId)
        awaitUi(model) { !it.workoutPreparing && it.active?.sourceRoutineId == routineId }
        val prepared = model.ui.value.active!!
        assertEquals(24, prepared.exercises.size)
        prepared.exercises.forEachIndexed { index, card ->
            assertEquals(2, card.sets.size)
            assertEquals(90 + index, card.restSeconds)
            assertTrue(card.sets.all { !it.completed })
        }
        fun assertWorker(vm: AppViewModel) {
            val presenter = AppViewModel::class.java.getDeclaredField("presenter").apply { isAccessible = true }.get(vm) as WorkoutPresenter
            assertTrue("Heavy preparation must stay off main: ${presenter.metrics().lastPreparationThread}",
                presenter.metrics().lastPreparationThread.startsWith("DefaultDispatcher-worker"))
        }
        assertWorker(model)
        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) { !it.loading && it.active?.id == prepared.id }
        assertEquals(prepared, restored.ui.value.active)
        assertWorker(restored)
    }

    private fun postCompletionAlert() {
        app.getSystemService(NotificationManager::class.java).notify(1,
            Notification.Builder(app, "rest").setSmallIcon(R.drawable.ic_rest_notification)
                .setContentTitle("Previous rest complete").build())
    }

    @Test fun discardSuccessEventFollowsPersistentDeletionAndDurableTimerClearWithoutSavingProvisionalRecords() {
        val seeded = seed()
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == seeded.workout && it.timer.running }
        val before = io { app.repository.snapshot() }
        val history = model.ui.value.history
        val records = model.ui.value.records
        assertTrue(model.ui.value.active!!.records.isNotEmpty())
        assertEquals(seeded.routine, model.ui.value.active!!.sourceRoutineId)
        assertEquals(seeded.entries[1], timerSnapshot().workoutExerciseId)
        postCompletionAlert()
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(1, notifications.allNotifications.size)
        val staleToken = timerSnapshot().token

        model.discardWorkout()
        awaitUi(model) { it.discardedWorkoutId == seeded.workout && it.active == null && !it.timer.running && !it.timer.paused }
        val after = io { app.repository.snapshot() }
        assertNull(after.activeWorkout)
        assertTrue(after.workouts.none { it.id == seeded.workout })
        assertTrue(after.workoutExercises.none { it.workoutId == seeded.workout })
        assertTrue(after.sets.none { it.workoutExerciseId in seeded.entries })
        assertEquals(before.routines, after.routines)
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        assertEquals(history, model.ui.value.history)
        assertEquals(records, model.ui.value.records)
        assertTrue(model.ui.value.routines.none { it.id == model.ui.value.active?.sourceRoutineId })
        val cleared = timerSnapshot()
        assertNull(cleared.workoutId)
        assertNull(cleared.workoutExerciseId)
        assertFalse(cleared.running)
        assertFalse(cleared.paused)
        assertFalse(cleared.finished)
        assertEquals(0, alarms.scheduledAlarms.size)
        assertEquals(0, notifications.allNotifications.size)
        appCommand { app.timer.complete(staleToken) }
        assertEquals(0, notifications.allNotifications.size)

        // A new presentation has no durable navigation event or active session to reopen.
        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) { !it.loading && it.active == null && it.history == history }
        assertNull(restored.ui.value.discardedWorkoutId)
        assertEquals(records, restored.ui.value.records)
        assertFalse(restored.ui.value.timer.running)
        assertEquals(cleared, timerSnapshot())
        restored.viewModelScope.cancel()
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
        val reopened = WorkoutDatabase.open(app)
        try {
            io {
                val repo = WorkoutRepository(reopened)
                repo.initialize()
                val durable = repo.snapshot()
                assertNull(durable.activeWorkout)
                assertEquals(after.workouts, durable.workouts)
                assertEquals(after.workoutExercises, durable.workoutExercises)
                assertEquals(after.sets, durable.sets)
                assertEquals(after.routineSets, durable.routineSets)
                assertEquals(cleared, RestTimerPersistence(app.moosclesStore).snapshot())
            }
        } finally { reopened.close() }
    }

    @Test fun failedPersistentDiscardShowsAnErrorWithoutSuccessEventOrTimerInterruption() {
        val seeded = seed()
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == seeded.workout && it.timer.running }
        val before = io { app.repository.snapshot() }
        val timer = timerSnapshot()
        app.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_discard BEFORE DELETE ON workouts " +
                "WHEN OLD.status = 'ACTIVE' BEGIN SELECT RAISE(ABORT, 'injected discard failure'); END")
        try {
            model.discardWorkout()
            awaitUi(model) { it.error?.contains("injected discard failure") == true }
            assertNull(model.ui.value.discardedWorkoutId)
            assertEquals(seeded.workout, model.ui.value.active?.id)
            assertEquals(before, io { app.repository.snapshot() })
            assertEquals(timer, timerSnapshot())
            assertEquals(1, alarms.scheduledAlarms.size)
        } finally {
            app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_discard")
        }
        model.clearError()
        model.discardWorkout()
        awaitUi(model) { it.discardedWorkoutId == seeded.workout && it.active == null }
        assertFalse(timerSnapshot().running)
    }

    private class FailingTimerStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        @Volatile var failWrites = false
        override val data = delegate.data
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrites) throw IOException("injected timer clear failure")
            return delegate.updateData(transform)
        }
    }

    @Test fun failedDurableTimerClearCancelsAlertsButDoesNotPublishDiscardSuccess() {
        val store = FailingTimerStore(app.moosclesStore)
        val controller = RestTimerController(app, app.appScope, RestTimerPersistence(store))
        // Test-only replacement of the existing lazy dependency; production stays unmodified.
        MoosclesApplication::class.java.getDeclaredField("timer\$delegate").apply { isAccessible = true }
            .set(app, lazyOf(controller))
        val seeded = seed()
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == seeded.workout && it.timer.running }
        val history = model.ui.value.history
        val records = model.ui.value.records
        postCompletionAlert()
        store.failWrites = true
        try {
            model.discardWorkout()
            awaitUi(model) { it.error?.contains("injected timer clear failure") == true && it.active == null }
            assertNull(model.ui.value.discardedWorkoutId)
            assertNull(io { app.repository.snapshot().activeWorkout })
            assertEquals(history, model.ui.value.history)
            assertEquals(records, model.ui.value.records)
            assertEquals(0, alarms.scheduledAlarms.size)
            assertEquals(0, notifications.allNotifications.size)
            // The store still contains the old timer, so claiming successful cleanup would be false.
            assertEquals(seeded.workout, timerSnapshot().workoutId)
            assertTrue(timerSnapshot().running)
        } finally {
            store.failWrites = false
            appCommand { controller.clearForSessionEnd() }
        }
    }

    @Test fun autoRestOwnsTheExactDuplicateInstanceAndReorderingKeepsItsCountdownAndRecordedSets() {
        val seeded = seed(startTimer = false)
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == seeded.workout && it.preferences.autoRest }
        val second = model.ui.value.active!!.exercises.single { it.id == seeded.entries[1] }
        val set = second.sets.first()
        model.updateSet(set.id, reps = 8, weightKg = 22.0, completed = true, warmup = false, assistance = false)
        awaitUi(model) { it.active?.exercises?.single { entry -> entry.id == second.id }?.sets?.first()?.completed == true &&
            it.timer.running && it.timer.workoutExerciseId == second.id }
        val before = io { app.repository.snapshot() }
        val timer = timerSnapshot()
        val history = model.ui.value.history
        assertEquals(seeded.workout, timer.workoutId)
        assertEquals(second.id, timer.workoutExerciseId)
        val ordered = seeded.entries.reversed()
        model.reorderWorkoutExercises(seeded.workout, ordered)
        awaitUi(model) { it.active?.exercises?.map { entry -> entry.id } == ordered }
        val after = io { app.repository.snapshot() }
        assertEquals(timer, timerSnapshot())
        assertEquals(timer.deadline, alarms.scheduledAlarms.single().triggerAtTime)
        assertEquals(second.id, model.ui.value.timer.workoutExerciseId)
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.sets, after.sets)
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)
        assertEquals(history, model.ui.value.history)
        before.workoutItems(seeded.workout).forEach { entry ->
            assertEquals(entry.copy(position = ordered.indexOf(entry.id)), after.workoutItems(seeded.workout).single { it.id == entry.id })
        }
        model.viewModelScope.cancel()
        val restored = newModel()
        awaitUi(restored) { !it.loading && it.active?.exercises?.map { entry -> entry.id } == ordered &&
            it.timer.workoutExerciseId == second.id && it.timer.running }
        assertEquals(timer, timerSnapshot())
        assertEquals(22.0, restored.ui.value.active!!.exercises.first().sets.first().weightKg, 0.0)
        assertTrue(restored.ui.value.active!!.exercises.first().sets.first().completed)
    }
}
