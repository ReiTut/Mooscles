package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineFolder
import com.mooscles.ui.TargetSetUi
import com.mooscles.ui.UiError
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

/**
 * The phase 4B routine and folder actions through the real view model, Room and DataStore: ids and names chosen
 * by the screen, folder deletion and selection, the target line during a workout and the per-command error
 * subject (plan decision 7, preflight F10).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelRoutinesHostTest {
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

    @Test fun createRoutineUsesTheScreenIdAndFolder() {
        val folder = seedFolder("Upper")
        val model = ready()
        // The saved selection is the default folder; the screen's folder wins.
        model.createRoutine("  Push day ", folder, "screen-id")
        awaitUi(model) { it.routines.any { routine -> routine.id == "screen-id" } }
        val routine = model.contentUi.value.routines.single { it.id == "screen-id" }
        assertEquals("Push day", routine.name)
        assertEquals(folder, routine.folderId)
        assertEquals(RoutineFolder.DEFAULT_ID, model.contentUi.value.preferences.selectedRoutineFolderId)
    }

    @Test fun duplicateUsesTheScreensNameAndId() {
        val routineId = seedRoutine(2)
        val model = ready()
        model.duplicateRoutine(routineId, "Saved routine (cópia)", "copy-id")
        awaitUi(model) { it.routines.any { routine -> routine.id == "copy-id" } }
        val original = model.contentUi.value.routines.single { it.id == routineId }
        val copy = model.contentUi.value.routines.single { it.id == "copy-id" }
        assertEquals("Saved routine (cópia)", copy.name)
        assertEquals(original.exercises.map { it.exercise.id }, copy.exercises.map { it.exercise.id })
        assertEquals(original.folderId, copy.folderId)
    }

    @Test fun addRoutineExercisesKeepsTheTapOrder() {
        val routineId = seedRoutine(0)
        val (a, b, c) = catalog(3)
        val model = ready()
        model.addRoutineExercises(routineId, listOf(c, a, b))
        awaitUi(model) { it.routines.single { routine -> routine.id == routineId }.exercises.size == 3 }
        assertEquals(listOf(c, a, b), model.contentUi.value.routines.single { it.id == routineId }.exercises.map { it.exercise.id })
    }

    @Test fun reorderRoutinesInAFolderKeepsTheOthers() {
        val folder = seedFolder("Upper")
        val (d1, a1, d2, a2) = runBlocking(Dispatchers.IO) {
            listOf(app.repository.createRoutine("D1"), app.repository.createRoutine("A1", folder),
                app.repository.createRoutine("D2"), app.repository.createRoutine("A2", folder))
        }
        val model = ready()
        model.reorderRoutines(folder, listOf(a2, a1))
        awaitUi(model) { it.routines.filter { routine -> routine.folderId == folder }.map { routine -> routine.id } == listOf(a2, a1) }
        assertEquals(listOf(d1, a2, d2, a1), model.contentUi.value.routines.map { it.id })
    }

    @Test fun deletingTheSelectedFolderSelectsTheDefault() {
        val model = ready()
        model.createRoutineFolder("Travel")
        awaitUi(model) { it.routineFolders.any { folder -> folder.name == "Travel" } && it.preferences.selectedRoutineFolderId != RoutineFolder.DEFAULT_ID }
        val folder = model.contentUi.value.preferences.selectedRoutineFolderId
        model.createRoutine("Hotel gym", folder, "hotel")
        awaitUi(model) { it.routines.any { routine -> routine.id == "hotel" } }

        model.deleteRoutineFolder(folder)
        awaitUi(model) { it.routineFolders.none { f -> f.id == folder } && it.preferences.selectedRoutineFolderId == RoutineFolder.DEFAULT_ID }
        assertEquals(RoutineFolder.DEFAULT_ID, model.contentUi.value.routines.single { it.id == "hotel" }.folderId)
        assertEquals(RoutineFolder.DEFAULT_ID, runBlocking(Dispatchers.IO) { app.preferences.snapshot().selectedRoutineFolderId })
        assertNull(model.contentUi.value.errorCode)
    }

    @Test fun deletingAnotherFolderKeepsTheSelection() {
        val kept = seedFolder("Upper")
        val deleted = seedFolder("Lower")
        val model = ready()
        model.selectRoutineFolder(kept)
        awaitUi(model) { it.preferences.selectedRoutineFolderId == kept }
        model.deleteRoutineFolder(deleted)
        awaitUi(model) { it.routineFolders.none { f -> f.id == deleted } }
        // One more command behind the delete: had it changed the selection, this would see it.
        model.renameRoutineFolder(kept, "Upper body")
        awaitUi(model) { it.routineFolders.any { f -> f.name == "Upper body" } }
        assertEquals(kept, model.contentUi.value.preferences.selectedRoutineFolderId)
        assertEquals(kept, runBlocking(Dispatchers.IO) { app.preferences.snapshot().selectedRoutineFolderId })
    }

    @Test fun errorSubjectNamesTheFailedCommand() {
        val routineId = seedRoutine(0)
        val model = ready()
        model.createRoutine("Again", RoutineFolder.DEFAULT_ID, routineId)
        awaitUi(model) { it.errorCode != null }
        assertEquals(UiError.INVALID_VALUE, model.contentUi.value.errorCode)
        assertEquals(routineId, model.contentUi.value.errorSubject)
        assertEquals(mapOf(routineId to 1), model.contentUi.value.subjectFailures)
        assertEquals(1, snapshot().routines.size)

        // clearError publishes asynchronously: wait for it, or the first error still reads as the second.
        model.clearError()
        awaitUi(model) { it.errorCode == null }
        model.deleteRoutineFolder(RoutineFolder.DEFAULT_ID)
        awaitUi(model) { it.errorCode != null }
        assertEquals(RoutineFolder.DEFAULT_ID, model.contentUi.value.errorSubject)
    }

    @Test fun unrelatedErrorHasItsOwnSubject() {
        val exercise = catalog(1).single()
        val model = ready()
        model.createRoutine("Fresh", RoutineFolder.DEFAULT_ID, "fresh")
        model.addRoutineExercise("missing-routine", exercise)
        // The error can be published before the presentation of the routine saved ahead of it.
        awaitUi(model) { it.errorCode != null && it.routines.any { routine -> routine.id == "fresh" } }
        assertEquals("missing-routine", model.contentUi.value.errorSubject)
        // The screen that waits for "fresh" sees no failure of its own.
        assertNull(model.contentUi.value.subjectFailures["fresh"])

        // A command without a subject fails with none.
        model.clearError()
        awaitUi(model) { it.errorCode == null }
        model.renameRoutine("missing-routine", "Name")
        awaitUi(model) { it.errorCode != null }
        assertNull(model.contentUi.value.errorSubject)
    }

    // Preflight F10: a second failure before the screen saw the first replaces the error shown, but the first
    // subject's failure stays counted, so a screen waiting on it is released all the same.
    @Test fun aSecondFailureStillReleasesTheFirstWait() {
        val first = seedRoutine(0)
        val second = seedRoutine(0)
        val model = ready()
        model.createRoutine("A", RoutineFolder.DEFAULT_ID, first)
        model.createRoutine("B", RoutineFolder.DEFAULT_ID, second)
        awaitUi(model) { it.subjectFailures[second] == 1 }
        assertEquals(second, model.contentUi.value.errorSubject)
        assertEquals(mapOf(first to 1, second to 1), model.contentUi.value.subjectFailures)

        model.clearError()
        awaitUi(model) { it.errorCode == null }
        assertNull(model.contentUi.value.errorSubject)
        // The counts outlive the toast: a wait that started before the clear still finds its failure.
        assertEquals(mapOf(first to 1, second to 1), model.contentUi.value.subjectFailures)
        model.createRoutine("A again", RoutineFolder.DEFAULT_ID, first)
        awaitUi(model) { it.subjectFailures[first] == 2 }
    }

    @Test fun editingTheSourceRoutineMidWorkoutChangesOnlyTheTarget() {
        val routineId = seedRoutine(1)
        val model = ready()
        model.startWorkout(routineId)
        awaitUi(model) { !it.startingWorkout && it.active?.sourceRoutineId == routineId }
        val before = model.contentUi.value.active!!.exercises.single()
        val logged = snapshot().sets
        val plan = model.contentUi.value.routines.single { it.id == routineId }.exercises.single()
        assertEquals(plan.setTargets.map { TargetSetUi(it.reps, it.weightKg) }, before.target!!.working)

        model.updateRoutineSet(plan.setTargets[0].id, 12, 50.0)
        awaitUi(model) { it.active!!.exercises.single().target!!.working[0] == TargetSetUi(12, 50.0) }
        val after = model.contentUi.value.active!!.exercises.single()
        assertEquals(before.sets, after.sets)
        assertEquals(logged, snapshot().sets)
    }

    @Test fun restoreRoutineSetRefusedIsSetNotRestored() {
        val routineId = seedRoutine(1)
        val model = ready()
        val plan = model.contentUi.value.routines.single { it.id == routineId }.exercises.single()
        val removed = plan.setTargets.last()
        model.removeRoutineSet(removed.id)
        model.restoreRoutineSet(plan.id, plan.setTargets.lastIndex, removed)
        awaitUi(model) { it.routines.single { r -> r.id == routineId }.exercises.single().setTargets.map { s -> s.id } == plan.setTargets.map { s -> s.id } }
        assertNull(model.contentUi.value.errorCode)

        model.restoreRoutineSet(plan.id, plan.setTargets.lastIndex, removed)
        awaitUi(model) { it.errorCode != null }
        assertEquals(UiError.SET_NOT_RESTORED, model.contentUi.value.errorCode)
        assertEquals(removed.id, model.contentUi.value.errorSubject)
        assertEquals(plan.setTargets.size, snapshot().plannedSets(plan.id).size)
    }

    // (2026-10-31, owner's request) "Copy to all sets" and its undo go through the command loop, behind the edits
    // queued before them.
    @Test fun copyRoutineSetToAllAndItsUndoGoThroughTheLoop() {
        val routineId = seedRoutine(1)
        val model = ready()
        val plan = model.contentUi.value.routines.single { it.id == routineId }.exercises.single()
        val (first, second) = plan.setTargets
        assertTrue(second.inherited)

        // The edit queued just before is the value copied.
        model.updateRoutineSet(first.id, 8, 60.0)
        model.copyRoutineSetToAll(plan.id, first.id)
        awaitUi(model) { entry(it, routineId).setTargets[1] == second.copy(reps = 8, weightKg = 60.0, inherited = false) }
        assertNull(model.contentUi.value.errorCode)

        model.restoreRoutineSetTargets(plan.id, plan.exercise.id, listOf(second))
        awaitUi(model) { entry(it, routineId).setTargets[1] == second }
        assertEquals(first.copy(reps = 8, weightKg = 60.0, inherited = false), entry(model.contentUi.value, routineId).setTargets[0])
        assertNull(model.contentUi.value.errorCode)
    }

    @Test fun copyAndRestoreFailuresCountUnderTheEntry() {
        val routineId = seedRoutine(1)
        val model = ready()
        val plan = model.contentUi.value.routines.single { it.id == routineId }.exercises.single()

        model.copyRoutineSetToAll(plan.id, "missing-set")
        awaitUi(model) { it.subjectFailures[plan.id] == 1 }
        assertEquals(plan.id, model.contentUi.value.errorSubject)

        model.clearError()
        awaitUi(model) { it.errorCode == null }
        model.restoreRoutineSetTargets(plan.id, plan.exercise.id, listOf(plan.setTargets[1].copy(id = "missing-set")))
        awaitUi(model) { it.subjectFailures[plan.id] == 2 }
        assertEquals(UiError.SET_NOT_RESTORED, model.contentUi.value.errorCode)
        assertEquals(plan.id, model.contentUi.value.errorSubject)
        assertEquals(plan.setTargets, entry(model.contentUi.value, routineId).setTargets)
    }

    private fun entry(state: UiState, routineId: String) = state.routines.single { it.id == routineId }.exercises.single()

    private fun seedFolder(name: String): String = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        app.repository.createRoutineFolder(name)
    }

    /** A routine with [exercises] catalog exercises, each with its two default sets. */
    private fun seedRoutine(exercises: Int): String = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        val routineId = app.repository.createRoutine("Saved routine")
        catalog(exercises).forEach { app.repository.addRoutineExercise(routineId, it) }
        routineId
    }

    private fun catalog(count: Int): List<String> = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        app.repository.snapshot().exercises.filter { it.convention == LoadConvention.TOTAL_LOAD }.take(count).map { it.id }
    }

    private fun ready(): AppViewModel = AppViewModel(app).also { models += it }.also { model ->
        awaitUi(model) { !it.loading && it.routineFolders.any { folder -> folder.id == RoutineFolder.DEFAULT_ID } }
    }

    private fun snapshot(): AppData = runBlocking(Dispatchers.IO) { app.repository.snapshot() }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!runCatching { predicate(model.contentUi.value) }.getOrDefault(false) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }
}
