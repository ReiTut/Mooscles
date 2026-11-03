package com.mooscles

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The system bars follow the app's theme, not the system's night mode, also after a configuration
 * change the Activity survives without being recreated (a 90° to 270° rotation, for one). With the
 * app in dark and the system in light, `enableEdgeToEdge` used to re-apply the system's light bars
 * on such a change: dark status icons over the dark app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class SystemBarsThemeHostTest {
    private var activityApplication: MoosclesApplication? = null

    // Same environment as MainActivityOpenWorkoutHostTest, with the app theme set to dark.
    @get:Rule(order = 0) val environment = object : ExternalResource() {
        override fun before() {
            resetApplicationFactory()
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.deleteDatabase("mooscles.db")
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            runBlocking(Dispatchers.IO) { app.preferences.theme("dark") }
        }
        override fun after() {
            val app = activityApplication ?: ApplicationProvider.getApplicationContext<MoosclesApplication>()
            runBlocking(Dispatchers.IO) { app.preferences.theme("system") }
            app.appScope.cancel()
            app.database.close()
            resetApplicationFactory()
        }
    }
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    private fun resetApplicationFactory() {
        ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("_instance")
            .apply { isAccessible = true }.set(null, null)
    }

    @Test fun darkAppKeepsDarkBarsAfterAConfigurationChangeWithoutRecreation() {
        val context = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activityApplication = it.application as MoosclesApplication }
            compose.waitUntil(30_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasTestTag("routines-list")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                assertFalse("the system must be in light mode for this test", night == Configuration.UI_MODE_NIGHT_YES)
                val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertFalse("status bar icons light on launch", bars.isAppearanceLightStatusBars)
                assertFalse("navigation bar light on launch", bars.isAppearanceLightNavigationBars)

                // What a 90° to 270° rotation delivers: a new configuration to the views, no recreation.
                activity.window.decorView.dispatchConfigurationChanged(Configuration(activity.resources.configuration))
            }
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertFalse("status bar icons light after the change", bars.isAppearanceLightStatusBars)
                assertFalse("navigation bar light after the change", bars.isAppearanceLightNavigationBars)
            }
        }
    }
}
