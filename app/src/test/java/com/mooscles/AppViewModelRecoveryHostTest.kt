package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.*
import com.mooscles.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
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
import org.robolectric.shadows.ShadowTrace

/** Real Room recovery/publication contracts. Native tests establish lifecycle and frame timing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [RecoveryPresentationTraceShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelRecoveryHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
        RecoveryPresentationTraceShadow.resetRecoveryGate()
        ShadowTrace.setEnabled(true)
        runBlocking(Dispatchers.IO) { app.preferences.autoRest(false) }
    }

    @After fun close() {
        RecoveryPresentationTraceShadow.releaseFullPreparation()
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun recreatedModelPublishesTheValidatedEssentialEditorBeforeHeldHistoryPreparation() {
        val saved = seedSession()
        val first = model()
        await(first) { !it.loading && !it.secondaryLoading && it.active?.id == saved.workout.id }
        val cached = requireNotNull(app.workoutRecoveryCache.getValidated(saved))
        val before = snapshot()
        first.viewModelScope.cancel()
        RecoveryPresentationTraceShadow.holdFullPreparation()
        val reopened = model()
        var expected = saved
        try {
            await(reopened) { it.active?.id == saved.workout.id && it.secondaryLoading &&
                RecoveryPresentationTraceShadow.fullPreparationEntered() }
            assertSame("A fresh matching storage read can reuse the process-local essential editor",
                cached, reopened.contentUi.value.active)
            assertFalse("Recovery is not another Start", reopened.workoutOpening.value)
            assertRows(saved, reopened.contentUi.value)
            assertEquals("Recovery must not create a session", before.workouts, snapshot().workouts)
            // Saving recovered input must not wait for historical presentation.
            // Publication still fences an in-flight older full result.
            val changed = saved.sets.first().copy(reps = 9, weightKg = 32.125, completed = false)
            reopened.updateSet(changed.id, changed.reps, changed.weightKg, changed.completed, changed.warmup, changed.assistance)
            await(reopened) { runBlocking(Dispatchers.IO) { app.repository.workoutSet(changed.id) } == changed }
            expected = requireNotNull(runBlocking(Dispatchers.IO) { app.repository.activeSnapshot() })
            assertTrue(reopened.contentUi.value.secondaryLoading)
            assertFalse(reopened.workoutOpening.value)
        } finally { RecoveryPresentationTraceShadow.releaseFullPreparation() }
        await(reopened) { !it.loading && !it.secondaryLoading && it.active?.id == saved.workout.id }
        assertRows(expected, reopened.contentUi.value)
        assertNotNull("Historical information may arrive after the essential editor",
            reopened.contentUi.value.active!!.exercises.first().previous)
    }

    @Test fun retainedModelKeepsSavedEditsCurrentWithoutScreenCollectorsAndNewModelRejectsAnOldCache() {
        val saved = seedSession()
        val retained = model()
        await(retained) { !it.loading && !it.secondaryLoading && it.active?.id == saved.workout.id }
        assertNotNull(app.workoutRecoveryCache.getValidated(saved))
        val screen = CoroutineScope(Dispatchers.Unconfined).launch { retained.contentUi.collect { } }
        screen.cancel()
        val changed = saved.sets.first().copy(reps = 9, weightKg = 32.125, completed = false)
        runBlocking(Dispatchers.IO) { app.repository.updateSet(changed) }
        await(retained) { it.active?.exercises?.flatMap { row -> row.sets }?.any {
            it.id == changed.id && it.reps == 9 && it.weightKg == 32.125 && !it.completed
        } == true }
        assertFalse(retained.workoutOpening.value)
        retained.viewModelScope.cancel()
        val fresh = requireNotNull(runBlocking(Dispatchers.IO) { app.repository.activeSnapshot() })
        RecoveryPresentationTraceShadow.holdFullPreparation()
        val reopened = model()
        try {
            await(reopened) { it.active?.id == saved.workout.id && it.secondaryLoading &&
                it.active.exercises.flatMap { row -> row.sets }.any { row -> row.id == changed.id && row.reps == 9 } }
            assertRows(fresh, reopened.contentUi.value)
            assertEquals(32.125, reopened.contentUi.value.active!!.exercises.first().sets.first().weightKg, 0.0)
            assertNotNull("Fresh changed rows replace the earlier cache", app.workoutRecoveryCache.getValidated(fresh))
            assertEquals(1, snapshot().workouts.count { it.status == WorkoutStatus.ACTIVE })
        } finally { RecoveryPresentationTraceShadow.releaseFullPreparation() }
        await(reopened) { !it.loading && !it.secondaryLoading }
    }

    @Test fun finishDiscardAndAReplacementSessionDoNotResurrectTheRetainedEditor() {
        val firstSaved = seedSession()
        val first = model()
        await(first) { !it.loading && it.active?.id == firstSaved.workout.id }
        first.finishWorkout()
        // The Room publication can precede the queued finish command's final
        // cache cleanup. Wait for the completed operation, not only its rows.
        await(first) { it.active == null && it.history.any { row -> row.id == firstSaved.workout.id } &&
            app.workoutRecoveryCache.getValidated(firstSaved) == null }
        assertNull(app.workoutRecoveryCache.getValidated(firstSaved))
        first.viewModelScope.cancel()

        val next = runBlocking(Dispatchers.IO) {
            val id = app.repository.startEmpty("Replacement session")
            app.repository.addWorkoutExercise(id, firstSaved.exercises.first().id)
            requireNotNull(app.repository.activeSnapshot())
        }
        val replacement = model()
        await(replacement) { !it.loading && it.active?.id == next.workout.id }
        assertNotEquals(firstSaved.workout.id, replacement.contentUi.value.active!!.id)
        assertNotNull(app.workoutRecoveryCache.getValidated(next))
        replacement.discardWorkout()
        await(replacement) { !it.discardingWorkout && it.active == null }
        assertNull(runBlocking(Dispatchers.IO) { app.repository.activeSnapshot() })
        assertNull(app.workoutRecoveryCache.getValidated(next))
        replacement.viewModelScope.cancel()

        val reopened = model()
        await(reopened) { !it.loading && !it.secondaryLoading }
        assertNull(reopened.contentUi.value.active)
        assertFalse(reopened.workoutOpening.value)
        assertEquals(0, snapshot().workouts.count { it.status == WorkoutStatus.ACTIVE })
        assertTrue(snapshot().workouts.any { it.id == firstSaved.workout.id && it.status == WorkoutStatus.FINISHED })
    }

    private fun seedSession(): ActiveWorkoutSnapshot = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        val routineId = app.repository.createRoutine("Retained recovery routine")
        val movement = app.repository.createCustomExercise("Precise recovery movement", MuscleGroup.CHEST,
            Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        repeat(2) { app.repository.addRoutineExercise(routineId, movement) }
        val historical = app.repository.startEmpty("Earlier recovery workout", now = 1000)
        app.repository.addWorkoutExercise(historical, movement)
        val historicalSet = requireNotNull(app.repository.activeSnapshot()).sets.first()
        app.repository.updateSet(historicalSet.copy(reps = 8, weightKg = 24.0, completed = true))
        app.repository.finishWorkout(historical, now = 2000)
        val active = app.repository.startRoutineSnapshot(routineId)
        app.repository.updateSet(active.sets.first().copy(reps = 6, weightKg = 31.125, completed = true))
        requireNotNull(app.repository.activeSnapshot())
    }

    private fun model() = AppViewModel(app).also { models += it }
    private fun snapshot() = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
    private fun assertRows(saved: ActiveWorkoutSnapshot, ui: UiState) {
        val active = requireNotNull(ui.active)
        assertEquals(saved.workout.id, active.id)
        assertEquals(saved.workout.sourceRoutineId, active.sourceRoutineId)
        assertEquals(saved.entries.map { it.id }, active.exercises.map { it.id })
        assertEquals(saved.entries.map { it.restSeconds }, active.exercises.map { it.restSeconds })
        assertEquals(saved.sets.sortedWith(compareBy<WorkoutSet> { it.workoutExerciseId }.thenBy { it.position })
            .map { listOf(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance) },
            active.exercises.sortedBy { it.id }.flatMap { it.sets }.map {
                listOf(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance)
            })
    }
    private fun await(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val end = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected recovery state: ${model.contentUi.value}", predicate(model.contentUi.value))
    }
}
