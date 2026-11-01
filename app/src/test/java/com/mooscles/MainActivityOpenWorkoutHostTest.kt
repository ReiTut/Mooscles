package com.mooscles

import android.Manifest
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mooscles.timer.RestNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Decision 25, with the real Activity: a tap on a rest notification opens the workout once, on a
 * fresh launch or a new intent, and never again when the Activity is recreated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityOpenWorkoutHostTest {
    private var activityApplication: MoosclesApplication? = null

    // Same environment as UpdateNotificationRecreationHostTest, with a workout in progress.
    @get:Rule(order = 0) val environment = object : ExternalResource() {
        override fun before() {
            resetApplicationFactory()
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.deleteDatabase("mooscles.db")
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
            runBlocking(Dispatchers.IO) { app.repository.startEmpty("Push day") }
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

    private val context get() = ApplicationProvider.getApplicationContext<MoosclesApplication>()
    private fun openWorkoutIntent() = Intent(context, MainActivity::class.java).setAction(RestNotifications.OPEN_WORKOUT)
    private fun awaitTag(tag: String) {
        compose.waitUntil(30_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    @Test fun freshLaunchWithOpenWorkoutOpensIt() {
        ActivityScenario.launch<MainActivity>(openWorkoutIntent()).use { scenario ->
            scenario.onActivity { activityApplication = it.application as MoosclesApplication }
            awaitTag("workout-route")
        }
    }

    @Test fun recreationDoesNotRepeat() {
        ActivityScenario.launch<MainActivity>(openWorkoutIntent()).use { scenario ->
            scenario.onActivity { activityApplication = it.application as MoosclesApplication }
            awaitTag("workout-route")
            compose.onNodeWithTag("workout-minimize").performClick()
            awaitTag("routines-list")

            // The recreated Activity keeps the notification's intent, but must not open the workout again.
            scenario.recreate()
            compose.waitForIdle()
            awaitTag("routines-list")
            compose.onNodeWithTag("workout-route").assertDoesNotExist()
        }
    }

    @Test fun onNewIntentOpensIt() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activityApplication = it.application as MoosclesApplication }
            awaitTag("routines-list")
            compose.onNodeWithTag("workout-route").assertDoesNotExist()
            // A tap while the app is open delivers the intent to the Activity at the top (single top).
            scenario.onActivity { activity ->
                val launch = activity.intent
                MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
                    .apply { isAccessible = true }.invoke(activity, openWorkoutIntent())
                // ActivityScenario finds its Activity by the launch intent, which onNewIntent replaced
                // (setIntent): give it back, or closing the scenario waits forever.
                activity.intent = launch
            }
            awaitTag("workout-route")
        }
    }

    @Test fun aNewProcessNeverRepeatsAnEarlierRequest() {
        // MoosclesApp remembers the last request it handled in saved state; a restored value must
        // never swallow the first request of a new process, so values do not restart from 1.
        val first = AppViewModel(context)
        val second = AppViewModel(context)
        try {
            assertEquals(0L, first.workoutRequest.value)
            first.openWorkout()
            val earlier = first.workoutRequest.value
            assertNotEquals(0L, earlier)
            first.openWorkout()
            assertTrue(first.workoutRequest.value > earlier)
            Thread.sleep(5)
            second.openWorkout()
            assertTrue(second.workoutRequest.value > first.workoutRequest.value)
        } finally {
            first.viewModelScope.cancel()
            second.viewModelScope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
