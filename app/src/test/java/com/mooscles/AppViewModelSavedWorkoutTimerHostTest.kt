package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.UiState
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

/** Historical editing goes through the real command queue, Room and Android timer, not a recording UI fake. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelSavedWorkoutTimerHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        // The host's preferences can outlive a Robolectric Application. Establish this test's idle timer
        // before the lazy controller starts observing; never inherit another test's running interval.
        runBlocking(Dispatchers.IO) { RestTimerPersistence(app.moosclesStore).clear() }
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun completingASavedSetWithAutoRestEnabledDoesNotStartATimer() {
        val saved = seedSaved()
        val model = model()
        awaitUi(model) { !it.loading && it.history.any { workout -> workout.id == saved.workoutId } }
        val before = app.timer.state.value
        assertFalse(before.running)
        model.updateSet(saved.setId, 9, 22.125, true, false, false)
        model.renameWorkout(saved.workoutId, "Historical edit saved") // Queue barrier after the set command.
        awaitUi(model) { ui -> ui.history.any { workout ->
            workout.id == saved.workoutId && workout.name == "Historical edit saved" &&
                workout.exercises.any { entry -> entry.sets.any { it.id == saved.setId && it.completed && it.reps == 9 && it.weightKg == 22.125 } }
        } }
        assertEquals("Finishing a historical set must leave the entire timer state unchanged", before, app.timer.state.value)
        assertNull(model.contentUi.value.active)
    }

    @Test fun completingASavedSetDoesNotReplaceAnotherActiveWorkoutsTimerOrSnapshot() {
        val saved = seedSaved()
        val active = runBlocking(Dispatchers.IO) {
            val id = app.repository.startEmpty("Active", now = 70_000)
            val entry = app.repository.addWorkoutExercise(id, saved.exerciseId)
            id to entry
        }
        val model = model()
        awaitUi(model) { !it.loading && it.active?.id == active.first }
        model.startTimer("Active workout rest", 300, active.second)
        awaitUi(model) { it.timer.running && it.timer.workoutExerciseId == active.second }
        val beforeTimer = app.timer.state.value
        val beforeWorkout = activeSnapshot(active.first)

        model.updateSet(saved.setId, 11, 22.125, true, false, false)
        model.renameWorkout(saved.workoutId, "Saved while active")
        awaitUi(model) { ui -> ui.history.any { workout ->
            workout.id == saved.workoutId && workout.name == "Saved while active" &&
                workout.exercises.any { entry -> entry.sets.any { it.id == saved.setId && it.completed && it.reps == 11 } }
        } }
        assertEquals("Historical completion must not change the active timer's token, deadline or association", beforeTimer, app.timer.state.value)
        // Historical edits intentionally refresh derived "previous" suggestions. Persisted active rows must not change.
        assertEquals(beforeWorkout, activeSnapshot(active.first))
        assertEquals(active.first, runBlocking(Dispatchers.IO) { app.repository.activeWorkout()?.id })
    }

    private data class Saved(val workoutId: String, val setId: String, val exerciseId: String)

    @Test fun repeatedRejectedSavedEditsKeepTheirSubjectAndLaterCommandsRun() {
        val saved = seedSaved()
        val model = model()
        awaitUi(model) { !it.loading && it.history.any { workout -> workout.id == saved.workoutId } }
        val before = runBlocking(Dispatchers.IO) { app.repository.workoutSet(saved.setId) }
        repeat(2) { model.updateSet(saved.setId, -1, -1.0, true, false, false) }
        model.renameWorkout(saved.workoutId, "Queue remains usable")
        awaitUi(model) { it.history.any { workout -> workout.id == saved.workoutId && workout.name == "Queue remains usable" } }
        assertEquals("Every rejected edit must release only its own pending UI operation", 2,
            model.contentUi.value.subjectFailures[saved.setId] ?: 0)
        assertEquals(saved.setId, model.contentUi.value.errorSubject)
        assertEquals(before, runBlocking(Dispatchers.IO) { app.repository.workoutSet(saved.setId) })
    }

    private fun activeSnapshot(id: String) = runBlocking(Dispatchers.IO) {
        val data = app.repository.snapshot()
        val entries = data.workoutExercises.filter { it.workoutId == id }
        val entryIds = entries.map { it.id }.toSet()
        Triple(data.workouts.filter { it.id == id }, entries, data.sets.filter { it.workoutExerciseId in entryIds })
    }

    private fun seedSaved(): Saved = runBlocking(Dispatchers.IO) {
        app.preferences.autoRest(true)
        app.repository.initialize()
        val exercise = app.repository.createCustomExercise("Exact saved movement", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val workout = app.repository.startEmpty("Saved", now = 1_000)
        val entry = app.repository.addWorkoutExercise(workout, exercise, defaultRestSeconds = 185)
        val first = app.repository.snapshot().sets.first { it.workoutExerciseId == entry }
        app.repository.updateSet(first.copy(reps = 8, weightKg = 20.0, completed = true))
        val editable = app.repository.addSet(entry)
        app.repository.finishWorkout(workout, now = 61_000)
        Saved(workout, editable, exercise)
    }

    private fun model() = AppViewModel(app).also { models += it }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.ui.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected saved-workout state was not published: ${model.ui.value.error}", predicate(model.ui.value))
    }
}
