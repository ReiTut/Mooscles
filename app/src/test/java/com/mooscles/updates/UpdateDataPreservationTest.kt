package com.mooscles.updates

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInstaller
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.AppPreferences
import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.moosclesStore
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.UpdatePhase
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises cleanup against real Room and DataStore files without pretending to install an APK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateDataPreservationTest {
    private lateinit var context: Context
    private lateinit var state: UpdateStore
    private lateinit var temporary: File
    private val job = SupervisorJob()
    private val time = 10_000_000L
    private val databases = mutableListOf<WorkoutDatabase>()
    private val databaseNames = mutableListOf<String>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        temporary = Files.createTempDirectory("mooscles-update-preservation-").toFile()
        state = UpdateStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(temporary, "updates.preferences_pb") },
        ))
    }

    @After fun cleanup() = runBlocking {
        databases.forEach { it.close() }
        databaseNames.forEach(context::deleteDatabase)
        job.cancelAndJoin()
        temporary.listFiles()?.forEach { it.delete() }
        temporary.delete()
        Unit
    }

    @Test fun higherInstalledVersionCleansOnlyUpdaterStateAndRetainsDiskWorkoutDataPreferencesAndTimer() = runBlocking {
        val name = "upgrade-preservation-${UUID.randomUUID()}.db"
        databaseNames += name
        // Robolectric prefixes its data directory with the full test name. Keep the native
        // SQLite path short enough for Windows while still testing an actual disk database.
        val databaseContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(temporary, name)
        }
        fun open(): WorkoutDatabase = Room.databaseBuilder(databaseContext, WorkoutDatabase::class.java, name).setDriver(AndroidSQLiteDriver()).build().also { databases += it }
        val oldDatabase = open()
        val repo = WorkoutRepository(oldDatabase)
        repo.initialize()
        val exerciseId = repo.createCustomExercise("Preserved bench", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
        val routine = repo.createRoutine("Saved routine")
        repo.addRoutineExercise(routine, exerciseId)
        repo.updateRoutineExercise(repo.snapshot().routineItems(routine).single().copy(
            plannedSets = 4, targetReps = 7, targetWeightKg = 65.0, restSeconds = 135,
        ))
        for ((index, load) in listOf(60.0, 65.0).withIndex()) {
            val workout = repo.startRoutine(routine, now = (index + 1) * 1_000L)
            val entry = repo.snapshot().workoutItems(workout).single()
            val set = repo.snapshot().exerciseSets(entry.id).first()
            repo.updateSet(set.copy(weightKg = load, reps = 8 - index, completed = true))
            repo.finishWorkout(workout, now = (index + 1) * 1_000L + 500)
        }
        val activeId = repo.startRoutine(routine, now = 5_000L)
        val activeEntry = repo.snapshot().workoutItems(activeId).single()
        repo.updateSet(repo.snapshot().exerciseSets(activeEntry.id).last().copy(weightKg = 67.5, reps = 5))
        val preferences = AppPreferences(context)
        preferences.pounds(true)
        preferences.theme("dark")
        preferences.autoRest(false)
        preferences.defaultRest(135)
        val timer = RestTimerPersistence(context.moosclesStore) { time }
        val savedTimer = timer.start("Preserved bench", 135)
        val before = repo.snapshot()
        val beforeRecords = WorkoutCalculations.records(before)
        assertTrue(beforeRecords.isNotEmpty())
        val beforePreferences = context.moosclesStore.data.first()
        oldDatabase.close()

        val installedCode = UpdateManager.installedVersion(context).first
        val next = available(installedCode + 1)
        state.change { it.copy(available = next, phase = UpdatePhase.INSTALLING,
            lastInstalledVersion = installedCode, downloadedBytes = 123, installAttemptedVersion = next.metadata.versionCode) }
        val updateDirectory = File(context.filesDir, "updates").apply { mkdirs() }
        File(updateDirectory, "update-${next.metadata.versionCode}.apk").writeText("obsolete update")
        File(updateDirectory, "update-stale.part").writeText("partial download")
        val unrelated = File(context.filesDir, "retained-training-${UUID.randomUUID()}.txt").apply { writeText("keep") }
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0).apply {
            longVersionCode = next.metadata.versionCode
            versionName = next.metadata.versionName
        }
        shadowOf(context.packageManager).installPackage(packageInfo)
        manager { true }.reconcile()

        val updated = state.snapshot()
        assertEquals(UpdatePhase.SUCCEEDED, updated.phase)
        assertNull(updated.available)
        assertEquals(-1, updated.sessionId)
        assertNull(updated.pendingActionUri)
        assertEquals(next.metadata.versionCode, updated.lastInstalledVersion)
        assertTrue(updateDirectory.listFiles().orEmpty().isEmpty())
        assertEquals("keep", unrelated.readText())
        unrelated.delete()
        val restored = WorkoutRepository(open()).snapshot()
        assertEquals(before, restored)
        assertEquals(activeId, restored.activeWorkout?.id)
        assertEquals(beforeRecords, WorkoutCalculations.records(restored))
        assertEquals(beforePreferences, context.moosclesStore.data.first())
        assertEquals(savedTimer, RestTimerPersistence(context.moosclesStore) { time }.snapshot())
        assertEquals("dark", AppPreferences(context).flow.first().theme)
    }

    @Test fun activeTrainingDefersInstallationAndAutomaticOffLeavesManualInstallationReady() = runBlocking {
        var busy = true
        state.change { it.copy(available = available(), phase = UpdatePhase.READY) }
        val manager = manager { busy }
        manager.onTrainingChanged()
        assertEquals(UpdatePhase.DEFERRED, state.snapshot().phase)
        assertEquals(-1, state.snapshot().sessionId)
        assertFalse(state.snapshot().sourcePermissionPending)
        state.change { it.copy(automatic = false) }
        busy = false
        manager.onTrainingChanged()
        assertEquals(UpdatePhase.READY, state.snapshot().phase)
        assertEquals(-1, state.snapshot().sessionId)
        assertFalse(state.snapshot().sourcePermissionPending)
    }

    @Test fun explicitInstallationDuringTrainingCannotOpenPermissionOrApproval() = runBlocking {
        state.change { it.copy(available = available(), phase = UpdatePhase.READY) }
        assertNull(manager { true }.requestInstall())
        assertEquals(UpdatePhase.DEFERRED, state.snapshot().phase)
        assertFalse(state.snapshot().sourcePermissionPending)
        assertEquals(-1, state.snapshot().sessionId)
    }

    @Test fun deniedSourcePermissionWaitsForAnotherUserGesture() = runBlocking {
        shadowOf(context.packageManager).setCanRequestPackageInstalls(false)
        state.change { it.copy(available = available(), phase = UpdatePhase.PERMISSION_REQUIRED,
            sourcePermissionPending = false, installAttemptedVersion = available().metadata.versionCode) }
        val manager = manager { false }
        repeat(3) { manager.onTrainingChanged() }
        assertEquals(UpdatePhase.PERMISSION_REQUIRED, state.snapshot().phase)
        assertFalse(state.snapshot().sourcePermissionPending)
        assertEquals(-1, state.snapshot().sessionId)
        assertNotNull(manager.requestInstall())
        assertTrue(state.snapshot().sourcePermissionPending)
        assertNull(manager.requestInstall())
        assertTrue(state.snapshot().sourcePermissionPending)
        assertEquals(UpdatePhase.PERMISSION_REQUIRED, state.snapshot().phase)
    }

    @Test fun installerCallbacksRejectStaleSessionsAndPreserveCancellationAndFailure() = runBlocking {
        val candidate = available()
        state.change { it.copy(available = candidate, phase = UpdatePhase.INSTALLING,
            sessionId = 42, installAttemptedVersion = candidate.metadata.versionCode) }
        val manager = manager { true }
        manager.onInstallerResult(99, PackageInstaller.STATUS_SUCCESS, null, null)
        assertEquals(UpdatePhase.INSTALLING, state.snapshot().phase)
        manager.onInstallerResult(42, PackageInstaller.STATUS_PENDING_USER_ACTION, null, "intent:pending-system-approval")
        assertEquals(UpdatePhase.DEFERRED, state.snapshot().phase)
        assertEquals("intent:pending-system-approval", state.snapshot().pendingActionUri)
        manager.onInstallerResult(42, PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        assertEquals(UpdatePhase.CANCELLED, state.snapshot().phase)
        assertNull(state.snapshot().pendingActionUri)
        assertEquals(candidate.metadata.versionCode, state.snapshot().cancelledVersion)
        state.change { it.copy(phase = UpdatePhase.INSTALLING, sessionId = 43) }
        manager.onInstallerResult(43, PackageInstaller.STATUS_FAILURE_STORAGE, null, null)
        assertEquals(UpdatePhase.FAILED, state.snapshot().phase)
        assertEquals(-1, state.snapshot().sessionId)
        assertTrue(state.snapshot().message.orEmpty().contains("storage"))
    }

    @Test fun interruptedPreparationCannotLeaveTrainingBlockedAfterRestart() = runBlocking {
        state.change { it.copy(available = available(), phase = UpdatePhase.INSTALLING, sessionId = -1) }
        val manager = manager { false }
        manager.reconcile()
        assertNotEquals(UpdatePhase.INSTALLING, state.snapshot().phase)
        manager.assertTrainingMayStart()

        val session = context.packageManager.packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
        )
        assertEquals(false, UpdateInstaller(context).sessionSealed(session))
        state.change { it.copy(phase = UpdatePhase.INSTALLING, sessionId = session) }
        manager.reconcile()
        assertNotEquals(UpdatePhase.INSTALLING, state.snapshot().phase)
        assertFalse(UpdateInstaller(context).sessionExists(session))
        manager.assertTrainingMayStart()
    }

    private fun manager(busy: suspend () -> Boolean) = UpdateManager(context, Mutex(), busy, state, now = { time })

    private fun available(code: Long = UpdateManager.installedVersion(context).first + 1) = AvailableUpdate(
        UpdateMetadata(context.packageName, code, "next-$code", 26, "Mooscles-$code.apk", 123, "0".repeat(64)),
        "https://github.com/ReiTut/Mooscles/releases/download/v-next/Mooscles-$code.apk",
        "https://github.com/ReiTut/Mooscles/releases/tag/v-next", "Test release", false,
    )
}
