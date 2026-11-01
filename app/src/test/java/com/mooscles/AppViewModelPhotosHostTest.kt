package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.RoutineFolder
import com.mooscles.photos.ExercisePhotoStore
import com.mooscles.photos.PhotoFile
import com.mooscles.ui.UiState
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * The orphan photo sweep through the real view model, Room and DataStore (phase 4B, preflight F4): it runs only
 * after an initialize that succeeded, with ids from a read of its own, and before the first command.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelPhotosHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<MoosclesViewModel>()

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

    @Test fun orphanPhotosAreRemovedAfterInitialize() {
        runBlocking(Dispatchers.IO) {
            app.repository.initialize()
            app.repository.createCustomExercise("Cable fly", MuscleGroup.CHEST, Equipment.CABLE, LoadConvention.TOTAL_LOAD, "custom-keep")
        }
        val photos = RecordingPhotos()
        val model = viewModel(photos)
        awaitUi(model) { !it.loading }
        awaitTrue { photos.sweeps.isNotEmpty() }
        assertEquals(1, photos.sweeps.size)
        // Only the custom exercise's id: a catalog id never owns a photo.
        assertEquals(setOf("custom-keep"), photos.sweeps.single())
    }

    @Test fun aFailingCleanupDoesNotBlockTheApp() {
        val photos = RecordingPhotos(failWith = IOException("disk full"))
        val model = viewModel(photos)
        awaitUi(model) { !it.loading }
        awaitTrue { photos.sweeps.isNotEmpty() }
        model.createRoutine("Still works", RoutineFolder.DEFAULT_ID, "after-failure")
        awaitUi(model) { it.routines.any { routine -> routine.id == "after-failure" } }
        assertNull(model.contentUi.value.error)
    }

    @Test fun failedInitializeSkipsTheCleanup() {
        runBlocking(Dispatchers.IO) { app.repository.initialize() }
        // A trigger makes only the folder seed fail: initialize throws, yet a plain read of the data still works. The
        // sweep must still not run, because the ids it would use come from a database that did not start cleanly.
        android.database.sqlite.SQLiteDatabase.openDatabase(app.getDatabasePath("mooscles.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("CREATE TRIGGER block_seed BEFORE INSERT ON routine_folders BEGIN SELECT RAISE(ABORT, 'blocked'); END")
        }
        val photos = RecordingPhotos()
        val model = viewModel(photos)
        awaitUi(model) { it.error != null }
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertEquals(emptyList<Set<String>>(), photos.sweeps)
    }

    @Test fun cleanupRunsBeforeTheFirstCommand() {
        val gate = CompletableDeferred<Unit>()
        val events = Collections.synchronizedList(mutableListOf<String>())
        val photos = RecordingPhotos(gate = gate, events = events)
        val model = viewModel(photos)
        // Queued while the sweep is still waiting: it must not start before the sweep ends.
        model.enqueueCommandForTest { events += "command" }
        awaitTrue { "sweep-start" in events }
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        assertEquals(listOf("sweep-start"), events.toList())
        gate.complete(Unit)
        awaitTrue { "command" in events }
        assertEquals(listOf("sweep-start", "sweep-end", "command"), events.toList())
    }

    private fun viewModel(photos: ExercisePhotoStore): MoosclesViewModel {
        val base = app.services
        val services = MoosclesServices(base.repository, base.preferences, base.timer, base.updates, base.trainingGate, base.documents, base.about, photos)
        return object : MoosclesViewModel(services, AndroidViewModelPlatform()) {
            override suspend fun install() {}
        }.also { models += it }
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Condition was not met in time", condition())
    }

    private fun awaitUi(model: MoosclesViewModel, predicate: (UiState) -> Boolean) =
        awaitTrue { runCatching { predicate(model.contentUi.value) }.getOrDefault(false) }
}

/** Records every sweep; can fail, or wait on [gate], to probe how the view model reacts. */
private class RecordingPhotos(
    private val failWith: Exception? = null,
    private val gate: CompletableDeferred<Unit>? = null,
    private val events: MutableList<String> = Collections.synchronizedList(mutableListOf()),
) : ExercisePhotoStore {
    val sweeps: MutableList<Set<String>> = Collections.synchronizedList(mutableListOf())
    override val revision: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun stage(draftKey: String, source: String) = false
    override suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile? = null
    override suspend fun commit(draftKey: String, exerciseId: String) = false
    override suspend fun discard(draftKey: String) {}
    override suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile? = null
    override suspend fun remove(exerciseId: String) {}
    override suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long) {
        events += "sweep-start"
        sweeps += customExerciseIds
        gate?.await()
        failWith?.let { throw it }
        events += "sweep-end"
    }
}
