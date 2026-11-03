package com.mooscles.screens.workout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.TimerAnnounceTag
import com.mooscles.designsystem.components.TimerCloseTag
import com.mooscles.designsystem.components.TimerCompactTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerDoneCheckTag
import com.mooscles.designsystem.components.TimerExpandedTag
import com.mooscles.designsystem.components.TimerMinusTag
import com.mooscles.designsystem.components.TimerOpenTag
import com.mooscles.designsystem.components.TimerPauseResumeTag
import com.mooscles.designsystem.components.TimerPausedIconTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerRestartTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.components.TimerTimeTag
import com.mooscles.designsystem.components.TimerTitleTag
import com.mooscles.designsystem.components.TimerTotalTag
import com.mooscles.designsystem.components.TimerUpNextTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.restoreTouchMode
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rest timer on the active workout screen (spec "Dock, compacto e expandido", D15, plan
 * decisions 21 and 23): the dock only while there is a rest, the compact one above the keyboard
 * and at large text, the expanded timer over the workout, and ticks that recompose the timer and
 * never a card or a row.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutTimerTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell", convention = "Total load")

    /** Bench press with 4 of 5 sets done (the fifth 85 × 6), then a row with 3 pending sets of 60 × 10. */
    private val workout = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = START,
        exercises = listOf(
            WorkoutExerciseUi(
                "e1", bench,
                sets = (1..5).map { n -> SetUi("e1s$n", reps = 6, weightKg = 85.0, completed = n <= 4) },
            ),
            WorkoutExerciseUi("e2", row, sets = (1..3).map { n -> SetUi("e2s$n", reps = 10, weightKg = 60.0) }),
        ),
    )

    /** A rest after set 4 of the bench press: 1:30 of 2:00 left. */
    private val running = TimerUi("Supino reto", remainingSeconds = 90, durationSeconds = 120, running = true, workoutExerciseId = "e1")

    private val finished = TimerUi("Supino reto", remainingSeconds = 0, durationSeconds = 120, finished = true, workoutExerciseId = "e1")

    private val hold = MoosclesTiming().timerDoneHold
    private val fast = MoosclesMotion().fast.toLong()

    private fun ComposeUiTest.show(
        timer: MutableState<TimerUi>,
        shown: WorkoutUi = workout,
        actions: RecordingActions = RecordingActions(),
        imeInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
        size: DpSize = DpSize(412.dp, 800.dp),
        fontScale: Float = 1f,
        density: Float? = null,
        probe: ((String) -> Unit)? = null,
        now: () -> Long = { START + ELAPSED_MS },
        inputModes: InputModes? = null,
    ) {
        withScreen(locale = "pt-BR", size = size, fontScale = fontScale, density = density, probe = probe) {
            inputModes?.manager = LocalInputModeManager.current
            ActiveWorkoutRoute(
                state = UiState(loading = false, active = shown, exercises = listOf(bench, row)),
                timer = timer,
                actions = actions,
                onMinimize = {},
                onOpenSummary = {},
                onExerciseHistory = {},
                onChooseRoutine = {},
                imeInsets = imeInsets,
                nowMillis = now,
            )
        }
        waitForIdle()
    }

    private fun ComposeUiTest.tag(tag: String): SemanticsNodeInteraction = onNodeWithTag(tag, useUnmergedTree = true)

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.textOf(tag: String): String =
        tag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun ComposeUiTest.stateOf(tag: String): String? =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun ComposeUiTest.announcement(): String =
        tag(TimerAnnounceTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    /** No dock and no compact timer. */
    private fun ComposeUiTest.assertNoTimer() {
        onNodeWithTag(TimerDockTag).assertDoesNotExist()
        onNodeWithTag(TimerCompactTag).assertDoesNotExist()
    }

    private fun ComposeUiTest.openExpanded() {
        onNodeWithTag(TimerOpenTag).performClick()
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).assertExists()
    }

    /** Advances the paused clock to [at] (in `mainClock` time), a frame at a time at the end. */
    private fun ComposeUiTest.advanceTo(at: Long) {
        val left = at - mainClock.currentTime
        if (left > 0) mainClock.advanceTimeBy(left, ignoreFrameDuration = true)
    }

    @Test
    fun noTimerTakesNoSpace() = runComposeUiTest {
        val timer = mutableStateOf(TimerUi())
        show(timer)
        assertNoTimer()
        val route = bounds(ActiveWorkoutTags.ROUTE)
        assertTrue(abs(bounds(ActiveWorkoutTags.LIST).bottom - route.bottom) <= 1f, "the list ends at the bottom: ${bounds(ActiveWorkoutTags.LIST)}, $route")
        // A rest takes room at the bottom; skipping it gives the room back.
        timer.value = running
        waitForIdle()
        val dock = bounds(TimerDockTag)
        assertTrue(bounds(ActiveWorkoutTags.LIST).bottom <= dock.top + 1f, "the list stops above the dock")
        timer.value = TimerUi("Supino reto", remainingSeconds = 0, durationSeconds = 120)
        waitForIdle()
        assertNoTimer()
        assertTrue(abs(bounds(ActiveWorkoutTags.LIST).bottom - route.bottom) <= 1f, "the list did not get the room back")
    }

    @Test
    fun dockShowsWhileRunningAndThreeSecondsAfterDone() = runComposeUiTest {
        val timer = mutableStateOf(running.copy(remainingSeconds = 1))
        show(timer)
        onNodeWithTag(TimerDockTag).assertIsDisplayed()
        assertEquals("0:01", textOf(TimerTimeTag))
        mainClock.autoAdvance = false
        val end = mainClock.currentTime
        runOnUiThread { timer.value = finished }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag(TimerDockTag).assertExists()
        onNode(hasText("Descanso concluído") and hasAnyAncestor(hasTestTag(TimerDockTag)), useUnmergedTree = true).assertExists()
        advanceTo(end + hold - 1)
        onNodeWithTag(TimerDockTag).assertExists()
        // After the hold the dock leaves with motion.fast, and takes no room.
        advanceTo(end + hold + fast + 100L)
        mainClock.advanceTimeByFrame()
        assertNoTimer()
        mainClock.autoAdvance = true
    }

    @Test
    fun doneAlreadyFinishedAtOpenShowsNothing() = runComposeUiTest {
        // Plan decision 21: the end happened while the screen was not there (the app opening, a return).
        show(mutableStateOf(finished))
        assertNoTimer()
        mainClock.advanceTimeBy(hold + 1_000L)
        waitForIdle()
        assertNoTimer()
    }

    @Test
    fun adjustAndSkipCallTheActions() = runComposeUiTest {
        val actions = RecordingActions()
        show(mutableStateOf(running), actions = actions)
        onNodeWithTag(TimerPlusTag).performClick()
        onNodeWithTag(TimerMinusTag).performClick()
        onNodeWithTag(TimerSkipTag).performClick()
        waitForIdle()
        assertEquals(listOf("adjustTimer:15", "adjustTimer:-15", "skipTimer:"), actions.calls)
    }

    @Test
    fun plusFifteenIsDisabledAtFiveMinutes() = runComposeUiTest {
        val actions = RecordingActions()
        val timer = mutableStateOf(TimerUi("Supino reto", remainingSeconds = 300, durationSeconds = 300, paused = true, workoutExerciseId = "e1"))
        show(timer, actions = actions)
        onNodeWithTag(TimerPlusTag).assertIsNotEnabled().performClick()
        onNodeWithTag(TimerMinusTag).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf("adjustTimer:-15"), actions.calls)
        // The expanded timer has the same ceiling.
        openExpanded()
        onNodeWithTag(TimerPlusTag).assertIsNotEnabled()
        // Under the ceiling, +15 s is back.
        timer.value = timer.value.copy(remainingSeconds = 285)
        waitForIdle()
        onNodeWithTag(TimerPlusTag).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf("adjustTimer:-15", "adjustTimer:15"), actions.calls)
    }

    @Test
    fun bodyTapOpensTheExpanded() = runComposeUiTest {
        show(mutableStateOf(running))
        onNodeWithTag(TimerExpandedTag).assertDoesNotExist()
        onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals("Abrir timer de descanso, tempo restante: 1 minuto e 30 segundos")
        openExpanded()
        // The expanded timer is the one timer on screen: the dock waits under it.
        onNodeWithTag(TimerDockTag).assertDoesNotExist()
        assertEquals("1:30", textOf(TimerTimeTag))
    }

    /**
     * Focus is the keyboard's and the screen reader's: in touch mode a button takes no focus
     * (`Focusability.SystemDefined`), so the test works in keyboard mode and activates the
     * buttons by their semantic click, as a screen reader or a keyboard does, not by a touch.
     */
    @Test
    fun closingTheExpandedReturnsFocusToTheDock() = try {
        closeAndCheckTheFocus()
    } finally {
        // Keyboard mode outlives the test on Android (it is the display's): give touch mode back.
        restoreTouchMode()
    }

    private fun closeAndCheckTheFocus() = runComposeUiTest {
        val modes = InputModes()
        show(mutableStateOf(running), inputModes = modes)
        runOnUiThread { modes.manager?.requestInputMode(InputMode.Keyboard) }
        waitForIdle()
        assertEquals(InputMode.Keyboard, modes.manager?.inputMode, "keyboard mode")
        onNodeWithTag(TimerOpenTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).assertExists()
        onNodeWithTag(TimerCloseTag).assertContentDescriptionEquals("Fechar timer").performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).assertDoesNotExist()
        onNodeWithTag(TimerOpenTag).assertIsFocused()
        // Back closes it the same way.
        onNodeWithTag(TimerOpenTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).performKeyInput { pressKey(Key.Back) }
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).assertDoesNotExist()
        onNodeWithTag(TimerOpenTag).assertIsFocused()
    }

    /** The screen's input mode manager, read from the composition. */
    private class InputModes {
        var manager: InputModeManager? = null
    }

    @Test
    fun expandedShowsTitleUpNextAndControls() = runComposeUiTest {
        val actions = RecordingActions()
        val timer = mutableStateOf(running)
        show(timer, actions = actions)
        openExpanded()
        tag(TimerTitleTag).assertTextEquals("Descanso · Supino reto · série 4 de 5")
        tag(TimerTotalTag).assertTextEquals("de 2:00")
        // Ruling F18: the component's own "A seguir" over the value, and the label only once.
        onNode(hasText("A seguir") and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertExists()
        onNode(hasText("série 5 · 85 × 6") and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertExists()
        assertEquals(1, onAllNodesWithText("A seguir", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag(TimerPauseResumeTag).assert(hasText("Pausar")).performClick()
        waitForIdle()
        timer.value = running.copy(running = false, paused = true)
        waitForIdle()
        onNodeWithTag(TimerPauseResumeTag).assert(hasText("Retomar")).performClick()
        onNodeWithTag(TimerRestartTag).assert(hasText("Recomeçar")).performClick()
        waitForIdle()
        assertEquals(listOf("pauseTimer:", "resumeTimer:", "resetTimer:"), actions.calls)
        // Ruling F19: a rest started from the rest sheet has no set: the title is the exercise
        // alone, and up next is the first pending set of the workout.
        timer.value = running.copy(workoutExerciseId = null, exerciseName = "Remada curvada")
        waitForIdle()
        tag(TimerTitleTag).assertTextEquals("Descanso · Remada curvada")
        onNode(hasText("série 5 · 85 × 6") and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertExists()
    }

    @Test
    fun expandedClosesAfterTheDoneHold() = runComposeUiTest {
        val timer = mutableStateOf(running.copy(remainingSeconds = 1))
        show(timer)
        openExpanded()
        mainClock.autoAdvance = false
        val end = mainClock.currentTime
        runOnUiThread { timer.value = finished }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        // "Rest complete" with the check, for the hold.
        onNode(hasText("Descanso concluído") and hasAnyAncestor(hasTestTag(TimerExpandedTag)), useUnmergedTree = true).assertExists()
        tag(TimerDoneCheckTag).assertExists()
        advanceTo(end + hold - 1)
        onNodeWithTag(TimerExpandedTag).assertExists()
        advanceTo(end + hold + fast + 100L)
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
        // It closed on its own, back to the workout, and no rest is left on screen.
        onNodeWithTag(TimerExpandedTag).assertDoesNotExist()
        assertNoTimer()
        onNodeWithTag(ActiveWorkoutTags.LIST).assertIsDisplayed()
    }

    /**
     * Spec "Expandido": after the done hold it closes on its own "e volta para a série seguinte":
     * the list brings the set "Up next" named into view, and that row takes the focus (its first
     * control, not a field). Focus is the keyboard's, so the test runs in keyboard mode, as
     * [closingTheExpandedReturnsFocusToTheDock] does.
     */
    @Test
    fun doneHoldReturnsToTheNextSet() = try {
        returnAndCheckTheNextSet()
    } finally {
        restoreTouchMode()
    }

    private fun returnAndCheckTheNextSet() = runComposeUiTest {
        val long = workout.copy(
            exercises = listOf(
                WorkoutExerciseUi("e1", bench, sets = (1..12).map { n -> SetUi("e1s$n", reps = 6, weightKg = 85.0, completed = n <= 4) }),
                workout.exercises[1],
            ),
        )
        val modes = InputModes()
        val timer = mutableStateOf(running.copy(remainingSeconds = 1))
        show(timer, shown = long, size = DpSize(412.dp, 640.dp), inputModes = modes)
        runOnUiThread { modes.manager?.requestInputMode(InputMode.Keyboard) }
        waitForIdle()
        assertEquals(InputMode.Keyboard, modes.manager?.inputMode, "keyboard mode")
        val next = workoutSetRowTag("e1s5")
        // The control: the list is at its end, the next set far above the viewport.
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.FINISH_WORKOUT))
        waitForIdle()
        assertFalse(wholeInTheList(next), "the next set is on screen before the rest ends")
        onNodeWithTag(TimerOpenTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        tag(TimerUpNextTag).assertExists()
        onNode(hasText("série 5 · 85 × 6") and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertExists()
        mainClock.autoAdvance = false
        val end = mainClock.currentTime
        runOnUiThread { timer.value = finished }
        mainClock.advanceTimeByFrame()
        advanceTo(end + hold + fast + 100L)
        mainClock.advanceTimeByFrame()
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithTag(TimerExpandedTag).assertDoesNotExist()
        assertTrue(wholeInTheList(next), "the next set is not in view: ${bounds(next)}, list ${bounds(ActiveWorkoutTags.LIST)}")
        val inRow = hasTestTag(next) or hasAnyAncestor(hasTestTag(next))
        onNode(isFocused() and inRow, useUnmergedTree = true).assertExists("the next set does not have the focus")
        // Its first control, never a field: the keyboard does not open on its own.
        onAllNodes(isFocused() and hasSetTextAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    /** Whether the node of [tag] is whole inside the list's viewport. */
    private fun ComposeUiTest.wholeInTheList(tag: String): Boolean {
        val node = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull() ?: return false
        val list = bounds(ActiveWorkoutTags.LIST)
        val rect = node.boundsInRoot
        return rect.height >= node.size.height - 0.5f && rect.top >= list.top - 0.5f && rect.bottom <= list.bottom + 0.5f
    }

    /** Review M3: "Up next" never names a 0 the row leaves blank. */
    @Test
    fun upNextLeavesOutBlankParts() {
        val cases = listOf(
            // Added weight without a load: only the reps.
            SetUi("e2s1", reps = 12, weightKg = 0.0) to "série 1 · 12 repetições",
            SetUi("e2s1", reps = 1, weightKg = 0.0) to "série 1 · 1 repetição",
            // A set still blank, with or without a load: only the set.
            SetUi("e2s1", reps = 0, weightKg = 0.0) to "série 1",
            SetUi("e2s1", reps = 0, weightKg = 60.0) to "série 1",
            SetUi("e2s1", reps = 10, weightKg = 62.5) to "série 1 · 62,5 × 10",
        )
        for ((set, expected) in cases) {
            runComposeUiTest {
                val shown = workout.copy(exercises = listOf(workout.exercises[0], workout.exercises[1].copy(sets = listOf(set))))
                show(mutableStateOf(running.copy(workoutExerciseId = "e2")), shown = shown)
                openExpanded()
                onNode(hasText(expected) and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertExists("up next for $set")
                onAllNodes(hasText(" 0 ", substring = true) and hasAnyAncestor(hasTestTag(TimerUpNextTag)), useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }

    /** Review M4: the undo toast sits above the rest timer, never over −15 s, +15 s or skip. */
    @Test
    fun undoToastSitsAboveTheDock() = runComposeUiTest {
        val actions = RecordingActions()
        show(mutableStateOf(running), actions = actions)
        val row = workoutSetRowTag("e1s5")
        onNodeWithTag(row).performTouchInput { click(Offset(width * 0.05f, centerY)) }
        waitForIdle()
        onNodeWithText("Remover série").performClick()
        waitForIdle()
        val toast = onNodeWithText("Série 5 removida").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        onNodeWithText("Desfazer", useUnmergedTree = true).assertIsDisplayed()
        val dock = bounds(TimerDockTag)
        assertTrue(toast.bottom <= dock.top + 0.5f, "toast $toast over the dock $dock")
        for (control in listOf(TimerMinusTag, TimerPlusTag, TimerSkipTag)) {
            assertFalse(toast.overlaps(bounds(control)), "toast $toast over $control ${bounds(control)}")
        }
        // The dock still takes the taps while the toast is up.
        onNodeWithTag(TimerSkipTag).performClick()
        waitForIdle()
        assertEquals(listOf("removeSet:e1s5", "skipTimer:"), actions.calls)
    }

    @Test
    fun compactSitsAboveTheKeyboard() = runComposeUiTest {
        // S3: the keyboard is 300 dp of injected insets on both platforms.
        show(mutableStateOf(running), imeInsets = WindowInsets(bottom = 300.dp))
        onNodeWithTag(TimerDockTag).assertDoesNotExist()
        val compact = bounds(TimerCompactTag)
        val route = bounds(ActiveWorkoutTags.ROUTE)
        val keyboard = with(density) { 300.dp.toPx() }
        assertTrue(abs(compact.bottom - (route.bottom - keyboard)) <= 1f, "compact $compact, route $route")
        // No ±15 s there: they stay in the expanded timer.
        onNodeWithTag(TimerMinusTag).assertDoesNotExist()
        onNodeWithTag(TimerPlusTag).assertDoesNotExist()
        onNodeWithTag(TimerSkipTag).assertExists()
        assertTrue(bounds(ActiveWorkoutTags.LIST).bottom <= compact.top + 1f, "the list stops above the compact timer")
    }

    @Test
    fun compactAtOneAndAHalf() {
        for ((scale, compact) in listOf(1.3f to false, 1.5f to true, 2f to true)) {
            runComposeUiTest {
                show(mutableStateOf(running), fontScale = scale)
                onNodeWithTag(TimerCompactTag).let { if (compact) it.assertExists() else it.assertDoesNotExist() }
                onNodeWithTag(TimerDockTag).let { if (compact) it.assertDoesNotExist() else it.assertExists() }
            }
        }
    }

    @Test
    fun listKeepsSixtyPercentAtTwoHundredWithTheTimer() {
        // Spec D15: at 200 %, on a Pixel 8 (412 × 915 dp), with the rest running.
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                show(mutableStateOf(running), size = DpSize(412.dp, 915.dp), fontScale = scale, density = density)
                onNodeWithTag(TimerCompactTag).assertIsDisplayed()
                val list = bounds(ActiveWorkoutTags.LIST)
                val compact = bounds(TimerCompactTag)
                // The route fills the 915 dp screen; its height is that in pixels at either density.
                val screen = bounds(ActiveWorkoutTags.ROUTE).height
                assertTrue(list.bottom <= compact.top + 1f, "scale $scale: the list runs under the timer")
                assertTrue(list.height >= 0.6f * screen, "scale $scale: the list is ${list.height} of $screen")
            }
        }
    }

    @Test
    fun editingRowStaysAboveKeyboardAndCompactDock() = runComposeUiTest {
        val long = workout.copy(
            exercises = listOf(
                WorkoutExerciseUi("e1", bench, sets = (1..10).map { n -> SetUi("e1s$n", reps = 6, weightKg = 85.0) }),
            ),
        )
        show(mutableStateOf(running.copy(workoutExerciseId = "e1")), shown = long, imeInsets = WindowInsets(bottom = 300.dp))
        val compact = bounds(TimerCompactTag)
        // The control: the list scrolled so the first row not wholly on screen is cut in half by its
        // bottom, right over the timer.
        val ids = (1..10).map { "e1s$it" }
        val bottom = bounds(ActiveWorkoutTags.LIST).bottom
        val height = onNodeWithTag(workoutSetRowTag(ids.first())).fetchSemanticsNode().size.height
        val cut = ids.first { !wholeAbove(it, compact) }
        repeat(3) {
            val node = onAllNodes(hasTestTag(workoutSetRowTag(cut))).fetchSemanticsNodes().firstOrNull()
            // Not composed yet: it is under the bottom; half a row brings it closer.
            val by = node?.let { it.positionInRoot.y - (bottom - height / 2f) } ?: (height / 2f)
            onNodeWithTag(ActiveWorkoutTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, by) }
            waitForIdle()
        }
        assertFalse(wholeAbove(cut, compact), "row $cut whole before the edit: ${bounds(workoutSetRowTag(cut))}, list bottom $bottom")
        assertTrue(bounds(workoutSetRowTag(cut)).height > 0f, "row $cut not on screen before the edit")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and hasAnyAncestor(hasTestTag(workoutSetRowTag(cut))), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus)
        waitForIdle()
        assertTrue(wholeAbove(cut, bounds(TimerCompactTag)), "row $cut not wholly above the compact timer ${bounds(TimerCompactTag)}")
        val keyboardTop = bounds(ActiveWorkoutTags.ROUTE).bottom - with(density) { 300.dp.toPx() }
        assertTrue(bounds(TimerCompactTag).bottom <= keyboardTop + 1f, "the timer went under the keyboard")
    }

    /** Whether the row of [id] is whole on screen and above [timer] (bounds are clipped by the list's viewport). */
    private fun ComposeUiTest.wholeAbove(id: String, timer: Rect): Boolean {
        val node = onAllNodes(hasTestTag(workoutSetRowTag(id))).fetchSemanticsNodes().firstOrNull() ?: return false
        val rect = node.boundsInRoot
        return rect.height >= node.size.height - 0.5f && rect.top >= -0.5f && rect.bottom <= timer.top + 0.5f
    }

    @Test
    fun timerTicksRecomposeNoCardOrRow() = runComposeUiTest {
        val keys = mutableListOf<String>()
        val timer = mutableStateOf(running)
        show(timer, probe = { keys += it })
        val before = runOnIdle { keys.groupingBy { it }.eachCount() }
        val shown = before.keys.filter { it.startsWith("card-") || it.startsWith("row-") }
        assertTrue(shown.any { it.startsWith("card-") } && shown.any { it.startsWith("row-") } && (before["dock"] ?: 0) >= 1, "the probes did not report: $before")
        repeat(10) { i ->
            runOnUiThread { timer.value = timer.value.copy(remainingSeconds = 89 - i) }
            waitForIdle()
        }
        assertEquals("1:20", textOf(TimerTimeTag))
        val after = runOnIdle { keys.groupingBy { it }.eachCount() }
        for (key in shown) assertEquals(before[key], after[key], "$key recomposed on timer ticks")
        // The positive control: the dock took the ten emissions, so the probe counts.
        assertTrue((after["dock"] ?: 0) >= (before["dock"] ?: 0) + 10, "the dock recomposed ${(after["dock"] ?: 0) - (before["dock"] ?: 0)} times for 10 emissions")
    }

    @Test
    fun clockRecomposesOnlyItself() = runComposeUiTest {
        // With a rest ticking beside it: the clock and the dock each take their seconds, each row none.
        val keys = mutableListOf<String>()
        val timer = mutableStateOf(running)
        val base = mainClock.currentTime
        show(timer, probe = { keys += it }, now = { START + ELAPSED_MS + (mainClock.currentTime - base) })
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        val before = keys.groupingBy { it }.eachCount()
        val rows = before.keys.filter { it.startsWith("row-") || it.startsWith("card-") }
        assertTrue(rows.isNotEmpty() && (before["clock"] ?: 0) >= 1, "the probes did not report: $before")
        val frame = 16L
        repeat(5) { i ->
            // A second per step, the frame that composes the tick included.
            mainClock.advanceTimeBy(1_000L - frame, ignoreFrameDuration = true)
            runOnUiThread { timer.value = timer.value.copy(remainingSeconds = 89 - i) }
            mainClock.advanceTimeBy(frame, ignoreFrameDuration = true)
        }
        // Counted before the clock runs free again (the ring's last sweep would take another second).
        val after = keys.groupingBy { it }.eachCount()
        mainClock.autoAdvance = true
        assertEquals((before["clock"] ?: 0) + 5, after["clock"], "clock recompositions: $after")
        for (key in rows) assertEquals(before[key], after[key], "$key recomposed with the clock or the rest")
        assertTrue((after["dock"] ?: 0) >= (before["dock"] ?: 0) + 5, "the dock did not take the ticks")
    }

    @Test
    fun dockStaysVisibleWhileALongWorkoutScrolls() = runComposeUiTest {
        val actions = RecordingActions()
        val long = WorkoutUi(
            "w1", "Treino longo", START,
            exercises = List(12) { i ->
                WorkoutExerciseUi(
                    "entry-$i",
                    ExerciseUi("exercise-$i", "Movimento ${i + 1}", "Back", "Bodyweight", "Added weight"),
                    sets = listOf(SetUi("set-$i", reps = 10, completed = true)),
                )
            },
        )
        show(mutableStateOf(running.copy(exerciseName = "Barra fixa", workoutExerciseId = "entry-0")), shown = long, actions = actions, size = DpSize(320.dp, 470.dp))
        val before = onNodeWithTag(TimerDockTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(ActiveWorkoutTags.FINISH_WORKOUT))
        waitForIdle()
        onNodeWithTag(ActiveWorkoutTags.FINISH_WORKOUT).assertIsDisplayed()
        val after = onNodeWithTag(TimerDockTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        assertTrue(bounds(ActiveWorkoutTags.LIST).bottom <= after.top + 1f, "the timer must be outside the list's viewport")
        onNodeWithTag(TimerSkipTag).performClick()
        waitForIdle()
        assertEquals(listOf("skipTimer:"), actions.calls)
        onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToIndex(0)
        waitForIdle()
        onNodeWithTag(TimerDockTag).assertIsDisplayed()
    }

    @Test
    fun progressSemanticsFollowRunningPausedAdjustedResetAndDone() = runComposeUiTest {
        // The announcer clears itself after `timerAnnounceHold`, so the clock only moves a frame at a time.
        mainClock.autoAdvance = false
        val timer = mutableStateOf(running)
        show(timer)
        fun expect(time: String, label: String, state: String?, announced: String, paused: Boolean) {
            mainClock.advanceTimeByFrame()
            waitForIdle()
            assertEquals(time, textOf(TimerTimeTag))
            onNodeWithTag(TimerOpenTag).assertContentDescriptionEquals(label)
            assertEquals(state, stateOf(TimerOpenTag), "state of $label")
            assertEquals(announced, announcement())
            tag(TimerPausedIconTag).let { if (paused) it.assertExists() else it.assertDoesNotExist() }
        }
        expect("1:30", "Abrir timer de descanso, tempo restante: 1 minuto e 30 segundos", null, "Descanso, tempo restante: 1 minuto e 30 segundos", paused = false)
        timer.value = running.copy(running = false, paused = true)
        expect("1:30", "Abrir timer de descanso, tempo restante: 1 minuto e 30 segundos", "Pausado", "Pausado", paused = true)
        timer.value = running.copy(remainingSeconds = 60)
        expect("1:00", "Abrir timer de descanso, tempo restante: 1 minuto", null, "Descanso, tempo restante: 1 minuto", paused = false)
        timer.value = running.copy(remainingSeconds = 120)
        expect("2:00", "Abrir timer de descanso, tempo restante: 2 minutos", null, "Descanso, tempo restante: 2 minutos", paused = false)
        timer.value = finished
        expect("0:00", "Abrir timer de descanso", "Descanso concluído", "Descanso concluído", paused = false)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val ELAPSED_MS = 2_538_000L
    }
}
