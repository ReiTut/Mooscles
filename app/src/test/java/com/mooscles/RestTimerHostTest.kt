package com.mooscles

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.designsystem.components.WorkoutBarRestTag
import com.mooscles.designsystem.components.WorkoutBarSkipTag
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The rest timer in the shell (Task 24): the workout bar above the tabs carries the rest on every
 * route with tabs, and the workout's own timer on the workout route; neither without a session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RestTimerHostTest {
    @get:Rule val compose = createComposeRule()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Timer host test actions"
            else -> null
        }
    } as MoosclesActions
    private val workout = WorkoutUi("active", "Active training", System.currentTimeMillis(),
        exercises = List(8) { index -> WorkoutExerciseUi("entry-$index",
            ExerciseUi("exercise-$index", "Movement $index", "Back", "Barbell"),
            sets = List(2) { SetUi("set-$index-$it", reps = 8, weightKg = 20.0) }) })

    @Test fun noPersistedSessionHidesAllTimerControlsIncludingSettingsDespiteStaleTimer() {
        compose.setContent { MoosclesApp(UiState(loading = false,
            timer = TimerUi("Stale squat", 90, 120, running = true)), actions) }
        assertNoTimer()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-content").assertExists()
        assertNoTimer()
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
    }

    @Test fun activeSessionHasOneFixedTimerAboveFooterAcrossScrollingAndRoutes() {
        compose.setContent { MoosclesApp(UiState(loading = false, active = workout,
            timer = TimerUi("Bench press", 90, 120, running = true)), actions) }
        assertOneBarWithTheRestAboveTheTabs()
        // In the workout, the screen's own timer takes over: still one, through the whole list.
        compose.onNodeWithTag("shell-workout-bar").performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        compose.onAllNodesWithTag("workout-timer-layer").assertCountEquals(1)
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("workout-end"))
        compose.onNodeWithTag("workout-timer-layer").assertIsDisplayed()
        val dock = compose.onNodeWithTag("workout-timer-layer").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("workout-list").fetchSemanticsNode().boundsInRoot
        assertTrue("The list ends above the timer: $list vs $dock", list.bottom <= dock.top + 1f)
        compose.onNodeWithTag("workout-minimize").performClick()
        compose.onNodeWithTag("tab-history").performClick()
        assertOneBarWithTheRestAboveTheTabs()
        compose.onNodeWithContentDescription("Settings").performClick()
        assertOneBarWithTheRestAboveTheTabs()
    }

    @Test fun finishingOrDiscardingSessionRemovesDockWithoutWaitingForTimerFlow() {
        val state = mutableStateOf(UiState(loading = false, active = workout,
            timer = TimerUi("Bench press", 90, 120, running = true)))
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        assertNoTimer()
        compose.runOnIdle { state.value = state.value.copy(active = workout) }
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutBarSkipTag, useUnmergedTree = true).assertExists()
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        assertNoTimer()
    }

    private fun assertNoTimer() {
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        compose.onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutBarSkipTag, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("workout-timer-layer").assertDoesNotExist()
    }

    private fun assertOneBarWithTheRestAboveTheTabs() {
        compose.onAllNodesWithTag("shell-workout-bar").assertCountEquals(1)
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertExists()
        val bar = compose.onNodeWithTag("shell-workout-bar").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag(MTabBarTag).fetchSemanticsNode().boundsInRoot
        assertEquals(tabs.top, bar.bottom, 1f)
    }
}
