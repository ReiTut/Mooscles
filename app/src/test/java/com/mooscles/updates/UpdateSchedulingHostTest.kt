package com.mooscles.updates

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.mooscles.ui.UpdatePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Uses the real WorkManager database/scheduler while deliberately leaving network constraints unmet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateSchedulingHostTest {
    private lateinit var context: Context
    private lateinit var work: WorkManager
    private lateinit var temporary: File
    private lateinit var state: UpdateStore
    private lateinit var updater: UpdateManager
    private val job = SupervisorJob()
    private val workersCreated = AtomicInteger()
    private val httpCalls = AtomicInteger()

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        temporary = Files.createTempDirectory("mooscles-work-test-").toFile()
        state = UpdateStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(temporary, "updates.preferences_pb") },
        ))
        val executor = SynchronousExecutor()
        val configuration = Configuration.Builder()
            .setExecutor(executor)
            .setTaskExecutor(executor)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? {
                    workersCreated.incrementAndGet()
                    throw AssertionError("Network-constrained updater work must not run in this scheduling test")
                }
            }).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        work = WorkManager.getInstance(context)
        assertNotNull(WorkManagerTestInitHelper.getTestDriver(context))
        state.change { it.copy(automatic = false, available = candidate(), phase = UpdatePhase.AVAILABLE) }
        updater = UpdateManager(context, Mutex(), { false }, state,
            ReleaseClient(HttpTransport { _, _ ->
                httpCalls.incrementAndGet()
                throw AssertionError("Scheduling must not execute an HTTP request")
            })) { 10_000_000L }
        Unit
    }

    @After fun cleanup() = runBlocking {
        work.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
        job.cancelAndJoin()
        temporary.listFiles()?.forEach { it.delete() }
        temporary.delete()
        Unit
    }

    private fun candidate(): AvailableUpdate {
        val code = UpdateManager.installedVersion(context).first + 1
        return AvailableUpdate(UpdateMetadata(context.packageName, code, "test-$code", 26,
            "Mooscles-$code.apk", 2000, "ab".repeat(32)),
            "https://github.com/ReiTut/Mooscles/releases/download/v$code/Mooscles-$code.apk",
            "https://github.com/ReiTut/Mooscles/releases/tag/v$code", "Scheduling test", false)
    }

    private fun infos(name: String): List<WorkInfo> = work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS)

    @Test fun foregroundUsesUniqueSixHourNetworkPeriodicCheckAndKeepsExistingQueuedCheck() = runBlocking {
        updater.onForeground()
        val periodic = infos(UpdateManager.PERIODIC_WORK).single()
        val check = infos(UpdateManager.CHECK_WORK).single()
        assertEquals(WorkInfo.State.ENQUEUED, periodic.state)
        assertEquals(TimeUnit.HOURS.toMillis(6), periodic.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, periodic.constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, check.constraints.requiredNetworkType)
        assertNull(check.periodicityInfo)
        updater.onForeground()
        updater.requestCheck(manual = true)
        assertEquals(periodic.id, infos(UpdateManager.PERIODIC_WORK).single().id)
        assertEquals(check.id, infos(UpdateManager.CHECK_WORK).single().id)
        assertEquals(0, workersCreated.get())
        assertEquals(0, httpCalls.get())
    }

    @Test fun downloadWorkIsUniqueAndUsesUnmeteredOrConnectedConstraintFromPreference() = runBlocking {
        updater.enqueueDownload(manual = true)
        val wifi = infos(UpdateManager.DOWNLOAD_WORK).single()
        assertEquals(WorkInfo.State.ENQUEUED, wifi.state)
        assertEquals(NetworkType.UNMETERED, wifi.constraints.requiredNetworkType)
        assertEquals(UpdatePhase.WAITING_FOR_WIFI, state.snapshot().phase)
        updater.enqueueDownload(manual = true)
        assertEquals(wifi.id, infos(UpdateManager.DOWNLOAD_WORK).single().id)
        updater.cancelDownload(markCancelled = false)
        assertEquals(WorkInfo.State.CANCELLED, infos(UpdateManager.DOWNLOAD_WORK).single().state)
        state.change { it.copy(wifiOnly = false) }
        updater.enqueueDownload(manual = true)
        val connected = infos(UpdateManager.DOWNLOAD_WORK).single { !it.state.isFinished }
        assertNotEquals(wifi.id, connected.id)
        assertEquals(NetworkType.CONNECTED, connected.constraints.requiredNetworkType)
        assertEquals(UpdatePhase.DOWNLOADING, state.snapshot().phase)
        assertEquals(0, workersCreated.get())
        assertEquals(0, httpCalls.get())
    }

    @Test fun cancellationStopsUniqueWorkAndPersistsGenerationAndVersionUntilExplicitRetry() = runBlocking {
        updater.enqueueDownload(manual = true)
        val scheduled = infos(UpdateManager.DOWNLOAD_WORK).single()
        val before = state.snapshot()
        updater.cancelDownload()
        val cancelled = state.snapshot()
        assertEquals(WorkInfo.State.CANCELLED, infos(UpdateManager.DOWNLOAD_WORK).single().state)
        assertEquals(UpdatePhase.CANCELLED, cancelled.phase)
        assertEquals(before.generation + 1, cancelled.generation)
        assertEquals(before.available!!.metadata.versionCode, cancelled.cancelledVersion)
        assertEquals(before.available, cancelled.available)
        assertEquals(-1, cancelled.sessionId)
        assertNull(cancelled.pendingActionUri)
        updater.enqueueDownload(manual = false)
        assertEquals(scheduled.id, infos(UpdateManager.DOWNLOAD_WORK).single().id)
        assertEquals(WorkInfo.State.CANCELLED, infos(UpdateManager.DOWNLOAD_WORK).single().state)
        updater.enqueueDownload(manual = true)
        val retried = infos(UpdateManager.DOWNLOAD_WORK).single { !it.state.isFinished }
        assertNotEquals(scheduled.id, retried.id)
        assertEquals(0L, state.snapshot().cancelledVersion)
        assertEquals(0, workersCreated.get())
        assertEquals(0, httpCalls.get())
    }
}
