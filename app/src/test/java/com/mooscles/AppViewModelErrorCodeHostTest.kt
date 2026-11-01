package com.mooscles

import android.Manifest
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.RoutineFolder
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdatePhase
import com.mooscles.updates.UpdateSnapshot
import kotlinx.coroutines.CancellationException
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
 * The view model publishes [UiState.errorCode] next to the same [UiState.error] text as before; real Room,
 * DataStore and UpdateManager. The timer's code is covered by RestTimerControllerTest and UiErrorsTest: the
 * view model here runs on the app's real timer, which has no seam to fail its storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelErrorCodeHostTest {
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

    @Test fun missingSetSetsTextAndCode() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.updateSet("missing-set", 8, 60.0, completed = false, warmup = false, assistance = false)
        awaitUi(model) { it.error != null }
        assertEquals("Set unavailable", model.contentUi.value.error)
        assertEquals(UiError.SET_GONE, model.contentUi.value.errorCode)
    }

    @Test fun installingUpdateBlocksWithTheSameText() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        runBlocking(Dispatchers.IO) { app.updates.store.change { it.copy(phase = UpdatePhase.INSTALLING) } }
        model.updateSet("missing-set", 8, 60.0, completed = false, warmup = false, assistance = false)
        awaitUi(model) { it.error != null }
        assertEquals("An update is being installed. Training will be available again shortly.", model.contentUi.value.error)
        assertEquals(UiError.BLOCKED_BY_UPDATE, model.contentUi.value.errorCode)
    }

    @Test fun cancelledCommandIsSaveFailed() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.enqueueCommandForTest { throw CancellationException("Child work was cancelled") }
        awaitUi(model) { it.error != null }
        assertEquals("Could not save this change. Please try again.", model.contentUi.value.error)
        assertEquals(UiError.SAVE_FAILED, model.contentUi.value.errorCode)
    }

    @Test fun clearErrorClearsBoth() {
        val model = newModel()
        awaitUi(model) { !it.loading }
        model.createRoutine("   ")
        awaitUi(model) { it.error != null }
        assertEquals("Enter a name.", model.contentUi.value.error)
        assertEquals(UiError.INVALID_VALUE, model.contentUi.value.errorCode)

        model.clearError()
        awaitUi(model) { it.error == null }
        assertNull(model.contentUi.value.errorCode)
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
