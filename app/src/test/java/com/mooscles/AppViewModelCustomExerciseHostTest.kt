package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.Equipment
import com.mooscles.data.Exercise
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.RoutineFolder
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

/** `createCustomExercise` with the screen's id, through the serialized command loop; real Room and DataStore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelCustomExerciseHostTest {
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

    @Test fun createReachesTheStateWithTheScreenId() {
        val model = newModel()
        awaitUi(model) { !it.loading }

        model.createCustomExercise("screen-id-1", "Supino articulado", "Chest", "Plate-loaded machine", "Total load")
        awaitUi(model) { it.exercises.any { exercise -> exercise.id == "screen-id-1" } }

        val shown = model.contentUi.value.exercises.single { it.id == "screen-id-1" }
        assertEquals("Supino articulado", shown.name)
        assertTrue(shown.custom)
        assertNull(model.contentUi.value.errorCode)
        assertEquals(
            Exercise("screen-id-1", "Supino articulado", MuscleGroup.CHEST, Equipment.PLATE_LOADED_MACHINE, LoadConvention.TOTAL_LOAD, custom = true),
            runBlocking(Dispatchers.IO) { app.repository.exercise("screen-id-1") },
        )
    }

    @Test fun aRefusedCreateSetsTheErrorCode() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.createCustomExercise("taken", "Original press", "Chest", "Dumbbell", "Per hand")
        awaitUi(model) { it.exercises.any { exercise -> exercise.id == "taken" } }

        model.createCustomExercise("taken", "Replacement", "Back", "Cable", "Total load")
        awaitUi(model) { it.errorCode != null }

        assertEquals(UiError.INVALID_VALUE, model.contentUi.value.errorCode)
        assertEquals("Original press", model.contentUi.value.exercises.single { it.id == "taken" }.name)
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
