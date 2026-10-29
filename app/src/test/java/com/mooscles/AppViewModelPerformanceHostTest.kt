package com.mooscles

import android.os.Looper
import android.os.Trace
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.*
import com.mooscles.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTrace
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CopyOnWriteArrayList

/** Functional flow/persistence checks, deliberately not device timing benchmarks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PresentationThreadTraceShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelPerformanceHostTest {
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

    @Test fun changedWorkoutCardDetailsPrepareOffMainAndPublishOnTheMainLooper() {
        val workoutId = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            val id = app.repository.startEmpty()
            val exerciseId = app.repository.snapshot().exercises.first().id
            app.repository.addWorkoutExercise(id, exerciseId)
            id
        }
        val model = AppViewModel(app).also { models += it }
        awaitUi(model) { !it.loading && it.active?.id == workoutId }
        val setId = model.contentUi.value.active!!.exercises.first().sets.first().id
        ShadowTrace.setEnabled(true)
        assertTrue(Trace.isEnabled())
        val before = PresentationThreadTraceShadow.preparationThreads().size
        val publicationsOnMain = CopyOnWriteArrayList<Boolean>()
        // Unconfined observation records the actual StateFlow publisher thread,
        // rather than moving collection to Main and accidentally proving only that.
        val observer = CoroutineScope(Dispatchers.Unconfined).launch {
            model.contentUi.collect { ui ->
                if (ui.active?.exercises?.firstOrNull()?.sets?.firstOrNull()?.let { set ->
                        set.id == setId && set.reps == 11 && set.weightKg == 22.5
                    } == true) publicationsOnMain += Looper.myLooper() == Looper.getMainLooper()
            }
        }
        try {
            model.updateSet(setId, 11, 22.5, false, false, false)
            awaitUi(model) { it.active?.exercises?.firstOrNull()?.sets?.firstOrNull()?.let { set ->
                set.id == setId && set.reps == 11 && set.weightKg == 22.5
            } == true && publicationsOnMain.isNotEmpty() }
            val preparation = PresentationThreadTraceShadow.preparationThreads().drop(before)
            assertTrue("The saved edit must trigger preparation", preparation.isNotEmpty())
            assertTrue("Card preparation must stay off the Android main looper", preparation.none { it })
            assertTrue("Prepared screen state must be published on the Android main looper",
                publicationsOnMain.all { it })
        } finally { observer.cancel() }
    }

    @Test fun rapidStartTapsCreateOneSessionAndQueuedEditsSurviveReopening() {
        val routine = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            val id = app.repository.createRoutine("Quick start")
            val exercise = app.repository.createCustomExercise("Repeated movement", MuscleGroup.BACK,
                Equipment.DUMBBELL, LoadConvention.PER_HAND)
            repeat(3) { app.repository.addRoutineExercise(id, exercise) }
            id
        }
        val model = AppViewModel(app).also { models += it }
        awaitUi(model) { !it.loading && it.routines.any { r -> r.id == routine } }
        repeat(20) { model.startWorkout(routine) }
        awaitUi(model) { !it.startingWorkout && it.active?.sourceRoutineId == routine }
        val session = model.contentUi.value.active!!
        assertEquals(3, session.exercises.size)
        assertEquals(3, session.exercises.map { it.id }.toSet().size)
        assertEquals(1, runBlocking(Dispatchers.IO) { app.repository.snapshot().workouts.size })

        val set = session.exercises.first().sets.first()
        for (reps in 1..12) model.updateSet(set.id, reps, 17.25, false, false, false)
        model.updateSet(set.id, 12, 17.25, true, false, false)
        awaitUi(model) { it.active?.exercises?.first()?.sets?.first()?.let { row ->
            row.reps == 12 && row.weightKg == 17.25 && row.completed
        } == true }
        val saved = model.contentUi.value.active!!
        model.viewModelScope.cancel()
        val reopened = AppViewModel(app).also { models += it }
        awaitUi(reopened) { !it.loading && it.active == saved }
        reopened.startWorkout(null) // Resume must keep persisted values and identities.
        reopened.renameWorkout(saved.id, "Resumed session") // Queue barrier after resume.
        awaitUi(reopened) { !it.startingWorkout && it.active?.name == "Resumed session" }
        assertEquals(saved.copy(name = "Resumed session"), reopened.contentUi.value.active)
        assertEquals(1, runBlocking(Dispatchers.IO) { app.repository.snapshot().workouts.size })
    }

    @Test fun changingTimerDoesNotPublishWorkoutContentAgain() {
        runBlocking(Dispatchers.IO) {
            app.repository.initialize()
            app.repository.startEmpty()
        }
        val model = AppViewModel(app).also { models += it }
        awaitUi(model) { !it.loading && it.active != null }
        model.startTimer("Scoped timer", 120)
        awaitUi(model) { it.timer.running && it.timer.exerciseName == "Scoped timer" }
        val before = model.contentUi.value
        val remaining = model.ui.value.timer.remainingSeconds
        model.adjustTimer(-15)
        awaitUi(model) { it.timer.remainingSeconds < remaining }
        assertSame("Countdown/progress updates must stay outside the route state", before, model.contentUi.value)
        assertSame(before.active, model.contentUi.value.active)
        assertEquals(120, before.preferences.defaultRestSeconds)
    }

    @Test fun failedStartClearsLoadingAndCanBeRetriedWithoutCreatingPartialSessions() {
        val model = AppViewModel(app).also { models += it }
        awaitUi(model) { !it.loading }
        model.startWorkout("missing-routine")
        awaitUi(model) { !it.startingWorkout && it.error?.contains("Routine not found") == true }
        assertNull(runBlocking(Dispatchers.IO) { app.repository.activeWorkout() })
        model.startWorkout(null)
        awaitUi(model) { !it.startingWorkout && it.active != null && it.error == null }
        assertEquals(1, runBlocking(Dispatchers.IO) { app.repository.snapshot().workouts.size })
    }

    @Test fun queuedSettingChangesApplyToFollowingCommandsBeforePresentationCatchesUp() {
        val exerciseId = runBlocking(Dispatchers.IO) {
            app.preferences.defaultRest(120)
            app.preferences.autoRest(true)
            app.repository.initialize()
            app.repository.createCustomExercise("Fresh settings", MuscleGroup.CHEST, Equipment.BARBELL,
                LoadConvention.TOTAL_LOAD)
        }
        val model = AppViewModel(app).also { models += it }
        awaitUi(model) { !it.loading && it.exercises.any { e -> e.id == exerciseId } }
        model.setDefaultRest(175)
        model.startWorkout(null)
        awaitUi(model) { !it.startingWorkout && it.active != null }
        model.addWorkoutExercise(model.contentUi.value.active!!.id, exerciseId)
        awaitUi(model) { it.active?.exercises?.singleOrNull()?.restSeconds == 175 }
        val session = model.contentUi.value.active!!
        val set = session.exercises.single().sets.first()
        model.setAutoRest(false)
        model.updateSet(set.id, 8, 20.0, true, false, false)
        model.renameWorkout(session.id, "Saved settings barrier")
        awaitUi(model) { it.active?.name == "Saved settings barrier" && it.active.exercises.single().sets.first().completed }
        assertFalse(app.timer.state.value.running)
        assertFalse(app.timer.state.value.paused)
        assertEquals(175, runBlocking(Dispatchers.IO) { app.preferences.snapshot().defaultRestSeconds })
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.ui.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.ui.value.error}", predicate(model.ui.value))
    }
}
