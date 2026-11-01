package com.mooscles

import android.Manifest
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.LoadConvention
import com.mooscles.data.RoutineFolder
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdatePhase
import com.mooscles.updates.AvailableUpdate
import com.mooscles.updates.UpdateManager
import com.mooscles.updates.UpdateMetadata
import com.mooscles.updates.UpdateSnapshot
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

/**
 * The Install button reaches the installer only through [UpdateManager.requestInstall], the path that
 * takes the training gate: an active workout defers it, and its permission intent is handed to the Activity.
 * Real Room, DataStore and UpdateManager; no APK is installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AppViewModelInstallHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()
    private val collector = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        collector.cancel()
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun installDuringAnActiveWorkoutIsDeferredByTheTrainingGate() {
        val routineId = seedRoutine()
        runBlocking(Dispatchers.IO) { app.repository.startRoutine(routineId) }
        val model = newModel()
        awaitUi(model) { !it.loading && it.active != null }
        val intents = collectIntents(model)
        offerUpdate()

        model.installUpdate()
        awaitCondition { updateState().phase == UpdatePhase.DEFERRED }

        val state = updateState()
        assertFalse("A deferred install must not ask for the install permission", state.sourcePermissionPending)
        assertEquals(-1, state.sessionId)
        assertNull("No installer screen may open during a workout", intents.get())
    }

    @Test fun installWithoutPermissionHandsTheSettingsIntentToTheActivity() {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        val model = newModel()
        awaitUi(model) { !it.loading && it.active == null }
        val intents = collectIntents(model)
        offerUpdate()

        model.installUpdate()
        awaitCondition { intents.get() != null }

        val state = updateState()
        assertEquals(UpdatePhase.PERMISSION_REQUIRED, state.phase)
        assertTrue("Only requestInstall marks the permission request as pending", state.sourcePermissionPending)
    }

    /** Automatic installation off, so only the explicit Install command can move the update. */
    private fun offerUpdate() = runBlocking(Dispatchers.IO) {
        val code = UpdateManager.installedVersion(app).first + 1
        val candidate = AvailableUpdate(
            UpdateMetadata(app.packageName, code, "next-$code", 26, "Mooscles-$code.apk", 123, "0".repeat(64)),
            "https://github.com/ReiTut/Mooscles/releases/download/v-next/Mooscles-$code.apk",
            "https://github.com/ReiTut/Mooscles/releases/tag/v-next", "Test release", false,
        )
        app.updates.store.change { it.copy(automatic = false, available = candidate, phase = UpdatePhase.READY) }
        Unit
    }

    private fun collectIntents(model: AppViewModel): AtomicReference<Intent?> {
        val received = AtomicReference<Intent?>(null)
        collector.launch { received.set(model.installIntents.first()) }
        return received
    }

    private fun updateState(): UpdateSnapshot = runBlocking(Dispatchers.IO) { app.updates.store.snapshot() }

    private fun seedRoutine(): String = runBlocking(Dispatchers.IO) {
        app.repository.initialize()
        val routineId = app.repository.createRoutine("Saved routine")
        val exerciseId = app.repository.snapshot().exercises.first { it.convention == LoadConvention.TOTAL_LOAD }.id
        app.repository.addRoutineExercise(routineId, exerciseId)
        routineId
    }

    private fun newModel() = AppViewModel(app).also { models += it }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Condition was not reached", condition())
    }

    private fun awaitUi(model: AppViewModel, predicate: (UiState) -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (!predicate(model.contentUi.value) && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("Expected state was not published: ${model.contentUi.value.error}", predicate(model.contentUi.value))
    }
}
