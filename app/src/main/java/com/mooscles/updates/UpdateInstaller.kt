package com.mooscles.updates

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

sealed interface InstallOutcome {
    data class ApprovalRequired(val intentUri: String) : InstallOutcome
    data object Succeeded : InstallOutcome
    data object Cancelled : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
}

object InstallOutcomePolicy {
    fun fromStatus(status: Int, message: String?, approvalUri: String?): InstallOutcome = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> if (approvalUri != null) InstallOutcome.ApprovalRequired(approvalUri)
            else InstallOutcome.Failed("Android requested approval without a usable installation confirmation. Retry the update.")
        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Succeeded
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Failed("Android blocked this update. Check the install permission or device policy, then retry.")
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallOutcome.Failed("The installed app conflicts with this APK. A compatible signing key is required.")
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed("This APK is incompatible with this device.")
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failed("Android could not verify this APK. Download the update again.")
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failed("There is not enough storage to install the update.")
        else -> InstallOutcome.Failed(message?.take(500)?.takeIf(String::isNotBlank) ?: "Android could not install this update. You can retry it in Settings.")
    }
}

/** The caller owns persistent session state and the workout/timer installation guard. */
class UpdateInstaller(context: Context) {
    private val context = context.applicationContext
    private val installer get() = context.packageManager.packageInstaller

    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun sourcePermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /** Reverify immediately before staging. Persist the returned ID BEFORE calling commit. */
    fun prepare(file: File, metadata: UpdateMetadata): Int {
        val verified = ApkVerifier(context).verify(file, metadata)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(verified.identity.applicationId)
            setAppLabel("Mooscles")
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) {
                // Android decides whether this self-update qualifies. Always handle approval.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().buffered().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
            }
            return sessionId
        } catch (error: Exception) {
            abandon(sessionId)
            throw error
        }
    }

    fun commit(sessionId: Int) {
        require(sessionExists(sessionId)) { "The update installation session no longer exists. Please retry." }
        val result = Intent(context, InstallStatusReceiver::class.java).apply {
            action = ACTION_INSTALL_RESULT
            // A unique, immutable routing key is distinct from PackageInstaller's fill-in extras.
            data = Uri.parse("mooscles-update://install/$sessionId")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, sessionId, result, flags)
        installer.openSession(sessionId).use { it.commit(callback.intentSender) }
    }

    fun sessionExists(sessionId: Int): Boolean = sessionId >= 0 &&
        runCatching { installer.mySessions.any { it.sessionId == sessionId } }.getOrDefault(false)

    /** API 26+ exposes sealing, allowing recovery from a process death before commit. */
    fun sessionSealed(sessionId: Int): Boolean? = runCatching {
        installer.mySessions.firstOrNull { it.sessionId == sessionId }?.isSealed
    }.getOrNull()

    /** Invoke under the installation gate on recovery, including before the first saved session ID. */
    fun abandonUnsealedSessionsExcept(keepSessionId: Int = -1): Int {
        val sessions = runCatching { installer.mySessions }.getOrDefault(emptyList())
        return sessions.count { session ->
            session.sessionId != keepSessionId && !session.isSealed && session.appPackageName == context.packageName &&
                runCatching { installer.abandonSession(session.sessionId) }.isSuccess
        }
    }

    fun abandon(sessionId: Int) {
        if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
    }

    /** Never call automatically: this is exclusively for a foreground user install action. */
    fun pendingIntentForApproval(uri: String): Intent? = runCatching {
        validateApprovalIntent(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME), expectedSessionId = null)
    }.getOrNull()

    fun approvalIntentUri(intent: Intent, expectedSessionId: Int): String? =
        validateApprovalIntent(intent, expectedSessionId)?.toUri(Intent.URI_INTENT_SCHEME)

    private fun validateApprovalIntent(original: Intent, expectedSessionId: Int?): Intent? {
        if (original.action != ACTION_CONFIRM_INSTALL || original.selector != null) return null
        val sessionId = original.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId < 0 || expectedSessionId != null && sessionId != expectedSessionId || !sessionExists(sessionId)) return null
        @Suppress("DEPRECATION")
        val resolved = context.packageManager.resolveActivity(original, 0)?.activityInfo ?: return null
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        if (resolved.applicationInfo.flags and systemFlags == 0) return null
        return Intent(original).apply {
            component = ComponentName(resolved.packageName, resolved.name)
            selector = null
            clipData = null
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.mooscles.updates.INSTALL_RESULT"
        private const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
