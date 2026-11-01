package com.mooscles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.designsystem.components.WorkoutBarRestTag
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.screens.shell.AppShellBottom
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
 * The keyboard and the shell (Task 24, on [AppShellBottom]): the workout bar, which carries the
 * rest, rides on the keyboard whole, while the tab bar keeps its place behind it, as the old
 * timer dock and footer did.
 */
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
        val timerUi = TimerUi("Bench press", 90, 120, running = true, workoutExerciseId = "bench-entry")
        val timer = mutableStateOf(timerUi)
        val state = UiState(loading = false, active = WorkoutUi("active", "Workout", System.currentTimeMillis()), timer = timerUi)
        compose.setContent {
            MoosclesDesignTheme(dark = false) {
                val ime = WindowInsets(bottom = imeBottom.intValue)
                Column(Modifier.fillMaxSize().testTag("window")) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("content"))
                    AppShellBottom(state, timer, "routines", {}, {}, {}, actions, imeInsets = ime, nowMillis = { 61_000L + state.active!!.startedAt })
                }
            }
        }
        val closedBar = bounds("shell-workout-bar")
        val closedTabs = bounds(MTabBarTag)
        val window = bounds("window")
        val closedRest = restText()
        assertEquals(closedTabs.top, closedBar.bottom, 1f)
        assertEquals(window.bottom, closedTabs.bottom, 1f)
        compose.runOnIdle { imeBottom.intValue = 350 }
        val openedBar = bounds("shell-workout-bar")
        val openedTabs = bounds(MTabBarTag)
        assertEquals("The tab bar keeps its existing position behind the keyboard", closedTabs, openedTabs)
        assertEquals("The whole workout bar is above the actual IME edge", window.bottom - 350, openedBar.bottom, 1f)
        assertEquals(closedBar.height, openedBar.height, 1f)
        assertEquals("Content gives way to exactly the bar and the keyboard, once", openedBar.top, bounds("content").bottom, 1f)
        assertEquals(closedRest, restText())
        compose.runOnIdle { imeBottom.intValue = 0 }
        assertEquals(closedBar, bounds("shell-workout-bar"))
        assertEquals(closedTabs, bounds(MTabBarTag))
        assertEquals(closedRest, restText())
        compose.runOnIdle {
            assertTrue("Opening and closing the IME must not issue countdown commands", calls.isEmpty())
            assertEquals("bench-entry", timer.value.workoutExerciseId)
            assertEquals(timerUi, timer.value)
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun restText() = compose.onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).fetchSemanticsNode()
        .let { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString { it.text } +
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString() }
}
