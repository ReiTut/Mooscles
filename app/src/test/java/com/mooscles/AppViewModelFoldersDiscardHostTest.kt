package com.mooscles

import androidx.room.execSQL
import androidx.room.useWriterConnection
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.AppData
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineFolder
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.data.open
import com.mooscles.timer.RestNotifications
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

/** Real Room/DataStore command, persistence and session-end checks; no device timing claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelFoldersDiscardHostTest {
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

    @Test fun rapidFolderSelectionThenCreationUsesSavedSelectionAndRenameKeepsIdentity() {
        val model = newModel()
        awaitUi(model) { !it.loading && it.routineFolders.any { folder -> folder.id == RoutineFolder.DEFAULT_ID } }
        assertEquals(RoutineFolder.DEFAULT_ID, model.contentUi.value.preferences.selectedRoutineFolderId)
        model.createRoutineFolder("Upper")
        model.createRoutineFolder("Lower")
        awaitUi(model) { it.routineFolders.any { folder -> folder.name == "Upper" } &&
            it.routineFolders.any { folder -> folder.name == "Lower" } }
        val upper = model.contentUi.value.routineFolders.single { it.name == "Upper" }.id
        val lower = model.contentUi.value.routineFolders.single { it.name == "Lower" }.id

        // Do not wait for display preferences between selection and creation: the
        // serialized command must read the durable selection, not stale screen text.
        model.selectRoutineFolder(upper)
        model.createRoutine("Upper routine")
        model.selectRoutineFolder(lower)
        model.createRoutine("Lower routine")
        model.renameRoutineFolder(upper, "Upper strength")
        awaitUi(model) { it.routines.any { routine -> routine.name == "Upper routine" && routine.folderId == upper } &&
            it.routines.any { routine -> routine.name == "Lower routine" && routine.folderId == lower } &&
            it.routineFolders.any { folder -> folder.id == upper && folder.name == "Upper strength" } &&
            it.preferences.selectedRoutineFolderId == lower }
        val saved = snapshot()
        assertEquals(3, saved.routineFolders.size)
        assertEquals(upper, saved.routines.single { it.name == "Upper routine" }.folderId)
        assertEquals(lower, saved.routines.single { it.name == "Lower routine" }.folderId)
        assertEquals(lower, runBlocking(Dispatchers.IO) { app.preferences.snapshot().selectedRoutineFolderId })
    }

    @Test fun movedRoutineAndSelectedFolderSurviveReopeningWithoutChangingActiveSnapshot() {
        val routineId = seedRoutine()
        val model = newModel()
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }
        model.createRoutineFolder("Travel")
        awaitUi(model) { it.routineFolders.any { folder -> folder.name == "Travel" } }
        val folderId = model.contentUi.value.routineFolders.single { it.name == "Travel" }.id
        model.startWorkout(routineId)
        awaitUi(model) { !it.startingWorkout && it.active?.sourceRoutineId == routineId }
        val active = model.contentUi.value.active!!
        val before = snapshot()

        model.selectRoutineFolder(RoutineFolder.DEFAULT_ID)
        model.moveRoutineToFolder(routineId, folderId)
        model.selectRoutineFolder(folderId)
        awaitUi(model) { it.preferences.selectedRoutineFolderId == folderId &&
            it.routines.any { routine -> routine.id == routineId && routine.folderId == folderId } }
        assertEquals(active, model.contentUi.value.active)
        val after = snapshot()
        assertEquals(before.workouts, after.workouts)
        assertEquals(before.workoutExercises, after.workoutExercises)
        assertEquals(before.sets, after.sets)
        assertEquals(before.routineExercises, after.routineExercises)
        assertEquals(before.routineSets, after.routineSets)

        model.viewModelScope.cancel()
        val reopened = newModel()
        awaitUi(reopened) { !it.loading && it.preferences.selectedRoutineFolderId == folderId && it.active == active }
        assertEquals(folderId, reopened.contentUi.value.routines.single { it.id == routineId }.folderId)
        val disk = reopenedDatabaseSnapshot()
        assertEquals(after.routineFolders, disk.routineFolders)
        assertEquals(after.routines, disk.routines)
        assertEquals(before.workouts, disk.workouts)
        assertEquals(before.workoutExercises, disk.workoutExercises)
        assertEquals(before.sets, disk.sets)
    }

    @Test fun repeatedStartAndDiscardPublishOneNavigationPerCycleAfterDurableTimerCleanup() {
        val routineId = seedRoutine()
        val model = newModel()
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }
        val notifications = app.getSystemService(NotificationManager::class.java)
        val alarms = app.getSystemService(AlarmManager::class.java)
        repeat(3) { cycle ->
            val previousEvent = model.contentUi.value.discardNavigationRequest
            repeat(15) { model.startWorkout(routineId) }
            awaitUi(model) { !it.startingWorkout && it.active?.sourceRoutineId == routineId }
            val started = model.contentUi.value.active!!
            assertEquals(1, snapshot().workouts.size)
            model.startTimer("Discard cycle $cycle", 120)
            awaitUi(model) { it.timer.running && it.timer.exerciseName == "Discard cycle $cycle" }
            // The timer UI reads DataStore, which shows "running" before RestTimerController.schedule()
            // registers the alarm, and the session observer cancels then sets it again. So wait for
            // exactly one alarm (bounded) instead of counting at the first running state.
            awaitCondition({ "Expected one rest alarm, saw ${shadowOf(alarms).scheduledAlarms.size}" }) {
                shadowOf(alarms).scheduledAlarms.size == 1
            }
            notifications.notify(1, Notification.Builder(app, "rest")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Rest complete").build())
            // No Activity is in front here, so the running rest also has its ongoing notification (D16).
            awaitCondition({ "Expected the finished and the progress notifications" }) {
                shadowOf(notifications).getNotification(RestNotifications.PROGRESS_ID) != null
            }
            assertEquals(2, shadowOf(notifications).allNotifications.size)

            repeat(15) { model.discardWorkout() }
            awaitUi(model) { !it.discardingWorkout && it.active == null &&
                it.discardNavigationRequest == previousEvent + 1 }
            val saved = snapshot()
            assertNull(saved.activeWorkout)
            assertTrue(saved.workouts.none { it.id == started.id })
            assertTrue(saved.workoutExercises.none { it.workoutId == started.id })
            assertTrue(saved.sets.isEmpty())
            val timer = runBlocking(Dispatchers.IO) { RestTimerPersistence(app.moosclesStore).snapshot() }
            assertNull(timer.workoutId)
            assertFalse(timer.running)
            assertFalse(timer.paused)
            assertFalse(timer.finished)
            assertEquals(0, shadowOf(alarms).scheduledAlarms.size)
            assertEquals(0, shadowOf(notifications).allNotifications.size)
            assertNull(reopenedDatabaseSnapshot().activeWorkout)
        }
        assertEquals(3L, model.contentUi.value.discardNavigationRequest)
        assertEquals(1, snapshot().routines.size)
    }

    @Test fun databaseDiscardFailureReleasesBusyStateWithoutNavigationAndAllowsRetry() {
        val routineId = seedRoutine()
        val model = newModel()
        awaitUi(model) { !it.loading && it.routines.any { routine -> routine.id == routineId } }
        model.startWorkout(routineId)
        awaitUi(model) { !it.startingWorkout && it.active != null }
        val active = model.contentUi.value.active!!
        val before = snapshot()
        val previousEvent = model.contentUi.value.discardNavigationRequest
        runBlocking(Dispatchers.IO) {
            app.database.useWriterConnection { it.execSQL(
                "CREATE TRIGGER reject_discard_for_test BEFORE DELETE ON workouts BEGIN SELECT RAISE(ABORT, 'Injected discard failure'); END") }
        }
        try {
            repeat(10) { model.discardWorkout() }
            awaitUi(model) { !it.discardingWorkout && it.error?.contains("Injected discard failure") == true }
            assertEquals(previousEvent, model.contentUi.value.discardNavigationRequest)
            assertEquals(active, model.contentUi.value.active)
            assertEquals(before.workouts, snapshot().workouts)
            assertEquals(before.workoutExercises, snapshot().workoutExercises)
            assertEquals(before.sets, snapshot().sets)
        } finally {
            runBlocking(Dispatchers.IO) {
                app.database.useWriterConnection { it.execSQL("DROP TRIGGER IF EXISTS reject_discard_for_test") }
            }
        }
        model.clearError()
        model.discardWorkout()
        awaitUi(model) { !it.discardingWorkout && it.active == null &&
            it.discardNavigationRequest == previousEvent + 1 }
        assertNull(reopenedDatabaseSnapshot().activeWorkout)
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

    private fun reopenedDatabaseSnapshot(): AppData = runBlocking(Dispatchers.IO) {
        val reopened = WorkoutDatabase.open(app)
        try { WorkoutRepository(reopened).snapshot() } finally { reopened.close() }
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        awaitCondition({ "Expected state was not published: ${model.ui.value.error}" }) { predicate(model.ui.value) }
    }

    /** Polls a condition other threads make true, bounded so a real failure still fails. */
    private fun awaitCondition(message: () -> String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(message(), condition())
    }
}
