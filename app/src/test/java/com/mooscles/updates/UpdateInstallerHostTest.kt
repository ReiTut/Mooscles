package com.mooscles.updates

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateInstallerHostTest {
    private lateinit var context: Context
    private lateinit var engine: UpdateInstaller
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        engine = UpdateInstaller(context)
    }

    @Test fun permissionRequestOnlyTargetsThisAppsUnknownSourceSettings() {
        val intent = engine.sourcePermissionIntent()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
    }

    @Test fun sessionLifecycleAllowsAStagedSessionToBePersistedAndAbandoned() {
        val session = createSession()
        assertTrue(engine.sessionExists(session))
        assertEquals(false, engine.sessionSealed(session))
        engine.abandon(session)
        assertFalse(engine.sessionExists(session))
        assertNull(engine.sessionSealed(session))
        engine.abandon(session)
    }

    @Test fun onlySystemApprovalForTheExpectedExistingSessionCanBePersisted() {
        val session = createSession()
        val intent = approval(session)
        resolveTo(intent, system = true)
        val uri = engine.approvalIntentUri(intent, session)
        assertNotNull(uri)
        val parsed = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        assertEquals("com.android.packageinstaller", parsed.component?.packageName)
        assertEquals(session, parsed.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, parsed.flags)
        assertNull(engine.approvalIntentUri(intent, session + 1))
        engine.abandon(session)
        assertNull(engine.approvalIntentUri(intent, session))
    }

    @Test fun thirdPartyAndUnrelatedIntentsCannotBeLaunchedAsInstallerApproval() {
        val session = createSession()
        val intent = approval(session)
        resolveTo(intent, system = false)
        assertNull(engine.approvalIntentUri(intent, session))
        assertNull(engine.pendingIntentForApproval(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")).toUri(Intent.URI_INTENT_SCHEME)))
        assertNull(engine.pendingIntentForApproval("not an installation intent"))
    }

    @Test fun recoveryAbandonsOnlyOrphanedUnsealedSelfUpdateSessions() {
        val installer = context.packageManager.packageInstaller
        fun create(packageName: String) = installer.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(packageName) },
        )
        val keep = create(context.packageName)
        val orphan = create(context.packageName)
        val unrelated = create("other.application")
        assertEquals(1, engine.abandonUnsealedSessionsExcept(keep))
        assertTrue(engine.sessionExists(keep))
        assertFalse(engine.sessionExists(orphan))
        assertTrue(engine.sessionExists(unrelated))
    }

    private fun createSession(): Int = context.packageManager.packageInstaller.createSession(
        PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL),
    )

    private fun approval(session: Int) = Intent("android.content.pm.action.CONFIRM_INSTALL")
        .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun resolveTo(intent: Intent, system: Boolean) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                name = "com.android.packageinstaller.PackageInstallerActivity"
                packageName = "com.android.packageinstaller"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "com.android.packageinstaller"
                    flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }
}
