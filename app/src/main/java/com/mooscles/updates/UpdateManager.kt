package com.mooscles.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.*
import com.mooscles.BuildConfig
import com.mooscles.ui.UpdatePhase
import com.mooscles.ui.UpdateUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Coordinates durable work. Only installation takes the same gate as new training activity. */
class UpdateManager(
    private val context: Context,
    private val trainingGate: Mutex,
    private val trainingBusy: suspend () -> Boolean,
    val store: UpdateStore = UpdateStore(context.updateStore),
    private val client: ReleaseClient = ReleaseClient(),
    /** False in the side-by-side `preview` build: it never checks, downloads or installs anything. */
    private val enabled: Boolean = BuildConfig.UPDATER_ENABLED,
    private val now: () -> Long = System::currentTimeMillis,
) : UpdateCoordinator {
    private val checkGate = Mutex()
    private val installGate = Mutex()
    private val downloadGate = Mutex()
    private val installer = UpdateInstaller(context)
    private val notifications = UpdateNotifications(context)
    private val work get() = WorkManager.getInstance(context)
    private val directory get() = File(context.filesDir, "updates").also { it.mkdirs() }
    private fun apkFile(code: Long) = File(directory, "update-$code.apk")

    override val ui = store.flow.map { s ->
        val installed = installedVersion(context)
        UpdateUi(installed.second, installed.first, s.checkedAt.takeIf { it > 0 },
            s.automatic, s.wifiOnly, s.prereleases, s.phase,
            s.available?.metadata?.versionName, s.available?.metadata?.versionCode,
            s.available?.releaseNotes.orEmpty(), s.downloadedBytes, s.available?.metadata?.apkSize ?: 0, s.message,
            verifiedApkReady = s.available?.metadata?.let { apkFile(it.versionCode).let { file -> file.isFile && file.length() == it.apkSize } } ?: false)
    }.flowOn(Dispatchers.IO)

    suspend fun onForeground() = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        try {
            reconcile()
            schedulePeriodic()
            val state = store.snapshot()
            if (state.sourcePermissionPending) {
                store.change { it.copy(sourcePermissionPending = false) }
                if (installer.canRequestInstalls()) attemptInstall(userInitiated = true)
                else store.change { it.copy(phase = UpdatePhase.PERMISSION_REQUIRED,
                    message = "Install permission was not enabled. You can keep training and allow it later.") }
            } else if (state.phase in setOf(UpdatePhase.READY, UpdatePhase.DEFERRED)) {
                onTrainingChanged()
            }
            val current = store.snapshot()
            if (current.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.WAITING_FOR_WIFI, UpdatePhase.VERIFYING)) enqueueDownload()
            requestCheck(manual = false)
        } catch (e: Exception) { if (e is CancellationException) throw e; report(e) }
    }

    override suspend fun onTrainingChanged() {
        if (!enabled) return
        val state = store.snapshot()
        if (state.phase == UpdatePhase.DEFERRED && !trainingBusy()) {
            store.change { if (it.phase == UpdatePhase.DEFERRED) it.copy(
                phase = if (it.pendingActionUri != null) UpdatePhase.APPROVAL_REQUIRED else UpdatePhase.READY,
                message = if (it.pendingActionUri != null) "Update ready. Android needs your installation approval." else "Update ready. Your workout is saved.") else it }
        }
        if (store.snapshot().phase in setOf(UpdatePhase.READY, UpdatePhase.DEFERRED)) attemptInstall(false)
    }

    override suspend fun assertTrainingMayStart() {
        if (store.snapshot().phase == UpdatePhase.INSTALLING) throw TrainingBlockedException("An update is being installed. Training will be available again shortly.")
    }

    private fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        work.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override suspend fun requestCheck(manual: Boolean) {
        if (!enabled) return
        val s = store.snapshot()
        if (!UpdatePolicy.canCheck(s, manual, now())) {
            if (manual) store.change { it.copy(message = if (now() < it.retryAt)
                "GitHub checks are paused until ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.retryAt))}. Please retry afterward."
                else "An update check just ran. Please wait a moment before checking again.") }
            return
        }
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setInputData(workDataOf("manual" to manual))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        work.enqueueUniqueWork(CHECK_WORK, ExistingWorkPolicy.KEEP, request)
        if (manual) store.change { it.copy(message = "Update check queued. A connection is required; your workouts remain available offline.") }
    }

    /** Foreground, periodic and manual work share one gate and the persisted throttle. */
    suspend fun check(manual: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext false
        if (!checkGate.tryLock()) return@withContext false
        try {
            val old = store.snapshot()
            if (!UpdatePolicy.canCheck(old, manual, now())) return@withContext now() < old.retryAt
            store.change { it.copy(attemptedAt = now(), phase = if (it.available == null) UpdatePhase.CHECKING else it.phase, message = "Checking GitHub Releases…") }
            val installed = installedVersion(context).first
            val result = client.check(context.packageName, installed, Build.VERSION.SDK_INT, old.prereleases, old.cache)
            val current = store.snapshot()
            // A channel change or cancellation during a request must not restore the old selection.
            if (current.prereleases != old.prereleases) return@withContext false
            val candidate = result.available
            val changed = candidate?.metadata?.versionCode != current.available?.metadata?.versionCode ||
                candidate?.metadata?.sha256 != current.available?.metadata?.sha256
            val protected = current.phase in BUSY_PHASES || current.sessionId >= 0
            val saved = store.change { s ->
                if (s.prereleases != old.prereleases) return@change s
                s.copy(checkedAt = now(), checkFailures = 0, retryAt = 0, cache = result.cache,
                    available = if (protected) s.available else candidate,
                    phase = if (protected || !changed && s.available != null) s.phase else if (candidate != null) UpdatePhase.AVAILABLE else UpdatePhase.IDLE,
                    generation = if (changed && !protected) s.generation + 1 else s.generation,
                    downloadedBytes = if (changed && !protected) 0 else s.downloadedBytes,
                    message = when {
                        protected -> s.message
                        candidate != null -> if (!changed && s.phase == UpdatePhase.CANCELLED) s.message else "${candidate.metadata.versionName} is available."
                        result.diagnostics.isNotEmpty() -> "No compatible update found. Some releases lack valid update.json metadata."
                        else -> "You have the latest compatible version in this channel."
                    })
            }
            if (saved.automatic && saved.available != null && saved.cancelledVersion != saved.available.metadata.versionCode &&
                saved.phase == UpdatePhase.AVAILABLE) enqueueDownload()
            false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            store.change { s ->
                val failures = s.checkFailures + 1
                s.copy(checkFailures = failures,
                    retryAt = maxOf((e as? ReleaseCheckException)?.retryAtMillis ?: 0L, now() + UpdatePolicy.retryDelay(failures - 1)),
                    phase = if (s.available == null) UpdatePhase.FAILED else s.phase,
                    message = "Update check could not finish: ${e.message ?: "connection unavailable"}. Training remains available offline.")
            }
            true
        } finally { checkGate.unlock() }
    }

    override suspend fun setAutomatic(enabled: Boolean) {
        store.change { it.copy(automatic = enabled) }
        if (!enabled && store.snapshot().phase in DOWNLOAD_PHASES) cancelDownload(markCancelled = false)
        else if (enabled) {
            val s = store.change { it.copy(cancelledVersion = 0) }
            if (s.available != null && s.phase in setOf(UpdatePhase.AVAILABLE, UpdatePhase.CANCELLED, UpdatePhase.FAILED)) enqueueDownload(manual = true)
            else if (s.phase in setOf(UpdatePhase.READY, UpdatePhase.DEFERRED)) attemptInstall(false)
        }
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        val previous = store.change { it.copy(wifiOnly = enabled) }
        if (previous.phase in DOWNLOAD_PHASES) {
            cancelDownload(markCancelled = false)
            enqueueDownload(manual = true)
        }
    }

    override suspend fun setPrereleases(enabled: Boolean) {
        installGate.withLock {
            if (store.snapshot().phase == UpdatePhase.INSTALLING) return
            cancelDownloadLocked(markCancelled = false)
            store.change { it.copy(prereleases = enabled, available = null, checkedAt = 0, attemptedAt = 0,
                phase = UpdatePhase.IDLE, pendingActionUri = null, sessionId = -1, installAttemptedVersion = 0) }
        }
        requestCheck(true)
    }

    override suspend fun enqueueDownload(manual: Boolean) {
        if (!enabled) return
        val state = store.snapshot()
        val candidate = state.available ?: return
        if (candidate.metadata.versionCode <= installedVersion(context).first) { reconcile(); return }
        if (state.phase in setOf(UpdatePhase.READY, UpdatePhase.DEFERRED, UpdatePhase.APPROVAL_REQUIRED, UpdatePhase.PERMISSION_REQUIRED, UpdatePhase.INSTALLING)) return
        if (!manual && state.cancelledVersion == candidate.metadata.versionCode) return
        val updated = store.change { s ->
            s.copy(phase = if (s.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) s.phase
                else if (s.wifiOnly) UpdatePhase.WAITING_FOR_WIFI else UpdatePhase.DOWNLOADING,
                cancelledVersion = if (manual) 0 else s.cancelledVersion,
                message = if (s.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) s.message
                    else if (s.wifiOnly) "Waiting for unmetered Wi-Fi to download the update." else "Download queued.")
        }
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(workDataOf("generation" to updated.generation))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (updated.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        work.enqueueUniqueWork(DOWNLOAD_WORK, ExistingWorkPolicy.KEEP, request)
    }

    suspend fun download(runAttempt: Int, expectedGeneration: Long): Boolean = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext false
        downloadGate.withLock {
            val initial = store.snapshot()
            currentCoroutineContext().ensureActive()
            if (initial.generation != expectedGeneration || initial.phase !in DOWNLOAD_PHASES) return@withLock false
            val candidate = initial.available ?: return@withLock false
            if (initial.cancelledVersion == candidate.metadata.versionCode) return@withLock false
            if (initial.wifiOnly && !onWifi()) {
                store.change { it.copy(phase = UpdatePhase.WAITING_FOR_WIFI) }
                return@withLock true
            }
            val generation = initial.generation
            val partial = File(directory, "update-${candidate.metadata.versionCode}-$generation.part")
            val destination = apkFile(candidate.metadata.versionCode)
            fun stillCurrent(s: UpdateSnapshot) = s.generation == generation && s.available == candidate && s.cancelledVersion != candidate.metadata.versionCode
            try {
                store.change { if (stillCurrent(it)) it.copy(phase = UpdatePhase.DOWNLOADING, downloadedBytes = 0, message = "Downloading update…") else it }
                ApkDownload().download(candidate, partial) { bytes ->
                    if (!stillCurrent(store.snapshot())) throw CancellationException("Update was cancelled or changed")
                    store.change { if (stillCurrent(it)) it.copy(downloadedBytes = bytes) else it }
                }
                currentCoroutineContext().ensureActive()
                if (!stillCurrent(store.snapshot())) throw CancellationException("Update selection changed")
                store.change { if (stillCurrent(it)) it.copy(phase = UpdatePhase.VERIFYING, message = "Verifying APK integrity and signing identity…") else it }
                ApkVerifier(context).verify(partial, candidate.metadata)
                currentCoroutineContext().ensureActive()
                if (!stillCurrent(store.snapshot())) throw CancellationException("Update selection changed")
                if (destination.exists() && !destination.delete()) throw IOException("Cannot replace the old update file.")
                if (!partial.renameTo(destination)) throw IOException("Cannot save the verified update.")
                val ready = store.change { if (stillCurrent(it)) it.copy(phase = UpdatePhase.READY, downloadedBytes = candidate.metadata.apkSize,
                    message = "Update verified and ready to install.") else it }
                if (stillCurrent(ready) && ready.phase == UpdatePhase.READY) {
                    notifications.ready(candidate.metadata.versionName)
                    attemptInstall(false)
                }
                false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val retry = e is IOException && runAttempt < 4
                store.change { if (stillCurrent(it)) it.copy(phase = if (retry) UpdatePhase.DOWNLOADING else UpdatePhase.FAILED,
                    message = if (retry) "Download paused after a connection problem. It will retry with backoff." else "Update could not be downloaded or verified: ${e.message}") else it }
                retry
            } finally { partial.delete() }
        }
    }

    override suspend fun cancelDownload(markCancelled: Boolean) = installGate.withLock { cancelDownloadLocked(markCancelled) }

    private suspend fun cancelDownloadLocked(markCancelled: Boolean) {
        val old = store.snapshot()
        if (old.phase == UpdatePhase.INSTALLING) return
        withContext(Dispatchers.IO) { work.cancelUniqueWork(DOWNLOAD_WORK).result.get(10, TimeUnit.SECONDS) }
        installer.abandon(old.sessionId)
        store.change { it.copy(generation = it.generation + 1, phase = UpdatePhase.CANCELLED,
            cancelledVersion = if (markCancelled) it.available?.metadata?.versionCode ?: 0 else 0,
            sessionId = -1, pendingActionUri = null, sourcePermissionPending = false,
            installAttemptedVersion = if (markCancelled) it.available?.metadata?.versionCode ?: 0 else it.installAttemptedVersion,
            message = if (markCancelled) "Update cancelled. It will wait for you to retry." else "Automatic download paused.") }
        notifications.clear()
    }

    /** User gesture only. Returns a system Settings/approval activity; never opens it itself. */
    suspend fun requestInstall(): Intent? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        installGate.withLock {
            if (trainingBusy()) { defer(); return@withContext null }
            val s = store.snapshot()
            if (s.available == null || s.phase == UpdatePhase.INSTALLING || s.sourcePermissionPending) return@withContext null
            if (!installer.canRequestInstalls()) {
                store.change { it.copy(phase = UpdatePhase.PERMISSION_REQUIRED, sourcePermissionPending = true,
                    message = "Allow Mooscles to install its updates, then return here. Android may still ask you to confirm each update.") }
                return@withContext installer.sourcePermissionIntent()
            }
            if (s.pendingActionUri != null) {
                val intent = installer.pendingIntentForApproval(s.pendingActionUri)
                if (intent != null) {
                    trainingGate.withLock {
                        if (trainingBusy()) { defer(); return@withContext null }
                        store.change { it.copy(phase = UpdatePhase.INSTALLING, message = "Waiting for Android's installation confirmation.") }
                    }
                    return@withContext intent
                }
                installer.abandon(s.sessionId)
                store.change { it.copy(sessionId = -1, pendingActionUri = null, phase = UpdatePhase.READY) }
            }
        }
        attemptInstall(userInitiated = true)
        null
    }

    suspend fun approvalReturned(cancelled: Boolean) {
        val s = store.snapshot()
        if (cancelled && s.phase == UpdatePhase.INSTALLING && s.pendingActionUri != null) {
            store.change { it.copy(phase = UpdatePhase.APPROVAL_REQUIRED,
                message = "Installation confirmation was closed. Tap Install when you are ready.") }
        }
        onForeground()
    }

    private suspend fun defer() = store.change { it.copy(phase = UpdatePhase.DEFERRED,
        message = "Update ready. Installation will wait until your workout is saved and your rest timer has stopped.") }

    private suspend fun attemptInstall(userInitiated: Boolean) = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext
        if (!installGate.tryLock()) return@withContext
        var preparedSession = -1
        try {
            trainingGate.withLock {
                val s = store.snapshot()
                val candidate = s.available ?: return@withLock
                if (s.phase == UpdatePhase.INSTALLING || s.pendingActionUri != null) return@withLock
                if (!userInitiated && (!s.automatic || s.installAttemptedVersion == candidate.metadata.versionCode || s.cancelledVersion == candidate.metadata.versionCode)) return@withLock
                if (trainingBusy()) { defer(); return@withLock }
                val file = apkFile(candidate.metadata.versionCode)
                if (!file.exists()) {
                    store.change { it.copy(phase = UpdatePhase.FAILED, message = "The verified APK is missing. Download the update again.") }
                    return@withLock
                }
                if (!installer.canRequestInstalls()) {
                    store.change { it.copy(phase = UpdatePhase.PERMISSION_REQUIRED,
                        message = "Update ready. Tap Install to allow updates from Mooscles when Android asks.") }
                    notifications.ready(candidate.metadata.versionName)
                    return@withLock
                }
                store.change { it.copy(phase = UpdatePhase.INSTALLING, installAttemptedVersion = candidate.metadata.versionCode,
                    message = "Installing the verified update…") }
                val session = installer.prepare(file, candidate.metadata)
                preparedSession = session
                store.change { it.copy(sessionId = session) }
                installer.commit(session)
            }
        } catch (e: Exception) {
            withContext(NonCancellable) {
                val s = store.snapshot(); installer.abandon(preparedSession); installer.abandon(s.sessionId)
                store.change { it.copy(phase = UpdatePhase.FAILED, sessionId = -1, message = "Installation could not start: ${e.message}. Retry when ready.") }
            }
            if (e is CancellationException) throw e
        } finally { installGate.unlock() }
    }

    suspend fun onInstallerResult(sessionId: Int, status: Int, message: String?, pendingIntentUri: String?) {
        installGate.withLock {
        val current = store.snapshot()
        if (sessionId != current.sessionId) return
        when (val outcome = InstallOutcomePolicy.fromStatus(status, message, pendingIntentUri)) {
            is InstallOutcome.ApprovalRequired -> {
                val busy = trainingBusy()
                store.change { it.copy(phase = if (busy) UpdatePhase.DEFERRED else UpdatePhase.APPROVAL_REQUIRED,
                    pendingActionUri = outcome.intentUri,
                    message = if (busy) "Update ready. Finish your workout before approving installation." else "Update ready. Android needs your installation approval.") }
                notifications.ready(current.available?.metadata?.versionName.orEmpty())
            }
            InstallOutcome.Succeeded -> {
                store.change { it.copy(phase = UpdatePhase.SUCCEEDED, pendingActionUri = null, sessionId = -1,
                    sourcePermissionPending = false, message = "Update installed successfully.") }
            }
            InstallOutcome.Cancelled -> store.change { it.copy(phase = UpdatePhase.CANCELLED,
                cancelledVersion = it.available?.metadata?.versionCode ?: 0,
                sessionId = -1, pendingActionUri = null, message = "Installation cancelled. Your data is unchanged; retry when ready.") }
            is InstallOutcome.Failed -> store.change { it.copy(phase = UpdatePhase.FAILED,
                sessionId = -1, pendingActionUri = null, message = outcome.message) }
        }
        }
        if (status == PackageInstaller.STATUS_SUCCESS) reconcile()
    }

    /** Invoked on startup and MY_PACKAGE_REPLACED; only updater files/state are cleaned. */
    suspend fun reconcile() = withContext(Dispatchers.IO) { installGate.withLock {
        val installed = installedVersion(context).first
        val s = store.snapshot()
        installer.abandonUnsealedSessionsExcept(s.sessionId)
        val completed = (s.lastInstalledVersion > 0 && installed > s.lastInstalledVersion) ||
            (s.available != null && installed >= s.available.metadata.versionCode)
        if (completed) {
            installer.abandon(s.sessionId)
            store.change { it.copy(phase = UpdatePhase.SUCCEEDED, available = null, downloadedBytes = 0,
                sessionId = -1, pendingActionUri = null, sourcePermissionPending = false, installAttemptedVersion = 0,
                cancelledVersion = 0, lastInstalledVersion = installed, generation = it.generation + 1,
                message = "Updated to ${installedVersion(context).second}. Your workout data is preserved.") }
            directory.listFiles()?.forEach { it.delete() }
            notifications.clear()
        } else {
            store.change { it.copy(lastInstalledVersion = installed,
                phase = if (it.phase == UpdatePhase.CHECKING) if (it.available == null) UpdatePhase.IDLE else UpdatePhase.AVAILABLE else it.phase) }
            if (s.phase == UpdatePhase.INSTALLING && s.sessionId < 0 || s.sessionId >= 0 && installer.sessionSealed(s.sessionId) != true) {
                installer.abandon(s.sessionId)
                store.change { it.copy(sessionId = -1, pendingActionUri = null, phase = UpdatePhase.FAILED,
                    message = "The previous installation did not complete. Tap Install to retry.") }
            }
            val keep = s.available?.metadata?.versionCode
            directory.listFiles()?.filter { it.name != "update-$keep.apk" && !it.name.startsWith("update-$keep-") }?.forEach { it.delete() }
        }
    } }

    private fun onWifi(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private suspend fun report(error: Exception) {
        try { store.change { it.copy(message = "Updater: ${error.message}. Your workouts are unaffected.") } }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            android.util.Log.w("MoosclesUpdater", "Update storage is unavailable", failure)
        }
    }

    companion object {
        const val PERIODIC_WORK = "mooscles-update-periodic"
        const val CHECK_WORK = "mooscles-update-check"
        const val DOWNLOAD_WORK = "mooscles-update-download"
        private val DOWNLOAD_PHASES = setOf(UpdatePhase.DOWNLOADING, UpdatePhase.WAITING_FOR_WIFI, UpdatePhase.VERIFYING)
        private val BUSY_PHASES = DOWNLOAD_PHASES + setOf(UpdatePhase.READY, UpdatePhase.DEFERRED,
            UpdatePhase.PERMISSION_REQUIRED, UpdatePhase.APPROVAL_REQUIRED, UpdatePhase.INSTALLING)
        fun installedVersion(context: Context): Pair<Long, String> {
            @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION") val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            return code to (info.versionName ?: BuildConfig.VERSION_NAME)
        }
    }
}
