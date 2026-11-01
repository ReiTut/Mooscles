package com.mooscles.updates

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.mooscles.BuildConfig
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

/** The side-by-side `preview` build must never check, download or install an update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdaterDisabledHostTest {
    private lateinit var context: Context
    private lateinit var work: WorkManager
    private lateinit var temporary: File
    private lateinit var state: UpdateStore
    private val job = SupervisorJob()
    private val httpCalls = AtomicInteger()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        temporary = Files.createTempDirectory("mooscles-updater-off-").toFile()
        state = UpdateStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { File(temporary, "updates.preferences_pb") },
        ))
        val executor = SynchronousExecutor()
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(executor).setTaskExecutor(executor).build())
        work = WorkManager.getInstance(context)
    }

    @After fun cleanup() = runBlocking {
        work.cancelAllWork().result.get(5, TimeUnit.SECONDS)
        WorkManagerTestInitHelper.closeWorkDatabase()
        job.cancelAndJoin()
        temporary.deleteRecursively()
        Unit
    }

    private fun disabled() = UpdateManager(context, Mutex(), { false }, state,
        ReleaseClient(HttpTransport { _, _ -> httpCalls.incrementAndGet(); throw AssertionError("No request when disabled") }),
        enabled = false) { 10_000_000L }

    @Test fun disabledSchedulesNoWorkAndChecksNothing() = runBlocking {
        val updater = disabled()
        updater.onForeground()
        updater.requestCheck(true)
        updater.enqueueDownload(true)
        assertFalse(updater.check(true))
        assertFalse(updater.download(0, 0))
        assertNull(updater.requestInstall())
        listOf(UpdateManager.PERIODIC_WORK, UpdateManager.CHECK_WORK, UpdateManager.DOWNLOAD_WORK).forEach { name ->
            assertTrue(name, work.getWorkInfosForUniqueWork(name).get(5, TimeUnit.SECONDS).isEmpty())
        }
        assertEquals(0, httpCalls.get())
    }

    @Test fun debugKeepsTheUpdaterOn() {
        assertTrue(BuildConfig.UPDATER_ENABLED)
    }
}
