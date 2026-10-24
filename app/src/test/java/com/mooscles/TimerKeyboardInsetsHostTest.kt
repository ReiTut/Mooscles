package com.mooscles

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class TimerKeyboardInsetsHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imeMovesTheWholeTimerOnceWhileFooterAndCountdownStayUnchanged() {
        val imeBottom = mutableIntStateOf(0)
        val calls = mutableListOf<String>()
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
            arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Timer inset test actions"
                else -> { calls += method.name; null }
            }
        } as MoosclesActions
        val timer = TimerUi("Bench press", 90, 120, running = true, workoutExerciseId = "bench-entry")
        val state = UiState(loading = false, active = WorkoutUi("active", "Workout", 1_000), timer = timer)
        compose.setContent {
            MaterialTheme {
                val ime = WindowInsets(bottom = imeBottom.intValue)
                Scaffold(modifier = Modifier.fillMaxSize().testTag("window"), contentWindowInsets = WindowInsets(0),
                    bottomBar = { WorkoutBottomBar(imeInsets = ime,
                        timer = { WorkoutTimerDock(state, actions) },
                        footer = { Box(Modifier.fillMaxWidth().height(80.dp).testTag("footer")) }) }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)
                        .windowInsetsPadding(ime).fillMaxSize().testTag("content"))
                }
            }
        }
        val closedTimer = bounds("active-rest-timer")
        val closedFooter = bounds("footer")
        val window = bounds("window")
        assertEquals(closedFooter.top, closedTimer.bottom, 1f)
        compose.onNodeWithText("1:30 min").assertExists()
        compose.runOnIdle { imeBottom.intValue = 350 }
        val openedTimer = bounds("active-rest-timer")
        val openedFooter = bounds("footer")
        assertEquals("Footer keeps its existing position behind the keyboard", closedFooter, openedFooter)
        assertEquals("The whole timer is above the actual IME edge", window.bottom - 350, openedTimer.bottom, 1f)
        assertEquals(closedTimer.height, openedTimer.height, 1f)
        assertEquals("Content reserves exactly the timer, without double IME padding", openedTimer.top, bounds("content").bottom, 1f)
        compose.onNodeWithContentDescription("Pause rest timer").assertIsDisplayed()
        compose.onNodeWithText("1:30 min").assertExists()
        compose.runOnIdle { imeBottom.intValue = 0 }
        assertEquals(closedTimer, bounds("active-rest-timer"))
        assertEquals(closedFooter, bounds("footer"))
        compose.onNodeWithText("1:30 min").assertExists()
        compose.runOnIdle {
            assertTrue("Opening and closing the IME must not issue countdown commands", calls.isEmpty())
            assertEquals("bench-entry", state.timer.workoutExerciseId)
            assertEquals(timer, state.timer)
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
