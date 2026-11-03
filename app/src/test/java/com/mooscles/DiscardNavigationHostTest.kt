package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Navigation tests use the real app graph and an explicitly acknowledged discard. The workout is
 * opened from the shell's workout bar and discarded from its header menu (Task 24).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class DiscardNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(UiState(loading = false, active = workout("first")))
    private var discardCalls = 0
    private var completeDiscardImmediately = true
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Discard navigation actions"
            "clearError" -> { state.value = state.value.copy(error = null, errorCode = null); null }
            "discardWorkout" -> {
                discardCalls++
                state.value = if (completeDiscardImmediately) state.value.copy(active = null,
                    discardingWorkout = false, discardNavigationRequest = state.value.discardNavigationRequest + 1)
                else state.value.copy(discardingWorkout = true)
                null
            }
            else -> null
        }
    } as MoosclesActions

    @Test fun trainSelectsTheActualRootAndRepeatedDiscardsClearTheBackStackAndSavedTail() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions) }
        // Regression: a workout opened from another tab must not come back with that tab's
        // saved tail, nor be restored by Train while it returns to its root.
        tab("library").performClick()
        openWorkout()
        compose.onNodeWithTag("workout-minimize").performClick()
        tab("history").performClick()
        tab("routines").performClick()
        assertTrain()
        repeat(2) { cycle ->
            if (cycle > 0) compose.runOnIdle { state.value = state.value.copy(active = workout("second")) }
            tab(if (cycle == 0) "library" else "records").performClick()
            openWorkout()
            confirmDiscard()
            assertTrain()
            compose.onNodeWithTag("workout-route").assertDoesNotExist()
            compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
            listOf("history", "records", "library", "routines").forEach { route ->
                tab(route).performClick()
                tab(route).assertIsSelected()
                compose.onNodeWithTag("workout-route").assertDoesNotExist()
            }
            assertTrain()
        }
        compose.runOnIdle { assertEquals(2, discardCalls) }
        restoration.emulateSavedInstanceStateRestore()
        assertTrain()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        // A process restart resets the ViewModel sequence, while Compose may
        // retain its saved marker. Its first subsequent discard must still work.
        compose.runOnIdle { state.value = state.value.copy(discardNavigationRequest = 0, active = workout("after-restart")) }
        openWorkout()
        confirmDiscard()
        assertTrain()
        compose.runOnIdle { assertEquals(3, discardCalls) }
        tab("library").performClick()
        tab("library").assertIsSelected()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrain()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertTrue("Train must be the root after discard, without a saved workout back entry", compose.activity.isFinishing)
        }
    }

    @Test fun failedDiscardClosesConfirmationReleasesTheButtonAndKeepsNavigationResponsive() {
        completeDiscardImmediately = false
        compose.setContent { MoosclesApp(state.value, actions) }
        openWorkout()
        confirmDiscard()
        compose.onNodeWithText("Discard workout?").assertDoesNotExist()
        compose.onNodeWithTag("workout-more").assertIsNotEnabled()
        compose.onNodeWithTag("workout-header-finish").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(discardingWorkout = false, error = "Could not discard this workout", errorCode = UiError.SAVE_FAILED) }
        // The workout shows its own words for the error, never the raw text.
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.onNodeWithText("Could not discard this workout").assertDoesNotExist()
        compose.onNodeWithTag("workout-more").assertIsEnabled()
        compose.onNodeWithTag("workout-route").assertExists()
        compose.onNodeWithTag("workout-minimize").performClick()
        assertTrain()
        openWorkout()
        compose.onNodeWithTag("workout-route").assertExists()
    }

    @Test fun loadingDoesNotAllowFooterActionsBeforeNavigationIsReady() {
        state.value = state.value.copy(loading = true)
        compose.setContent { MoosclesApp(state.value, actions) }
        listOf("routines", "library", "history", "records").forEach { tab(it).assertIsNotEnabled() }
        compose.runOnIdle { state.value = state.value.copy(loading = false) }
        tab("library").assertIsEnabled().performClick()
        tab("library").assertIsSelected()
        openWorkout()
        compose.onNodeWithTag("workout-minimize").performClick()
        tab("routines").performClick()
        assertTrain()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")
    private fun assertTrain() {
        tab("routines").assertIsSelected()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }
    private fun openWorkout() {
        compose.onNodeWithTag("shell-workout-bar").performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
    }
    private fun confirmDiscard() {
        compose.onNodeWithTag("workout-more").performClick()
        compose.onNodeWithTag("workout-menu-discard").performClick()
        compose.onNodeWithTag("dialog-confirm-danger").performClick()
    }
    private fun workout(id: String) = WorkoutUi(id, "Workout $id", System.currentTimeMillis())
}
