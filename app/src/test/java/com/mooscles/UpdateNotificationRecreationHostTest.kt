package com.mooscles

import android.Manifest
import android.content.Intent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mooscles.updates.UpdateNotifications
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Review I2, with the real Activity: opened from the update notification, the user goes on to
 * the licenses and rotates the phone. The recreated Activity keeps the notification intent, but
 * must not ask for the updates again, so the licenses stay on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateNotificationRecreationHostTest {
    private var activityApplication: MoosclesApplication? = null

    // Same environment as UiFlowHostTest: a fresh database and the process-wide factory reset.
    @get:Rule(order = 0) val environment = object : ExternalResource() {
        override fun before() {
            resetApplicationFactory()
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.deleteDatabase("mooscles.db")
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun after() {
            val app = activityApplication ?: ApplicationProvider.getApplicationContext<MoosclesApplication>()
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

    @Test
    fun recreationAfterTheNotificationKeepsTheUserWhereTheyWent() {
        val context = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val intent = Intent(context, MainActivity::class.java).setAction(UpdateNotifications.OPEN_UPDATES)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activityApplication = it.application as MoosclesApplication }
            // The notification opens Settings at the updates, once the data has loaded.
            compose.waitUntil(30_000) {
                compose.onAllNodes(hasText("App updates")).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
                        .fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("app-updates").assertIsDisplayed()
            compose.onNodeWithTag("open-licenses").performScrollTo().performClick()
            compose.onNode(hasText("Open-source licenses") and isHeading()).assertIsDisplayed()

            scenario.recreate()
            compose.waitForIdle()
            compose.onNode(hasText("Open-source licenses") and isHeading()).assertIsDisplayed()
            compose.onNodeWithTag("app-updates").assertDoesNotExist()
        }
    }
}
