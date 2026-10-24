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

/** Navigation tests use the real app graph and an explicitly acknowledged discard. */
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
            "clearError" -> { state.value = state.value.copy(error = null); null }
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
        // Regression: saving the Workout tail through a different tab used to
        // let Train restore that tail while trying to return to the root.
        tab("workout").performClick()
        compose.onNodeWithTag("active-workout-list").assertExists()
        tab("library").performClick()
        tab("routines").performClick()
        assertTrain()
        repeat(2) { cycle ->
            if (cycle > 0) compose.runOnIdle { state.value = state.value.copy(active = workout("second")) }
            tab("workout").performClick()
            confirmDiscard()
            assertTrain()
            compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
            compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
            listOf("history", "records", "library", "workout", "routines").forEach { route ->
                tab(route).performClick()
                tab(route).assertIsSelected()
            }
            assertTrain()
        }
        compose.runOnIdle { assertEquals(2, discardCalls) }
        restoration.emulateSavedInstanceStateRestore()
        assertTrain()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        // A process restart resets the ViewModel sequence, while Compose may
        // retain its saved marker. Its first subsequent discard must still work.
        compose.runOnIdle { state.value = state.value.copy(discardNavigationRequest = 0, active = workout("after-restart")) }
        tab("workout").performClick()
        confirmDiscard()
        assertTrain()
        compose.runOnIdle { assertEquals(3, discardCalls) }
        tab("library").performClick()
        tab("library").assertIsSelected()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertTrain()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertTrue("Train must be the root after discard, without a saved Workout back entry", compose.activity.isFinishing)
        }
    }

    @Test fun failedDiscardClosesConfirmationReleasesTheButtonAndKeepsNavigationResponsive() {
        completeDiscardImmediately = false
        compose.setContent { MoosclesApp(state.value, actions) }
        tab("workout").performClick()
        confirmDiscard()
        compose.onNodeWithText("Discard this workout?").assertDoesNotExist()
        compose.onNodeWithTag("discard-workout").assertIsNotEnabled()
        compose.onNodeWithText("Discarding workout…").assertExists()
        compose.runOnIdle { state.value = state.value.copy(discardingWorkout = false, error = "Could not discard this workout") }
        compose.onNodeWithText("Could not discard this workout").assertExists()
        tab("workout").assertIsSelected()
        compose.onNodeWithTag("discard-workout").assertIsEnabled()
        compose.onNodeWithTag("active-workout-list").assertExists()
        tab("routines").performClick()
        assertTrain()
        tab("workout").performClick()
        compose.onNodeWithTag("active-workout-list").assertExists()
    }

    @Test fun loadingDoesNotAllowFooterActionsBeforeNavigationIsReady() {
        state.value = state.value.copy(loading = true)
        compose.setContent { MoosclesApp(state.value, actions) }
        tab("routines").assertIsNotEnabled()
        tab("workout").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(loading = false) }
        tab("workout").assertIsEnabled().performClick()
        compose.onNodeWithTag("active-workout-list").assertExists()
        tab("routines").performClick()
        assertTrain()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")
    private fun assertTrain() {
        tab("routines").assertIsSelected()
        tab("workout").assertIsNotSelected()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }
    private fun confirmDiscard() {
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("discard-workout"))
        compose.onNodeWithTag("discard-workout").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
    }
    private fun workout(id: String) = WorkoutUi(id, "Workout $id", System.currentTimeMillis())
}
