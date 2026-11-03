package com.mooscles.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInstaller
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mooscles.AppPreferences
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.ui.UpdatePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Actual persisted updater state and coordinator paths, independent of internet and installers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateStorePolicyTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: UpdateStore
    private var storeJob: Job? = null
    private var now = 10_000_000L
    private var busy = false
    private val sha = "ab".repeat(32)

    @Before fun open() {
        file = Files.createTempDirectory("mooscles-update-test-").resolve("updates.preferences_pb").toFile()
        openStore()
    }

    private fun openStore() {
        val job = SupervisorJob()
        storeJob = job
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        store = UpdateStore(dataStore)
    }

    private suspend fun reopen() {
        storeJob!!.cancelAndJoin()
        openStore()
    }

    @After fun close() = runBlocking {
        storeJob?.cancelAndJoin()
        file.delete()
        file.parentFile?.delete()
        Unit
    }

    private fun available(code: Long = UpdateManager.installedVersion(context).first + 1) = AvailableUpdate(
        UpdateMetadata(context.packageName, code, "test-$code", 26, "Mooscles-$code.apk", 2000, sha),
        "https://github.com/ReiTut/Mooscles/releases/download/v$code/Mooscles-$code.apk",
        "https://github.com/ReiTut/Mooscles/releases/tag/v$code", "Updater test release", false,
    )

    private fun manager(transport: HttpTransport = HttpTransport { _, _ -> HttpResponse(200, body = "[]") }) =
        UpdateManager(context, Mutex(), { busy }, store, ReleaseClient(transport) { now }) { now }

    private fun releaseTransport(candidate: AvailableUpdate, calls: AtomicInteger = AtomicInteger()) = HttpTransport { url, _ ->
        calls.incrementAndGet()
        if (url == ReleaseUrls.API_URL) {
            val metadataUrl = candidate.apkUrl.substringBeforeLast('/') + "/update.json"
            HttpResponse(200, mapOf("ETag" to "list"), JSONArray().put(JSONObject()
                .put("draft", false).put("prerelease", candidate.prerelease)
                .put("published_at", "2026-10-21T00:00:00Z").put("html_url", candidate.releaseUrl)
                .put("body", candidate.releaseNotes).put("assets", JSONArray()
                    .put(JSONObject().put("name", "update.json").put("state", "uploaded").put("size", 300)
                        .put("browser_download_url", metadataUrl))
                    .put(JSONObject().put("name", candidate.metadata.apkFileName).put("state", "uploaded")
                        .put("size", candidate.metadata.apkSize).put("browser_download_url", candidate.apkUrl)))).toString())
        } else HttpResponse(200, mapOf("ETag" to "metadata"), UpdateJson.encodeMetadata(candidate.metadata))
    }

    @Test fun defaultsEnableAutomaticUpdatesAndWifiButKeepPrereleasesOptIn() = runBlocking {
        val state = store.snapshot()
        assertTrue(state.automatic)
        assertTrue(state.wifiOnly)
        assertFalse(state.prereleases)
        assertEquals(UpdatePhase.IDLE, state.phase)
        assertNull(state.available)
        assertEquals(-1, state.sessionId)
    }

    @Test fun reopeningRetainsDownloadSelectionProgressPreferencesAndHttpCache() = runBlocking {
        val candidate = available()
        val saved = store.change { it.copy(automatic = false, wifiOnly = false, prereleases = true,
            available = candidate, phase = UpdatePhase.DOWNLOADING, checkedAt = 100, attemptedAt = 95,
            downloadedBytes = 1024, generation = 4, cache = ReleaseCache(mapOf(ReleaseUrls.API_URL to CachedResponse("etag", "[]")))) }
        reopen()
        assertEquals(saved, store.snapshot())
    }

    @Test fun reopeningRetainsInstallApprovalCancellationAndRateLimitState() = runBlocking {
        val candidate = available()
        val saved = store.change { it.copy(available = candidate, phase = UpdatePhase.APPROVAL_REQUIRED,
            sessionId = 42, pendingActionUri = "intent:#Intent;action=android.content.pm.action.CONFIRM_INSTALL;end",
            sourcePermissionPending = true, installAttemptedVersion = candidate.metadata.versionCode,
            cancelledVersion = candidate.metadata.versionCode, checkFailures = 3, retryAt = 9_000_000,
            lastInstalledVersion = candidate.metadata.versionCode - 1, message = "Wait for approval") }
        reopen()
        assertEquals(saved, store.snapshot())
    }

    @Test fun persistedStateEditsAreAtomicAcrossConcurrentPreferenceChanges() = runBlocking {
        val jobs = (1..20).map { async(Dispatchers.Default) { store.change { it.copy(generation = it.generation + 1) } } }
        jobs.forEach { it.await() }
        assertEquals(20L, store.snapshot().generation)
    }

    @Test fun corruptedSavedUpdateStateRecoversWithoutTouchingTrainingState() {
        val failed = UpdateStore.decode("{corrupted")
        assertEquals(UpdatePhase.FAILED, failed.phase)
        assertTrue(failed.message!!.contains("workout data"))
        assertTrue(failed.automatic)
        assertNull(failed.available)
    }

    @Test fun foregroundAndManualThrottleHaveDifferentIntervalsAndBothHonorRateLimit() {
        val state = UpdateSnapshot(attemptedAt = now)
        assertFalse(UpdatePolicy.canCheck(state, false, now + UpdatePolicy.FOREGROUND_INTERVAL - 1))
        assertTrue(UpdatePolicy.canCheck(state, false, now + UpdatePolicy.FOREGROUND_INTERVAL))
        assertFalse(UpdatePolicy.canCheck(state, true, now + UpdatePolicy.MANUAL_INTERVAL - 1))
        assertTrue(UpdatePolicy.canCheck(state, true, now + UpdatePolicy.MANUAL_INTERVAL))
        val limited = state.copy(retryAt = now + 2 * UpdatePolicy.FOREGROUND_INTERVAL)
        assertFalse(UpdatePolicy.canCheck(limited, true, now + UpdatePolicy.FOREGROUND_INTERVAL))
        assertTrue(UpdatePolicy.canCheck(limited, false, limited.retryAt))
        assertTrue(UpdatePolicy.canCheck(UpdateSnapshot(), false, now))
    }

    @Test fun retryBackoffGrowsAndRemainsBoundedAndInstallGuardCoversBothTrainingStates() {
        assertEquals(60_000L, UpdatePolicy.retryDelay(0))
        assertEquals(120_000L, UpdatePolicy.retryDelay(1))
        assertTrue(UpdatePolicy.retryDelay(100) <= 6 * 60 * 60_000L)
        assertEquals(UpdatePolicy.retryDelay(100), UpdatePolicy.retryDelay(101))
        assertTrue(UpdatePolicy.mayInstall(false, false))
        assertFalse(UpdatePolicy.mayInstall(true, false))
        assertFalse(UpdatePolicy.mayInstall(false, true))
        assertFalse(UpdatePolicy.mayInstall(true, true))
        assertTrue(UpdatePolicy.newer(6, 5))
        assertFalse(UpdatePolicy.newer(5, 5))
        assertFalse(UpdatePolicy.newer(4, 5))
    }

    @Test fun managerPersistsAvailableUpdateAndCacheWithoutDownloadingWhenAutomaticIsDisabled() = runBlocking {
        store.change { it.copy(automatic = false) }
        val candidate = available()
        assertFalse(manager(releaseTransport(candidate)).check(manual = false))
        val saved = store.snapshot()
        assertEquals(candidate, saved.available)
        assertEquals(UpdatePhase.AVAILABLE, saved.phase)
        assertEquals(now, saved.checkedAt)
        assertEquals(2, saved.cache.responses.size)
        reopen()
        assertEquals(saved, store.snapshot())
    }

    @Test fun simultaneousChecksAndAnImmediateManualRetryMakeOnlyOneRequest() = runBlocking {
        store.change { it.copy(automatic = false) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val updater = manager(HttpTransport { _, _ ->
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Test did not release its request" }
            HttpResponse(200, body = "[]")
        })
        val first = async(Dispatchers.Default) { updater.check(false) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = async(Dispatchers.Default) { updater.check(true) }
            assertFalse(withTimeout(5000) { second.await() })
        } finally { release.countDown() }
        assertFalse(first.await())
        assertFalse(updater.check(true))
        assertEquals(1, calls.get())
    }

    @Test fun offlineCheckPersistsRetryAndGatesRequestsAcrossProcessRecreation() = runBlocking {
        store.change { it.copy(automatic = false) }
        val calls = AtomicInteger()
        val offline = HttpTransport { _, _ -> calls.incrementAndGet(); throw IOException("offline") }
        assertTrue(manager(offline).check(false))
        val failed = store.snapshot()
        assertEquals(UpdatePhase.FAILED, failed.phase)
        assertEquals(1, failed.checkFailures)
        assertEquals(now + 60_000, failed.retryAt)
        reopen()
        assertTrue(manager(offline).check(true))
        assertEquals(1, calls.get())
        now += 60_000
        assertTrue(manager(offline).check(true))
        assertEquals(2, calls.get())
        assertEquals(2, store.snapshot().checkFailures)
        assertEquals(now + 120_000, store.snapshot().retryAt)
    }

    @Test fun githubRateLimitSurvivesRestartAndSuccessfulRetryClearsFailureState() = runBlocking {
        store.change { it.copy(automatic = false) }
        val calls = AtomicInteger()
        var limited = true
        val transport = HttpTransport { _, _ ->
            calls.incrementAndGet()
            if (limited) HttpResponse(429, mapOf("Retry-After" to "300")) else HttpResponse(200, body = "[]")
        }
        assertTrue(manager(transport).check(false))
        assertEquals(now + 300_000, store.snapshot().retryAt)
        reopen()
        now += 299_000
        assertTrue(manager(transport).check(true))
        assertEquals(1, calls.get())
        now += 1000
        limited = false
        assertFalse(manager(transport).check(true))
        assertEquals(2, calls.get())
        assertEquals(0, store.snapshot().checkFailures)
        assertEquals(0L, store.snapshot().retryAt)
        assertEquals(UpdatePhase.IDLE, store.snapshot().phase)
    }

    @Test fun installationCancellationIsPersistedAndLaterCheckDoesNotRestoreAutomaticDownload() = runBlocking {
        val candidate = available()
        store.change { it.copy(automatic = false, available = candidate, phase = UpdatePhase.INSTALLING,
            sessionId = 42, pendingActionUri = "system-approval") }
        val updater = manager(releaseTransport(candidate))
        updater.onInstallerResult(42, PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        assertEquals(UpdatePhase.CANCELLED, store.snapshot().phase)
        assertEquals(candidate.metadata.versionCode, store.snapshot().cancelledVersion)
        assertEquals(-1, store.snapshot().sessionId)
        assertNull(store.snapshot().pendingActionUri)
        updater.check(false)
        assertEquals(UpdatePhase.CANCELLED, store.snapshot().phase)
        reopen()
        assertEquals(candidate.metadata.versionCode, store.snapshot().cancelledVersion)
    }

    @Test fun actualManagerDefersInstallationAndPendingApprovalDuringActiveTraining() = runBlocking {
        busy = true
        val candidate = available()
        store.change { it.copy(available = candidate, phase = UpdatePhase.READY, sessionId = 42) }
        val updater = manager()
        assertNull(updater.requestInstall())
        assertEquals(UpdatePhase.DEFERRED, store.snapshot().phase)
        updater.onInstallerResult(42, PackageInstaller.STATUS_PENDING_USER_ACTION, null, "system-approval")
        assertEquals(UpdatePhase.DEFERRED, store.snapshot().phase)
        assertEquals("system-approval", store.snapshot().pendingActionUri)
        assertTrue(store.snapshot().message!!.contains("Finish your workout"))
    }

    @Test fun deferredManualUpdateBecomesReadyWhenTrainingFinishesWithAutomaticUpdatesDisabled() = runBlocking {
        busy = true
        val candidate = available()
        store.change { it.copy(automatic = false, available = candidate, phase = UpdatePhase.READY) }
        val updater = manager()
        assertNull(updater.requestInstall())
        assertEquals(UpdatePhase.DEFERRED, store.snapshot().phase)
        updater.onTrainingChanged()
        assertEquals(UpdatePhase.DEFERRED, store.snapshot().phase)
        busy = false
        updater.onTrainingChanged()
        val ready = store.snapshot()
        assertEquals(UpdatePhase.READY, ready.phase)
        assertEquals(candidate, ready.available)
        assertEquals(-1, ready.sessionId)
        assertTrue(ready.message!!.contains("workout is saved"))
        reopen()
        assertEquals(UpdatePhase.READY, store.snapshot().phase)
    }

    @Test fun deferredSystemApprovalBecomesApprovalRequiredAfterTrainingWithoutOpeningItAutomatically() = runBlocking {
        busy = true
        val candidate = available()
        store.change { it.copy(available = candidate, phase = UpdatePhase.DEFERRED,
            sessionId = 42, pendingActionUri = "pending-system-approval", installAttemptedVersion = candidate.metadata.versionCode) }
        val updater = manager()
        updater.onTrainingChanged()
        assertEquals(UpdatePhase.DEFERRED, store.snapshot().phase)
        busy = false
        updater.onTrainingChanged()
        val approval = store.snapshot()
        assertEquals(UpdatePhase.APPROVAL_REQUIRED, approval.phase)
        assertEquals("pending-system-approval", approval.pendingActionUri)
        assertEquals(42, approval.sessionId)
        assertTrue(approval.message!!.contains("installation approval"))
        reopen()
        assertEquals(approval, store.snapshot())
    }

    @Test fun ignoresStaleInstallerCallbacksInsteadOfMutatingANewerSession() = runBlocking {
        store.change { it.copy(available = available(), phase = UpdatePhase.INSTALLING, sessionId = 42) }
        val saved = store.snapshot()
        manager().onInstallerResult(41, PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        assertEquals(saved, store.snapshot())
    }

    @Test fun installationFailureRetainsCandidateForUserRetryWithoutLosingTrainingData() = runBlocking {
        val candidate = available()
        store.change { it.copy(available = candidate, phase = UpdatePhase.INSTALLING, sessionId = 42) }
        manager().onInstallerResult(42, PackageInstaller.STATUS_FAILURE_STORAGE, null, null)
        val failed = store.snapshot()
        assertEquals(UpdatePhase.FAILED, failed.phase)
        assertEquals(candidate, failed.available)
        assertEquals(-1, failed.sessionId)
        assertTrue(failed.message!!.contains("storage"))
    }

    @Test fun installedVersionCleanupDeletesOnlyUpdaterFilesAndPreservesRealWorkoutDatabaseAndPreferences() = runBlocking {
        val databaseName = "retained.db"
        // Native SQLite on Windows must not inherit Robolectric's long test-method directory.
        val databaseContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(file.parentFile, name)
        }
        var database = Room.databaseBuilder(databaseContext, WorkoutDatabase::class.java, databaseName).setDriver(AndroidSQLiteDriver()).build()
        try {
            val repo = WorkoutRepository(database)
            repo.initialize()
            val exercise = repo.snapshot().exercises.first()
            val routineId = repo.createRoutine("Saved routine")
            val routineExerciseId = repo.addRoutineExercise(routineId, exercise.id)
            val routineExercise = repo.snapshot().routineExercises.first { it.id == routineExerciseId }
            repo.updateRoutineExercise(routineExercise.copy(plannedSets = 4, targetReps = 8, restSeconds = 120))
            val workoutId = repo.startRoutine(routineId, now = 1000)
            val entry = repo.snapshot().workoutItems(workoutId).first()
            val set = repo.snapshot().exerciseSets(entry.id).first()
            repo.updateSet(set.copy(reps = 8, weightKg = 42.0, completed = true))
            assertTrue(repo.finishWorkout(workoutId, now = 100_000))
            val activeId = repo.startEmpty("In progress", now = 200_000)
            repo.addWorkoutExercise(activeId, exercise.id, 135)
            val before = repo.snapshot()
            val preferences = AppPreferences(context)
            preferences.pounds(true)
            preferences.defaultRest(135)
            val preferencesBefore = preferences.flow.first()
            val installed = UpdateManager.installedVersion(context).first
            store.change { it.copy(available = available(installed), phase = UpdatePhase.READY,
                downloadedBytes = 2000, lastInstalledVersion = installed - 1, cancelledVersion = installed,
                installAttemptedVersion = installed, pendingActionUri = "system-approval") }
            val directory = File(context.filesDir, "updates").also { it.mkdirs() }
            File(directory, "update-$installed.apk").writeText("obsolete verified update")
            File(directory, "orphan.part").writeText("obsolete partial")
            val outside = File(context.filesDir, "training-preservation-sentinel").apply { writeText("preserve me") }
            manager().reconcile()
            val reconciled = store.snapshot()
            assertEquals(UpdatePhase.SUCCEEDED, reconciled.phase)
            assertNull(reconciled.available)
            assertEquals(0L, reconciled.downloadedBytes)
            assertEquals(0L, reconciled.cancelledVersion)
            assertNull(reconciled.pendingActionUri)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertEquals("preserve me", outside.readText())
            database.close()
            database = Room.databaseBuilder(databaseContext, WorkoutDatabase::class.java, databaseName).setDriver(AndroidSQLiteDriver()).build()
            assertEquals(before, WorkoutRepository(database).snapshot())
            assertEquals(preferencesBefore, AppPreferences(context).flow.first())
            outside.delete()
        } finally {
            database.close()
            SQLiteDatabase.deleteDatabase(databaseContext.getDatabasePath(databaseName))
        }
        Unit
    }
}
