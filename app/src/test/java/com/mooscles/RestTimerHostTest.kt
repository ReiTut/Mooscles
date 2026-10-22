package com.mooscles

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

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
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings", substring = false).assertExists()
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause rest timer").assertDoesNotExist()
    }

    @Test fun activeSessionHasOneFixedTimerAboveFooterAcrossScrollingAndRoutes() {
        compose.setContent { MoosclesApp(UiState(loading = false, active = workout,
            timer = TimerUi("Bench press", 90, 120, running = true)), actions) }
        compose.onAllNodesWithTag("active-rest-timer").assertCountEquals(1)
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.onNodeWithText("Workout", substring = false).performClick()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Discard workout"))
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        val timer = compose.onNodeWithTag("active-rest-timer").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithText("Train", substring = false).fetchSemanticsNode().boundsInRoot
        assertTrue(timer.bottom <= footer.top)
        compose.onNodeWithText("History", substring = false).performClick()
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onAllNodesWithTag("active-rest-timer").assertCountEquals(1)
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
    }

    @Test fun finishingOrDiscardingSessionRemovesDockWithoutWaitingForTimerFlow() {
        val state = mutableStateOf(UiState(loading = false, active = workout,
            timer = TimerUi("Bench press", 90, 120, running = true)))
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pause rest timer").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(active = workout) }
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
    }
}
