package com.mooscles.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.mooscles.MoosclesApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** An explicit, non-exported target; it records results without opening any installation UI. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_INSTALL_RESULT) return
        val routedSession = intent.data?.takeIf { it.scheme == "mooscles-update" && it.host == "install" }
            ?.lastPathSegment?.toIntOrNull() ?: return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, routedSession)
        if (sessionId != routedSession) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(500)
        val approval = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        val approvalUri = approval?.let { runCatching { UpdateInstaller(context).approvalIntentUri(it, sessionId) }.getOrNull() }
        val app = context.applicationContext as MoosclesApplication
        val pending = goAsync()
        app.appScope.launch(Dispatchers.IO) {
            try {
                app.updates.onInstallerResult(sessionId, status, message, approvalUri)
            } catch (error: Exception) {
                // A storage failure must not terminate an otherwise usable offline workout app.
                // The manager reconciles persisted sessions against Android on its next foreground.
                Log.w("MoosclesUpdater", "Could not persist installation status", error)
            } finally {
                pending.finish()
            }
        }
    }
}
