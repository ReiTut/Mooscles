package com.mooscles.screens.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.designsystem.components.WorkoutBarRestTag
import com.mooscles.designsystem.components.WorkoutBarSetsTag
import com.mooscles.designsystem.components.WorkoutBarSkipTag
import com.mooscles.designsystem.components.WorkoutBarTag
import com.mooscles.designsystem.components.WorkoutBarTimeTag
import com.mooscles.designsystem.components.tabTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bottom of the app shell: the four tabs (spec D2, D7), the in-progress workout bar above them
 * (D8), the keyboard reserved once without lifting the tabs, and the timer read only in the bar.
 */
@OptIn(ExperimentalTestApi::class)
class AppShellBottomTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val startedAt = 1_000_000L

    /** 42:18 after [startedAt]. */
    private val at4218 = startedAt + (42 * 60 + 18) * 1_000L

    private fun exercise(id: String, done: Int) = WorkoutExerciseUi(
        id = "entry-$id",
        exercise = ExerciseUi(id, "Exercise $id", "Chest", "Barbell"),
        sets = List(5) { SetUi("$id-$it", reps = 8, weightKg = 80.0, completed = it < done) },
    )

    /** 9 of 15 sets done: 5 + 4 + 0. */
    private val workout = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = startedAt,
        exercises = listOf(exercise("a", 5), exercise("b", 4), exercise("c", 0)),
    )

    private val activeState = UiState(loading = false, active = workout)
    private val resting = TimerUi("Supino", remainingSeconds = 84, durationSeconds = 120, running = true)

    private class Shown {
        val selected = mutableListOf<String>()
        val reselected = mutableListOf<String>()
        var opened = 0
    }

    private fun ComposeUiTest.shell(
        state: UiState = activeState,
        timer: State<TimerUi> = mutableStateOf(TimerUi()),
        selected: String? = ShellTabs.TRAIN,
        actions: RecordingActions = RecordingActions(),
        locale: String? = "pt-BR",
        enabled: Boolean = true,
        imeInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
        fontScale: Float = 1f,
        density: Float? = null,
        size: DpSize = DpSize(412.dp, 800.dp),
        probe: ((String) -> Unit)? = null,
        now: () -> Long = { at4218 },
    ): Shown {
        val shown = Shown()
        withScreen(locale = locale, fontScale = fontScale, density = density, size = size, probe = probe) {
            Box(Modifier.fillMaxSize().testTag(WINDOW)) {
                AppShellBottom(
                    state = state,
                    timer = timer,
                    selectedTab = selected,
                    onSelectTab = { shown.selected += it },
                    onReselectTab = { shown.reselected += it },
                    onOpenWorkout = { shown.opened++ },
                    actions = actions,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    imeInsets = imeInsets,
                    enabled = enabled,
                    nowMillis = now,
                )
            }
        }
        waitForIdle()
        return shown
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        runOnUiThread { assertTrue(action != null && action(results), "no text layout result for $tag") }
        return results.first()
    }

    private fun ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    @Test
    fun fourTabsInOrderWithLabels() {
        assertEquals(listOf("routines", "library", "history", "records"), ShellTabs.keys)
        val cases = listOf(
            "pt-BR" to listOf("Treinar", "Biblioteca", "Histórico", "Recordes"),
            "en" to listOf("Train", "Library", "History", "Records"),
        )
        for ((locale, labels) in cases) {
            runComposeUiTest {
                val shown = shell(state = UiState(loading = false), locale = locale)
                val lefts = ShellTabs.keys.mapIndexed { i, key ->
                    onNodeWithTag(tabTag(key)).assertContentDescriptionEquals(labels[i])
                    bounds(tabTag(key)).left
                }
                assertEquals(lefts.sorted(), lefts, "$locale tabs are not in order: $lefts")
                onNodeWithTag(tabTag(ShellTabs.TRAIN)).assertIsSelected()
                onNodeWithTag(tabTag(ShellTabs.LIBRARY)).assertIsNotSelected()
                // The selected tab draws the filled dumbbell, the others their outline icons.
                val drawn = listOf(MIcons.DumbbellFilled, MIcons.LayoutGrid, MIcons.History, MIcons.PlateRecord)
                ShellTabs.keys.forEachIndexed { i, key ->
                    onNode(hasTestTag("tab-icon-${drawn[i].name}") and hasAnyAncestor(hasTestTag(tabTag(key))), useUnmergedTree = true)
                        .assertExists(key)
                }
                // Another tab selects it; the selected one goes back to its root.
                onNodeWithTag(tabTag(ShellTabs.LIBRARY)).performClick()
                onNodeWithTag(tabTag(ShellTabs.TRAIN)).performClick()
                assertEquals(listOf(ShellTabs.LIBRARY), shown.selected)
                assertEquals(listOf(ShellTabs.TRAIN), shown.reselected)
            }
        }
    }

    @Test
    fun noWorkoutNoBar() = runComposeUiTest {
        shell(state = UiState(loading = false, active = null))
        onNodeWithTag(WorkoutBarTag).assertDoesNotExist()
        onNodeWithTag(ShellWorkoutBarTag).assertDoesNotExist()
        // The shell is only the tab bar, at the bottom of the window.
        val footer = bounds(AppFooterTag)
        val tabs = bounds(MTabBarTag)
        assertEquals(tabs.top, footer.top, 1f)
        assertEquals(bounds(WINDOW).bottom, tabs.bottom, 1f)
    }

    @Test
    fun workoutBarShowsElapsedAndSets() = runComposeUiTest {
        var now = at4218
        shell(now = { now })
        onNodeWithTag(WorkoutBarTimeTag, useUnmergedTree = true).assertTextEquals("42:18")
        onNodeWithTag(WorkoutBarSetsTag, useUnmergedTree = true).assertTextEquals("9 de 15 séries")
        // Spoken in whole minutes (ruling F5), with no rest.
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Treino em andamento, Treino A, 42 minutos")
        // The bar reads the clock once a second on its own.
        now += 1_000L
        mainClock.advanceTimeBy(1_100L)
        waitUntil(timeoutMillis = 3_000L) { textOf(WorkoutBarTimeTag) == "42:19" }
        onNodeWithTag(WorkoutBarTimeTag, useUnmergedTree = true).assertTextEquals("42:19")
    }

    @Test
    fun workoutBarNumbersAreWholeAtTwoHundredOnANarrowPhone() {
        // 2.01 with density 1 is the text iOS draws at 200% (see LINEAR_TWO_X_FONT_SCALE).
        for ((scale, density) in listOf(1f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                shell(fontScale = scale, density = density, size = DpSize(320.dp, 800.dp), timer = mutableStateOf(resting))
                assertNumbersWhole(atLeast = 2)
                assertShownWhole(layoutOf(WorkoutBarTimeTag), "elapsed at $scale")
                assertShownWhole(layoutOf(WorkoutBarRestTag), "rest at $scale")
                onNodeWithTag(WorkoutBarSkipTag).assertIsEnabled()
            }
        }
        runComposeUiTest {
            shell(fontScale = LINEAR_TWO_X_FONT_SCALE, density = 1f, size = DpSize(320.dp, 800.dp))
            assertShownWhole(layoutOf(WorkoutBarSetsTag), "sets at 2.01")
        }
    }

    @Test
    fun withRestShowsTheRingAndSkip() = runComposeUiTest {
        val actions = RecordingActions()
        shell(timer = mutableStateOf(resting), actions = actions)
        // The rest (ring and time) takes the place of the sets, and the skip button appears.
        assertEquals("1:24", textOf(WorkoutBarRestTag))
        onNodeWithTag(WorkoutBarSetsTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals(
            "Treino em andamento, Treino A, 42 minutos. Descanso, 1 minuto e 24 segundos",
        )
        onNodeWithTag(WorkoutBarSkipTag).assertContentDescriptionEquals("Pular descanso").performClick()
        assertEquals(listOf("skipTimer:"), actions.calls)
    }

    @Test
    fun pausedRestStaysOnTheBar() = runComposeUiTest {
        val paused = TimerUi("Supino", remainingSeconds = 84, durationSeconds = 120, paused = true)
        shell(timer = mutableStateOf(paused))
        assertEquals("1:24", textOf(WorkoutBarRestTag))
        onNodeWithTag(WorkoutBarSetsTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WorkoutBarSkipTag).assertExists()
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals(
            "Treino em andamento, Treino A, 42 minutos. Descanso, 1 minuto e 24 segundos, pausado",
        )
        // A pause has no end of its own: it is still there well past the done hold.
        mainClock.advanceTimeBy(MoosclesTiming().timerDoneHold + 1_000L)
        waitForIdle()
        assertEquals("1:24", textOf(WorkoutBarRestTag))
        onNodeWithTag(WorkoutBarSkipTag).assertExists()
    }

    @Test
    fun finishedRestShowsDoneThenGoesBack() = runComposeUiTest {
        val timer = mutableStateOf(resting.copy(remainingSeconds = 1))
        shell(timer = timer)
        assertEquals("0:01", textOf(WorkoutBarRestTag))
        val hold = MoosclesTiming().timerDoneHold
        mainClock.autoAdvance = false
        // The bar sees the rest end: the end shows for the hold.
        runOnUiThread { timer.value = TimerUi("Supino", remainingSeconds = 0, durationSeconds = 120, finished = true) }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals("0:00", textOf(WorkoutBarRestTag))
        onNodeWithTag(WorkoutBarSetsTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Treino em andamento, Treino A, 42 minutos. Descanso, concluído")
        mainClock.advanceTimeBy(hold - 200L)
        onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertExists()
        // After the hold the sets come back, and the bar no longer reads a rest.
        mainClock.advanceTimeBy(400L)
        mainClock.advanceTimeByFrame()
        onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WorkoutBarSkipTag).assertDoesNotExist()
        assertEquals("9 de 15 séries", textOf(WorkoutBarSetsTag))
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Treino em andamento, Treino A, 42 minutos")
        mainClock.autoAdvance = true
    }

    @Test
    fun alreadyFinishedRestIsNotShownOnOpen() = runComposeUiTest {
        // As after opening the app or coming back from the workout: the end happened while the bar was not there.
        shell(timer = mutableStateOf(TimerUi("Supino", remainingSeconds = 0, durationSeconds = 120, finished = true)))
        onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(WorkoutBarSkipTag).assertDoesNotExist()
        assertEquals("9 de 15 séries", textOf(WorkoutBarSetsTag))
        onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Treino em andamento, Treino A, 42 minutos")
    }

    @Test
    fun elapsedIsReadUnderAMinuteAndWithHours() {
        // Under a minute the bar does not read "0 seconds"; from 60 minutes it reads hours (ruling F5).
        val cases = listOf(
            35_000L to ("0:35" to "Treino em andamento, Treino A, menos de um minuto"),
            (62 * 60 + 5) * 1_000L to ("1:02:05" to "Treino em andamento, Treino A, 1 hora e 2 minutos"),
        )
        for ((since, expected) in cases) {
            runComposeUiTest {
                shell(now = { startedAt + since })
                onNodeWithTag(WorkoutBarTimeTag, useUnmergedTree = true).assertTextEquals(expected.first)
                onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals(expected.second)
            }
        }
        runComposeUiTest {
            shell(locale = "en", now = { startedAt + 35_000L })
            onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Workout in progress, Treino A, less than a minute")
        }
    }

    @Test
    fun barTapOpensTheWorkout() = runComposeUiTest {
        val actions = RecordingActions()
        val shown = shell(actions = actions)
        onNodeWithTag(WorkoutBarTag).performClick()
        assertEquals(1, shown.opened)
        assertTrue(actions.calls.isEmpty(), "opening the workout called ${actions.calls}")
        assertTrue(shown.selected.isEmpty() && shown.reselected.isEmpty())
    }

    @Test
    fun imeIsReservedOnceWithoutLiftingTheTabs() = runComposeUiTest {
        val imeBottom = mutableIntStateOf(0)
        val actions = RecordingActions()
        val ime = object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getTop(density: Density) = 0
            override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
            override fun getBottom(density: Density) = imeBottom.intValue
        }
        shell(imeInsets = ime, actions = actions, timer = mutableStateOf(resting))
        val window = bounds(WINDOW)
        val closedBar = bounds(ShellWorkoutBarTag)
        val closedTabs = bounds(MTabBarTag)
        assertEquals(closedTabs.top, closedBar.bottom, 1f)
        assertEquals(window.bottom, closedTabs.bottom, 1f)
        assertEquals(closedBar.top, bounds(AppFooterTag).top, 1f)

        // A keyboard taller than the tabs: the bar sits on it, the tabs stay behind it.
        val keyboard = (closedTabs.height + 300f).toInt()
        runOnUiThread { imeBottom.intValue = keyboard }
        waitForIdle()
        val openedBar = bounds(ShellWorkoutBarTag)
        assertEquals(closedTabs, bounds(MTabBarTag), "the tabs moved with the keyboard")
        assertEquals(window.bottom - keyboard, openedBar.bottom, 1f)
        assertEquals(closedBar.height, openedBar.height, 1f)
        // The shell reserves the keyboard once: its top is the bar's, not the bar plus the keyboard again.
        assertEquals(openedBar.top, bounds(AppFooterTag).top, 1f)
        assertEquals("1:24", textOf(WorkoutBarRestTag))

        // A keyboard shorter than the tabs moves nothing.
        runOnUiThread { imeBottom.intValue = (closedTabs.height / 2).toInt() }
        waitForIdle()
        assertEquals(closedBar, bounds(ShellWorkoutBarTag))
        assertEquals(closedTabs, bounds(MTabBarTag))

        runOnUiThread { imeBottom.intValue = 0 }
        waitForIdle()
        assertEquals(closedBar, bounds(ShellWorkoutBarTag))
        assertEquals(closedTabs, bounds(MTabBarTag))
        assertTrue(actions.calls.isEmpty(), "the keyboard issued ${actions.calls}")
    }

    @Test
    fun timerTicksDoNotRecomposeTheTabs() = runComposeUiTest {
        val keys = mutableListOf<String>()
        val timer = mutableStateOf(resting.copy(remainingSeconds = 90))
        shell(timer = timer, probe = { keys += it })
        val tabsBefore = runOnIdle { keys.count { it == "tabs" } }
        val barBefore = runOnIdle { keys.count { it == "bar" } }
        assertTrue(tabsBefore >= 1 && barBefore >= 1, "the probes did not report: $keys")
        repeat(10) { i ->
            runOnUiThread { timer.value = timer.value.copy(remainingSeconds = 89 - i) }
            waitForIdle()
        }
        assertEquals("1:20", textOf(WorkoutBarRestTag))
        val (tabsAfter, barAfter) = runOnIdle { keys.count { it == "tabs" } to keys.count { it == "bar" } }
        assertEquals(tabsBefore, tabsAfter, "the tabs recomposed on timer ticks")
        // The bar did take the ten emissions, so the count above is not a probe that never reports.
        assertTrue(barAfter >= barBefore + 10, "the bar recomposed ${barAfter - barBefore} times for 10 emissions")
    }

    @Test
    fun disabledWhileLoading() {
        for ((state, enabled) in listOf(UiState(loading = true) to true, UiState(loading = false) to false)) {
            runComposeUiTest {
                val shown = shell(state = state, enabled = enabled)
                for (key in ShellTabs.keys) onNodeWithTag(tabTag(key)).assertIsNotEnabled()
                onNodeWithTag(tabTag(ShellTabs.LIBRARY)).performClick()
                assertTrue(shown.selected.isEmpty(), "a disabled tab selected ${shown.selected}")
            }
        }
        runComposeUiTest {
            shell(state = UiState(loading = false))
            for (key in ShellTabs.keys) onNodeWithTag(tabTag(key)).assertIsEnabled()
        }
    }

    private companion object {
        const val WINDOW = "window"
    }
}
