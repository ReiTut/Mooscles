package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.ui.UiState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

/** Where and in which order the ViewModel presents saved data; real Room and DataStore, no timing claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelPresentationThreadHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun presentRunsOffTheMainThread() {
        val workoutId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            app.repository.startEmpty()
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active?.id == workoutId }
        model.renameWorkout(workoutId, "Presented in the background")
        awaitUi(model) { it.active?.name == "Presented in the background" }
        assertTrue("Opening and the rename must each be presented", model.presentedCount.get() >= 2)
        assertEquals("present() must never run on the main thread", 0, model.presentedOnMainThread.get())
    }

    @Test fun latestDataWinsAfterQuickSuccessiveChanges() {
        val exerciseId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            app.repository.createCustomExercise("Quick press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        }
        val model = newModel()
        awaitUi(model) { !it.loading && it.exercises.any { e -> e.id == exerciseId } }
        val seen = CopyOnWriteArrayList<UiState>()
        model.viewModelScope.launch(Dispatchers.Unconfined) { model.contentUi.collect { seen += it } }
        val presentedBefore = model.presentedCount.get()

        // Successive writes: after each one the main looper runs, so Room's snapshot reaches the
        // ViewModel's source, but nobody waits for that snapshot to be presented.
        val finished = (1..3).map { round ->
            val id = runBlocking(Dispatchers.IO) {
                val repo = app.repository
                val id = repo.startEmpty(name = "Quick $round", now = round * 10_000L)
                val entry = repo.addWorkoutExercise(id, exerciseId)
                val set = repo.snapshot().exerciseSets(entry).first()
                repo.updateSet(set.copy(reps = round, weightKg = 20.0 + round, completed = true))
                repo.finishWorkout(id, now = round * 10_000L + 5_000L)
                id
            }
            repeat(3) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            id
        }
        val latest: (UiState) -> Boolean = { state ->
            state.active == null && state.history.map { it.id }.toSet() == finished.toSet()
        }
        awaitUi(model, latest)
        // Give any stale presentation the chance to arrive after the latest one.
        repeat(50) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        // The scenario really produced successive inputs, not one snapshot holding every write.
        assertTrue("Expected several presentations, got ${model.presentedCount.get() - presentedBefore}",
            model.presentedCount.get() - presentedBefore >= 2)
        val sizes = seen.map { it.history.size }
        assertTrue(latest(model.contentUi.value))
        val first = seen.indexOfFirst(latest)
        assertTrue("The latest data was never published", first >= 0)
        assertTrue("No earlier state may be published after the latest one", seen.drop(first).all(latest))
        assertEquals("History must only grow while the writes are presented in order", sizes.sorted(), sizes)
    }

    @Test fun firstStateStillLoadingUntilPresented() {
        // A large saved history takes a while to present, which is when an empty ready state could slip out.
        val seeded = runBlocking(Dispatchers.IO) {
            val repo = app.repository
            repo.initialize()
            val rows = LargeHistoryFixture.rows(LargeHistoryFixture.defaultExercises)
            app.database.dao().insertWorkouts(rows.workouts)
            app.database.dao().insertWorkoutExercises(rows.entries)
            app.database.dao().insertSets(rows.sets)
            repo.createRoutine("Shown after presentation") to rows.workouts.last().id
        }
        val (routineId, workoutId) = seeded
        val hasSavedData: (UiState) -> Boolean = { state ->
            state.routines.any { it.id == routineId } && state.history.any { it.id == workoutId }
        }
        val model = newModel()
        val seen = CopyOnWriteArrayList<UiState>()
        model.viewModelScope.launch(Dispatchers.Unconfined) { model.contentUi.collect { seen += it } }
        assertTrue("A new model starts loading", model.contentUi.value.loading)
        awaitUi(model) { !it.loading && hasSavedData(it) }

        assertTrue("The first published state is the loading one", seen.first().loading)
        val early = seen.filter { !it.loading && !hasSavedData(it) }
            .map { "routines=${it.routines.size} history=${it.history.size}" }
        assertTrue("Every ready state must already show the saved data: $early", early.isEmpty())
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }
}
